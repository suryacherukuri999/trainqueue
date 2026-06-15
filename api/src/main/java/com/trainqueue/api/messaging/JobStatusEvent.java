package com.trainqueue.api.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trainqueue.api.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

/** Published by the scheduler to jobs.status; the api applies it via the JobStateMachine. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStatusEvent(
        UUID eventId,
        UUID jobId,
        int attempt,
        JobStatus status,
        Instant ts
) {
}
