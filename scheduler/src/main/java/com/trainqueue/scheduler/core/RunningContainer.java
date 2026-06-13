package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;

/** A placed job: the submission that produced it (for retry) plus its container id. */
public record RunningContainer(JobSubmittedEvent event, String containerId) {
}
