package com.trainqueue.api.job;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Temporary launcher: runs worker-sim directly via the docker CLI, one OS thread
 * per job. The scheduler service replaces this entirely in milestone 2 (Kafka +
 * resource-aware placement); nothing here is meant to survive that.
 */
@Component
public class LocalDockerLauncher {

    private static final Logger log = LoggerFactory.getLogger(LocalDockerLauncher.class);

    private final JobStatusUpdater status;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public LocalDockerLauncher(JobStatusUpdater status) {
        this.status = status;
    }

    public record LaunchSpec(UUID jobId, String image, int epochs, Integer failAtEpoch,
                             int cpuMillis, int memMb) {
    }

    public void launch(LaunchSpec spec) {
        pool.submit(() -> run(spec));
    }

    public void stop(UUID jobId) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName(jobId))
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (IOException e) {
            log.warn("failed to stop container for {}: {}", jobId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(LaunchSpec spec) {
        status.markRunning(spec.jobId());
        int exit;
        try {
            Process p = new ProcessBuilder(dockerCommand(spec))
                    .redirectErrorStream(true)
                    .start();
            drain(p, spec.jobId());
            exit = p.waitFor();
        } catch (IOException e) {
            log.error("failed to launch container for {}: {}", spec.jobId(), e.getMessage());
            status.markFinished(spec.jobId(), JobStatus.FAILED);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        status.markFinished(spec.jobId(), exit == 0 ? JobStatus.SUCCEEDED : JobStatus.FAILED);
    }

    private List<String> dockerCommand(LaunchSpec spec) {
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--name", containerName(spec.jobId()),
                "--cpus", String.format("%.3f", spec.cpuMillis() / 1000.0),
                "--memory", spec.memMb() + "m",
                "-e", "JOB_ID=" + spec.jobId(),
                "-e", "EPOCHS=" + spec.epochs()));
        if (spec.failAtEpoch() != null) {
            cmd.add("-e");
            cmd.add("FAIL_AT_EPOCH=" + spec.failAtEpoch());
        }
        cmd.add(spec.image());
        return cmd;
    }

    private void drain(Process p, UUID jobId) throws IOException {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.info("[{}] {}", jobId, line);
            }
        }
    }

    private static String containerName(UUID jobId) {
        return "trainqueue-" + jobId;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
