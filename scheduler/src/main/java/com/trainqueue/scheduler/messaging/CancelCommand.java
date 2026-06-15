package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelCommand(UUID eventId, UUID jobId) {
}
