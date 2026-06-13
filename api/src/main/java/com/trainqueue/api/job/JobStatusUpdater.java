package com.trainqueue.api.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Best-effort status sink the launcher calls from its background thread. Unlike
 * {@link JobService#cancel}, illegal transitions are ignored rather than thrown:
 * a container that exits after the job was already cancelled is expected, not an
 * error. Separate bean so these calls go through the transactional proxy.
 */
@Service
public class JobStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(JobStatusUpdater.class);

    private final JobRepository jobs;

    public JobStatusUpdater(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public void markRunning(UUID id) {
        jobs.findById(id).ifPresent(job -> {
            if (job.getStatus().canTransitionTo(JobStatus.RUNNING)) {
                job.start();
            } else {
                log.info("ignoring RUNNING for {} in state {}", id, job.getStatus());
            }
        });
    }

    @Transactional
    public void markFinished(UUID id, JobStatus terminal) {
        jobs.findById(id).ifPresent(job -> {
            if (job.getStatus().canTransitionTo(terminal)) {
                job.finish(terminal);
            } else {
                log.info("ignoring {} for {} in state {}", terminal, id, job.getStatus());
            }
        });
    }
}
