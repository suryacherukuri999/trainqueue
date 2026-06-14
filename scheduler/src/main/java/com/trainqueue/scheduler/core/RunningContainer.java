package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

import java.time.Instant;

/** A placed job: the submission (for retry), its container id, when it was placed, and its /output dir. */
public record RunningContainer(JobSubmittedEvent event, String containerId, Instant startedAt, String outputDir) {
}
