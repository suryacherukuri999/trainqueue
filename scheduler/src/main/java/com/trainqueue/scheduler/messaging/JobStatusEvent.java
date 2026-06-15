package com.trainqueue.scheduler.messaging;

import java.time.Instant;
import java.util.UUID;

public record JobStatusEvent(
        UUID eventId,
        UUID jobId,
        int attempt,
        JobStatus status,
        Instant ts
) {
    public static JobStatusEvent now(UUID jobId, int attempt, JobStatus status) {
        return new JobStatusEvent(UUID.randomUUID(), jobId, attempt, status, Instant.now());
    }
}
