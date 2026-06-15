package com.trainqueue.scheduler.recovery;

import com.trainqueue.scheduler.core.JobLauncher;
import com.trainqueue.scheduler.core.Scheduler;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * On startup, reconciles the api's RUNNING jobs against live worker containers:
 * a job whose container survived the restart is re-adopted; one whose container
 * is gone is re-queued so it runs again.
 */
@Component
public class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final ApiClient api;
    private final JobLauncher launcher;
    private final Scheduler scheduler;

    public Reconciler(ApiClient api, JobLauncher launcher, Scheduler scheduler) {
        this.api = api;
        this.launcher = launcher;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        List<ApiClient.JobInfo> runningJobs;
        try {
            runningJobs = api.runningJobs();
        } catch (Exception e) {
            log.warn("startup reconcile skipped; api not reachable: {}", e.getMessage());
            return;
        }

        Map<UUID, JobLauncher.Managed> managed = new HashMap<>();
        for (JobLauncher.Managed m : launcher.listManaged()) {
            managed.put(m.jobId(), m);
        }

        log.info("reconciling {} RUNNING job(s) against {} worker job(s)",
                runningJobs.size(), managed.size());
        for (ApiClient.JobInfo job : runningJobs) {
            JobSubmittedEvent event = toEvent(job);
            JobLauncher.Managed handle = managed.get(job.id());
            if (handle != null && handle.running()) {
                scheduler.adopt(event, handle.handle());
            } else {
                log.info("job {} was RUNNING but its worker is gone; re-queueing", job.id());
                scheduler.submit(event);
            }
        }
    }

    private static JobSubmittedEvent toEvent(ApiClient.JobInfo j) {
        return new JobSubmittedEvent(UUID.randomUUID(), j.id(), j.name(), j.dockerImage(), j.command(),
                j.epochs(), j.failAtEpoch(), j.priority(), j.cpuMillis(), j.memMb(), j.attempt(),
                j.maxRetries(), j.createdAt());
    }
}
