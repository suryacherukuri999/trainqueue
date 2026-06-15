package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.config.SchedulerProperties;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Runs worker-sim as a Kubernetes Job (backoffLimit 0, restartPolicy Never — all
 * retries stay in our scheduler). Selected with LAUNCHER=k8s.
 *
 * Artifacts: a completed Job's pod is terminated, so its filesystem can't be
 * exec-copied after the fact. Instead the worker uploads its own artifact to S3
 * (ARTIFACT_S3_* env below); readArtifact therefore returns empty here.
 */
@Component
@ConditionalOnProperty(name = "LAUNCHER", havingValue = "k8s")
public class KubernetesLauncher implements JobLauncher {

    private static final Logger log = LoggerFactory.getLogger(KubernetesLauncher.class);
    private static final String NS = "default";
    private static final String PREFIX = "trainqueue-";
    private static final String APP_LABEL = "trainqueue-worker";

    private final KubernetesClient client;
    private final SchedulerProperties.S3 s3;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "k8s-launcher");
        t.setDaemon(true);
        return t;
    });

    public KubernetesLauncher(KubernetesClient client, SchedulerProperties props) {
        this.client = client;
        this.s3 = props.s3();
    }

    @Override
    public String launch(JobSubmittedEvent e, String outputDir) {
        String name = PREFIX + e.jobId();
        remove(name);

        List<EnvVar> env = new ArrayList<>();
        env.add(new EnvVarBuilder().withName("JOB_ID").withValue(e.jobId().toString()).build());
        env.add(new EnvVarBuilder().withName("EPOCHS").withValue(String.valueOf(e.epochs())).build());
        if (e.failAtEpoch() != null) {
            env.add(new EnvVarBuilder().withName("FAIL_AT_EPOCH").withValue(String.valueOf(e.failAtEpoch())).build());
        }
        // the pod is gone before we could copy its artifact, so the worker uploads its own
        env.add(new EnvVarBuilder().withName("ARTIFACT_S3_BUCKET").withValue(s3.bucket()).build());
        env.add(new EnvVarBuilder().withName("ARTIFACT_S3_ENDPOINT").withValue(s3.endpoint()).build());
        env.add(new EnvVarBuilder().withName("ARTIFACT_S3_REGION").withValue(s3.region()).build());
        env.add(new EnvVarBuilder().withName("AWS_ACCESS_KEY_ID").withValue(s3.accessKey()).build());
        env.add(new EnvVarBuilder().withName("AWS_SECRET_ACCESS_KEY").withValue(s3.secretKey()).build());

        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(name)
                    .addToLabels("app", APP_LABEL)
                    .addToLabels("jobId", e.jobId().toString())
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(0)
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("app", APP_LABEL).endMetadata()
                        .withNewSpec()
                            .withRestartPolicy("Never")
                            .addNewContainer()
                                .withName("worker")
                                .withImage(e.dockerImage())
                                .withImagePullPolicy("IfNotPresent")
                                .withEnv(env)
                                .addNewVolumeMount().withName("output").withMountPath("/output").endVolumeMount()
                                .addNewVolumeMount().withName("tmp").withMountPath("/tmp").endVolumeMount()
                                .withNewResources()
                                    .addToRequests("cpu", new Quantity(e.cpuMillis() + "m"))
                                    .addToRequests("memory", new Quantity(e.memMb() + "Mi"))
                                .endResources()
                                .withNewSecurityContext()
                                    .withRunAsNonRoot(true)
                                    .withRunAsUser(10001L)
                                    .withAllowPrivilegeEscalation(false)
                                    .withReadOnlyRootFilesystem(true)
                                    .withNewSeccompProfile().withType("RuntimeDefault").endSeccompProfile()
                                    .withNewCapabilities().withDrop("ALL").endCapabilities()
                                .endSecurityContext()
                            .endContainer()
                            // read-only rootfs needs writable scratch for /output and /tmp (boto3)
                            .addNewVolume().withName("output").withNewEmptyDir().endEmptyDir().endVolume()
                            .addNewVolume().withName("tmp").withNewEmptyDir().endEmptyDir().endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        client.batch().v1().jobs().inNamespace(NS).resource(job).create();
        return name;
    }

    @Override
    public void streamLogs(String jobName, long sinceEpochSecs, LogSink sink) {
        pool.submit(() -> {
            try {
                String pod = awaitPod(jobName);
                if (pod == null) {
                    log.warn("no pod appeared for job {}", jobName);
                    return;
                }
                // K8s combines stdout+stderr into one stream; tag everything as non-stderr.
                try (LogWatch watch = client.pods().inNamespace(NS).withName(pod).watchLog();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(watch.getOutput(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            sink.accept(trimmed, false);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("log stream error for job {}: {}", jobName, e.getMessage());
            }
        });
    }

    @Override
    public void watchExit(String jobName, IntConsumer onExit) {
        pool.submit(() -> {
            try {
                while (true) {
                    Job job = client.batch().v1().jobs().inNamespace(NS).withName(jobName).get();
                    if (job == null) {
                        onExit.accept(1);
                        return;
                    }
                    JobStatus status = job.getStatus();
                    if (status != null) {
                        if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                            onExit.accept(0);
                            return;
                        }
                        if (status.getFailed() != null && status.getFailed() > 0) {
                            onExit.accept(1);
                            return;
                        }
                    }
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public boolean isRunning(String jobName) {
        Job job = client.batch().v1().jobs().inNamespace(NS).withName(jobName).get();
        if (job == null) {
            return false;
        }
        return !isComplete(job);
    }

    @Override
    public void stopAndRemove(String jobName) {
        remove(jobName);
    }

    @Override
    public void remove(String jobName) {
        try {
            client.batch().v1().jobs().inNamespace(NS).withName(jobName)
                    .withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();
        } catch (Exception e) {
            log.debug("delete job {} failed: {}", jobName, e.getMessage());
        }
    }

    @Override
    public Optional<byte[]> readArtifact(String jobName) {
        // the worker uploads its own artifact to S3 (see launch()); nothing to harvest here
        return Optional.empty();
    }

    @Override
    public List<Managed> listManaged() {
        List<Managed> out = new ArrayList<>();
        for (Job job : client.batch().v1().jobs().inNamespace(NS).withLabel("app", APP_LABEL).list().getItems()) {
            Map<String, String> labels = job.getMetadata().getLabels();
            String jobIdLabel = labels == null ? null : labels.get("jobId");
            if (jobIdLabel == null) {
                continue;
            }
            out.add(new Managed(UUID.fromString(jobIdLabel), job.getMetadata().getName(), !isComplete(job)));
        }
        return out;
    }

    private boolean isComplete(Job job) {
        JobStatus status = job.getStatus();
        if (status == null) {
            return false;
        }
        boolean succeeded = status.getSucceeded() != null && status.getSucceeded() > 0;
        boolean failed = status.getFailed() != null && status.getFailed() > 0;
        return succeeded || failed;
    }

    private String awaitPod(String jobName) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            List<Pod> pods = client.pods().inNamespace(NS).withLabel("job-name", jobName).list().getItems();
            for (Pod pod : pods) {
                String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
                if ("Running".equals(phase) || "Succeeded".equals(phase) || "Failed".equals(phase)) {
                    return pod.getMetadata().getName();
                }
            }
            Thread.sleep(1000);
        }
        return null;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
