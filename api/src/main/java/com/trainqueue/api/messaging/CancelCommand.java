package com.trainqueue.api.messaging;

import java.util.UUID;

/** Published to jobs.control when an operator cancels a job; the scheduler stops the work. */
public record CancelCommand(UUID eventId, UUID jobId) {
}
