package com.trainqueue.api.job;

import com.trainqueue.api.messaging.JobStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies status events from the scheduler to the jobs table. Only forward
 * progress is accepted, ordered by (attempt, state); this both enforces legal
 * transitions and makes the application idempotent against duplicate and
 * out-of-order events — a replayed RUNNING or a late event for an older attempt
 * ranks no higher than current state and is dropped.
 */
@Service
public class JobStateMachine {

    private static final Logger log = LoggerFactory.getLogger(JobStateMachine.class);

    private final JobRepository jobs;

    public JobStateMachine(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public void apply(JobStatusEvent event) {
        jobs.findById(event.jobId()).ifPresentOrElse(job -> {
            if (!isForwardProgress(job, event)) {
                log.info("ignoring {} attempt {} for {} in state {} attempt {}",
                        event.status(), event.attempt(), event.jobId(), job.getStatus(), job.getAttempt());
                return;
            }
            if (event.status() == JobStatus.RUNNING) {
                job.markRunning(event.attempt());
            } else {
                job.markTerminal(event.status(), event.attempt());
            }
        }, () -> log.warn("status event for unknown job {}", event.jobId()));
    }

    private boolean isForwardProgress(Job job, JobStatusEvent event) {
        return progressKey(event.attempt(), event.status()) > progressKey(job.getAttempt(), job.getStatus());
    }

    // Higher attempt always wins; within an attempt, RUNNING precedes any terminal state.
    private long progressKey(int attempt, JobStatus status) {
        return (long) attempt * 10 + rank(status);
    }

    private int rank(JobStatus status) {
        return switch (status) {
            case QUEUED -> 0;
            case RUNNING -> 1;
            case SUCCEEDED, FAILED, CANCELLED -> 2;
        };
    }
}
