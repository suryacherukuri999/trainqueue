package com.trainqueue.api.job;

import com.trainqueue.api.inbox.InboxEvent;
import com.trainqueue.api.inbox.InboxRepository;
import com.trainqueue.api.messaging.JobStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies status events from the scheduler to the jobs table with explicit, legal
 * transitions. Terminal states are absorbing (nothing applies after SUCCEEDED/
 * FAILED/CANCELLED). A retry resumes via RUNNING(n)->RUNNING(n+1). Idempotent:
 * each event is recorded in the inbox, so redeliveries are ignored.
 */
@Service
public class JobStateMachine {

    private static final Logger log = LoggerFactory.getLogger(JobStateMachine.class);
    private static final String CONSUMER = "api";

    private final JobRepository jobs;
    private final InboxRepository inbox;

    public JobStateMachine(JobRepository jobs, InboxRepository inbox) {
        this.jobs = jobs;
        this.inbox = inbox;
    }

    @Transactional
    public void apply(JobStatusEvent event) {
        if (inbox.existsById(InboxEvent.idFor(CONSUMER, event.eventId()))) {
            log.debug("duplicate status event {} ignored", event.eventId());
            return;
        }
        jobs.findById(event.jobId()).ifPresentOrElse(
                job -> applyTo(job, event),
                () -> log.warn("status event for unknown job {}", event.jobId()));
        // record after applying so a failure rolls back both and the event is redelivered
        inbox.save(new InboxEvent(CONSUMER, event.eventId()));
    }

    private void applyTo(Job job, JobStatusEvent event) {
        if (!isLegal(job, event)) {
            log.info("ignoring {} attempt {} for {} in state {} attempt {}",
                    event.status(), event.attempt(), event.jobId(), job.getStatus(), job.getAttempt());
            return;
        }
        if (event.status() == JobStatus.RUNNING) {
            job.markRunning(event.attempt());
        } else {
            job.markTerminal(event.status(), event.attempt());
        }
    }

    private boolean isLegal(Job job, JobStatusEvent event) {
        if (job.getStatus().isTerminal()) {
            return false; // absorbing
        }
        int attempt = event.attempt();
        return switch (event.status()) {
            // initial start of the current attempt, or a retry resuming on a higher attempt
            case RUNNING -> (job.getStatus() == JobStatus.QUEUED && attempt == job.getAttempt())
                    || (job.getStatus() == JobStatus.RUNNING && attempt > job.getAttempt());
            // success only from RUNNING; failure also from QUEUED (unschedulable / launch failure)
            case SUCCEEDED -> job.getStatus() == JobStatus.RUNNING && attempt >= job.getAttempt();
            case FAILED -> (job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.QUEUED)
                    && attempt >= job.getAttempt();
            // operator cancel (the api normally sets this itself; accept from non-terminal)
            case CANCELLED -> true;
            case QUEUED -> false;
        };
    }
}
