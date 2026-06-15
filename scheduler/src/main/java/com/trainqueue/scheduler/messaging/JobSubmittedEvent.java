package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
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
