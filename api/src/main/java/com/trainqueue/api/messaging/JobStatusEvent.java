package com.trainqueue.api.messaging;

import com.trainqueue.api.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

/** Published by the scheduler to jobs.status; the api applies it via the JobStateMachine. */
public record JobStatusEvent(
        UUID jobId,
        int attempt,
        JobStatus status,
        Instant ts
) {
}
