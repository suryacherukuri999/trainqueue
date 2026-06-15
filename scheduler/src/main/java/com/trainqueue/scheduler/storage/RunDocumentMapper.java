package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.time.Duration;
import java.time.Instant;

/** Builds the persisted run document from a job's accumulated metrics and timing. */
public final class RunDocumentMapper {

    private RunDocumentMapper() {
    }

    /** Final document written when an attempt completes. */
    public static RunDocument completed(JobSubmittedEvent event, Instant startedAt, Instant finishedAt,
                                        MetricsCollector.Snapshot snapshot) {
        return doc(event, startedAt, finishedAt, Duration.between(startedAt, finishedAt).toMillis(), snapshot);
    }

    /** Incremental document upserted as epochs stream in, so partial metrics survive a crash. */
    public static RunDocument inProgress(JobSubmittedEvent event, Instant startedAt,
                                         MetricsCollector.Snapshot snapshot) {
        return doc(event, startedAt, null, Duration.between(startedAt, Instant.now()).toMillis(), snapshot);
    }

    private static RunDocument doc(JobSubmittedEvent event, Instant startedAt, Instant finishedAt,
                                   long durationMs, MetricsCollector.Snapshot snapshot) {
        return new RunDocument(
                event.jobId() + "-" + event.attempt(),
                event.jobId(),
                event.attempt(),
                durationMs,
                snapshot.epochs(),
                snapshot.lossCurve(),
                snapshot.finalAccuracy(),
                startedAt,
                finishedAt);
    }
}
