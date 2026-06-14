package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Runs worker-sim jobs. Implemented by {@link DockerLauncher} (local containers)
 * and {@link KubernetesLauncher} (K8s Jobs); selected via the LAUNCHER env var.
 * The {@code handle} is an opaque per-job identifier (a container id, or a Job name).
 */
public interface JobLauncher {

    /** Start a job; returns its handle. {@code outputDir} is a host path for artifacts (docker only). */
    String launch(JobSubmittedEvent event, String outputDir);

    /** Follow the job's stdout, delivering one trimmed line at a time. */
    void streamLogs(String handle, long sinceEpochSecs, Consumer<String> onLine);

    /** Fire {@code onExit} once with the exit code when the job stops. */
    void watchExit(String handle, IntConsumer onExit);

    boolean isRunning(String handle);

    void stopAndRemove(String handle);

    void remove(String handle);

    /** All trainqueue worker jobs this launcher can see (for startup reconcile). */
    List<Managed> listManaged();

    record Managed(UUID jobId, String handle, boolean running) {
    }
}
