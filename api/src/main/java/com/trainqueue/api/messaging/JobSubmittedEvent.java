package com.trainqueue.api.messaging;

import java.time.Instant;
import java.util.UUID;

/** Published to jobs.submitted; the scheduler consumes it to place and run the job. */
public record JobSubmittedEvent(
        UUID eventId,
        UUID jobId,
        String name,
        String dockerImage,
        String command,
        int epochs,
        Integer failAtEpoch,
        int priority,
        int cpuMillis,
        int memMb,
        int attempt,
        int maxRetries,
        Instant createdAt
) {
}
