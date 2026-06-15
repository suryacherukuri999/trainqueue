package com.trainqueue.scheduler.messaging;

import java.time.Instant;
import java.util.UUID;

/** Published to job:{id}:events for live consumers (status changes and per-epoch metrics). */
public record StreamEvent(
        String type,          // "status" | "metric"
        UUID jobId,
        JobStatus status,     // set for type=status
        Integer attempt,
        Integer epoch,        // set for type=metric
        Double loss,
        Double accuracy,
        Instant ts
) {
    public static StreamEvent status(UUID jobId, JobStatus status, int attempt) {
        return new StreamEvent("status", jobId, status, attempt, null, null, null, Instant.now());
    }

    public static StreamEvent metric(UUID jobId, int attempt, int epoch, double loss, double accuracy) {
        return new StreamEvent("metric", jobId, null, attempt, epoch, loss, accuracy, Instant.now());
    }
}
