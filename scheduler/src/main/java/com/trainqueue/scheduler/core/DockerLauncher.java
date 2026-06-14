package com.trainqueue.scheduler.core;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.WaitResponse;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Component
public class DockerLauncher {

    private static final Logger log = LoggerFactory.getLogger(DockerLauncher.class);
    private static final String PREFIX = "trainqueue-";

    private final DockerClient docker;

    public DockerLauncher(DockerClient docker) {
        this.docker = docker;
    }

    public record Managed(UUID jobId, String containerId, boolean running) {
    }

    /** Create and start a worker-sim container; returns its id. */
    public String launch(JobSubmittedEvent e) {
        String name = containerName(e.jobId());
        removeIfExists(name);

        List<String> env = new ArrayList<>();
        env.add("JOB_ID=" + e.jobId());
        env.add("EPOCHS=" + e.epochs());
        if (e.failAtEpoch() != null) {
            env.add("FAIL_AT_EPOCH=" + e.failAtEpoch());
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNanoCPUs((long) e.cpuMillis() * 1_000_000L) // 1000 millis == 1 CPU == 1e9 nanos
                .withMemory((long) e.memMb() * 1024L * 1024L);

        CreateContainerResponse created = docker.createContainerCmd(e.dockerImage())
                .withName(name)
                .withEnv(env)
                .withHostConfig(hostConfig)
                .exec();
        docker.startContainerCmd(created.getId()).exec();
        return created.getId();
    }

    /**
     * Follow a container's stdout, delivering one trimmed line at a time.
     * {@code sinceEpochSecs} of 0 replays from the start; pass "now" to skip history.
     */
    public void streamLogs(String containerId, long sinceEpochSecs, Consumer<String> onLine) {
        docker.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(false)
                .withFollowStream(true)
                .withSince((int) sinceEpochSecs)
                .exec(new ResultCallback.Adapter<>() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onNext(Frame frame) {
                        buffer.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        int newline;
                        while ((newline = buffer.indexOf("\n")) >= 0) {
                            String line = buffer.substring(0, newline).trim();
                            buffer.delete(0, newline + 1);
                            if (!line.isEmpty()) {
                                onLine.accept(line);
                            }
                        }
                    }
                });
    }

    /** Fire {@code onExit} once with the container's exit code when it stops. */
    public void watchExit(String containerId, IntConsumer onExit) {
        docker.waitContainerCmd(containerId).exec(new WaitContainerResultCallback() {
            @Override
            public void onNext(WaitResponse response) {
                Integer code = response.getStatusCode();
                onExit.accept(code == null ? 1 : code);
            }

            @Override
            public void onError(Throwable throwable) {
                // The heartbeat is the backstop if the wait stream breaks (e.g. daemon hiccup).
                log.warn("wait stream error for container {}: {}", containerId, throwable.getMessage());
            }
        });
    }

    public boolean isRunning(String containerId) {
        try {
            InspectContainerResponse inspect = docker.inspectContainerCmd(containerId).exec();
            return Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (NotFoundException e) {
            return false;
        }
    }

    public void stopAndRemove(String containerId) {
        try {
            docker.stopContainerCmd(containerId).withTimeout(3).exec();
        } catch (Exception e) {
            log.debug("stop {} failed (already gone?): {}", containerId, e.getMessage());
        }
        remove(containerId);
    }

    public void remove(String containerId) {
        try {
            docker.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException ignored) {
            // already gone
        } catch (Exception e) {
            log.warn("remove {} failed: {}", containerId, e.getMessage());
        }
    }

    /** All trainqueue worker containers currently known to docker (running or not). */
    public List<Managed> listManaged() {
        List<Managed> out = new ArrayList<>();
        for (Container c : docker.listContainersCmd().withShowAll(true).exec()) {
            UUID jobId = parseJobId(c.getNames());
            if (jobId == null) {
                continue;
            }
            out.add(new Managed(jobId, c.getId(), "running".equalsIgnoreCase(c.getState())));
        }
        return out;
    }

    private void removeIfExists(String name) {
        for (Container c : docker.listContainersCmd().withShowAll(true).exec()) {
            for (String n : c.getNames()) {
                if (strip(n).equals(name)) {
                    remove(c.getId());
                }
            }
        }
    }

    private static String containerName(UUID jobId) {
        return PREFIX + jobId;
    }

    private static UUID parseJobId(String[] names) {
        if (names == null) {
            return null;
        }
        for (String raw : names) {
            String name = strip(raw);
            if (name.startsWith(PREFIX)) {
                try {
                    return UUID.fromString(name.substring(PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    // e.g. trainqueue-postgres-1 — not a worker container
                }
            }
        }
        return null;
    }

    private static String strip(String name) {
        return name.startsWith("/") ? name.substring(1) : name;
    }
}
