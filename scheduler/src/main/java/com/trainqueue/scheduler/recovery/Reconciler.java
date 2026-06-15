package com.trainqueue.scheduler.recovery;

import com.trainqueue.scheduler.core.JobLauncher;
import com.trainqueue.scheduler.core.Scheduler;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.StatusPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * On startup, reconciles desired state (the api's jobs) against actual workers:
 * adopt valid RUNNING workers, re-queue active jobs whose worker is gone (including
 * QUEUED jobs lost from the in-memory queue), and stop workers for cancelled,
 * terminal, or unknown jobs.
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final ApiClient api;
    private final JobLauncher launcher;
    private final Scheduler scheduler;
    private final StatusPublisher publisher;

    public Reconciler(ApiClient api, JobLauncher launcher, Scheduler scheduler, StatusPublisher publisher) {
        this.api = api;
        this.launcher = launcher;
        this.scheduler = scheduler;
        this.publisher = publisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        List<ApiClient.JobInfo> jobs;
        try {
            jobs = api.allJobs();
        } catch (Exception e) {
            log.warn("startup reconcile skipped; api not reachable: {}", e.getMessage());
            return;
        }

        Map<UUID, ApiClient.JobInfo> byId = new HashMap<>();
        for (ApiClient.JobInfo j : jobs) {
            byId.put(j.id(), j);
        }

        List<JobLauncher.Managed> managed = launcher.listManaged();
        Set<UUID> managedIds = new HashSet<>();
        log.info("reconciling {} job(s) against {} worker(s)", jobs.size(), managed.size());

        // 1. every managed worker must match a RUNNING job; otherwise stop it
        for (JobLauncher.Managed m : managed) {
            managedIds.add(m.jobId());
            ApiClient.JobInfo job = byId.get(m.jobId());
            if (job == null || job.isTerminal()) {
                log.info("stopping worker for {} (job {})", m.jobId(), job == null ? "unknown" : job.status());
                launcher.stopAndRemove(m.handle());
            } else if ("RUNNING".equals(job.status()) && m.running()) {
                scheduler.adopt(toEvent(job), m.handle(), job.startedAt());
            } else {
                // worker not running, or job still QUEUED with a stale worker — reset and re-queue
                launcher.remove(m.handle());
                requeue(job);
            }
        }

        // 2. active jobs with no worker (lost QUEUED, or RUNNING whose worker vanished)
        for (ApiClient.JobInfo job : jobs) {
            if (job.isActive() && !managedIds.contains(job.id())) {
                requeue(job);
            }
        }
    }

    /**
     * Re-queue a job that has no worker — unless a retry for it is already scheduled in the
     * durable outbox. Without this guard, a restart during a retry's backoff window would
     * re-queue the job immediately AND let the backoff fire later, running it twice.
     */
    private void requeue(ApiClient.JobInfo job) {
        if (publisher.hasPendingResubmit(job.id())) {
            log.info("not re-queueing {} ({}); a retry is already scheduled", job.id(), job.status());
            return;
        }
        log.info("re-queueing {} ({}) with no worker", job.id(), job.status());
        scheduler.submit(toEvent(job));
    }

    private static JobSubmittedEvent toEvent(ApiClient.JobInfo j) {
        return new JobSubmittedEvent(UUID.randomUUID(), j.id(), j.name(), j.dockerImage(), j.command(),
                j.epochs(), j.failAtEpoch(), j.priority(), j.cpuMillis(), j.memMb(), j.attempt(),
                j.maxRetries(), j.createdAt());
    }
}
