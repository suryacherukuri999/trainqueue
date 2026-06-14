package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.time.Instant;

/** A placed job: the submission that produced it (for retry), its container id, and when it was placed. */
public record RunningContainer(JobSubmittedEvent event, String containerId, Instant startedAt) {
}
