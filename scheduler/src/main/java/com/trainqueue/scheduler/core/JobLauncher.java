package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Runs worker-sim jobs. Implemented by {@link DockerLauncher} (local containers)
 * and {@link KubernetesLauncher} (K8s Jobs); selected via the LAUNCHER env var.
 * The {@code handle} is an opaque per-job identifier (a container id, or a Job name).
 */
public interface JobLauncher {

    /** Start a job; returns its handle. {@code outputDir} is a host path for artifacts (docker only). */
    String launch(JobSubmittedEvent event, String outputDir);

    /** Follow the job's stdout+stderr, delivering one trimmed line at a time. */
    void streamLogs(String handle, long sinceEpochSecs, LogSink sink);

    @FunctionalInterface
    interface LogSink {
        void accept(String line, boolean stderr);
    }

    /** Fire {@code onExit} once with the exit code when the job stops. */
    void watchExit(String handle, IntConsumer onExit);

    boolean isRunning(String handle);

    void stopAndRemove(String handle);

    void remove(String handle);

    /** All trainqueue worker jobs this launcher can see (for startup reconcile). */
    List<Managed> listManaged();

    /**
     * Read the model artifact from a finished worker, if this launcher fetches it that
     * way (Kubernetes copies it from the pod before deletion). Docker uses a host bind
     * mount instead and returns empty here. Must be called before the worker is removed.
     */
    default Optional<byte[]> readArtifact(String handle) {
        return Optional.empty();
    }

    record Managed(UUID jobId, String handle, boolean running) {
    }
}
