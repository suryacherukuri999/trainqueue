package com.trainqueue.scheduler.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot cached at job:{id}:state (EX 1h) for the api's cache-aside read.
 * Mirrors the api's JobResponse fields, plus the latest epoch/loss for live views.
 */
public record JobState(
        UUID id,
        String name,
        String dockerImage,
        String command,
        int epochs,
        Integer failAtEpoch,
        int priority,
        int cpuMillis,
        int memMb,
        JobStatus status,
        int attempt,
        int maxRetries,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer epoch,
        Double loss,
        Double accuracy
) {
    public static JobState of(JobSubmittedEvent e, JobStatus status, Instant startedAt, Instant finishedAt,
                              Integer epoch, Double loss, Double accuracy) {
        return new JobState(e.jobId(), e.name(), e.dockerImage(), e.command(), e.epochs(), e.failAtEpoch(),
                e.priority(), e.cpuMillis(), e.memMb(), status, e.attempt(), e.maxRetries(),
                e.createdAt(), startedAt, finishedAt, epoch, loss, accuracy);
    }
}
