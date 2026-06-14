package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.time.Duration;
import java.time.Instant;

/** Builds the persisted run document from a job's accumulated metrics and timing. */
public final class RunDocumentMapper {

    private RunDocumentMapper() {
    }

    public static RunDocument map(JobSubmittedEvent event, Instant startedAt, Instant finishedAt,
                                  MetricsCollector.Snapshot snapshot) {
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();
        return new RunDocument(
                event.jobId() + "-" + event.attempt(),
                event.jobId(),
                event.attempt(),
                durationMs,
                snapshot.epochs(),
                snapshot.lossCurve(),
                snapshot.finalAccuracy(),
                finishedAt);
    }
}
