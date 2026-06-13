package com.trainqueue.scheduler.recovery;

import com.trainqueue.scheduler.core.DockerLauncher;
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
    private final DockerLauncher launcher;
    private final Scheduler scheduler;

    public Reconciler(ApiClient api, DockerLauncher launcher, Scheduler scheduler) {
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

        Map<UUID, DockerLauncher.Managed> containers = new HashMap<>();
        for (DockerLauncher.Managed m : launcher.listManaged()) {
            containers.put(m.jobId(), m);
        }

        log.info("reconciling {} RUNNING job(s) against {} worker container(s)",
                runningJobs.size(), containers.size());
        for (ApiClient.JobInfo job : runningJobs) {
            JobSubmittedEvent event = toEvent(job);
            DockerLauncher.Managed container = containers.get(job.id());
            if (container != null && container.running()) {
                scheduler.adopt(event, container.containerId());
            } else {
                log.info("job {} was RUNNING but its container is gone; re-queueing", job.id());
                scheduler.submit(event);
            }
        }
    }

    private static JobSubmittedEvent toEvent(ApiClient.JobInfo j) {
        return new JobSubmittedEvent(j.id(), j.name(), j.dockerImage(), j.command(), j.epochs(),
                j.failAtEpoch(), j.priority(), j.cpuMillis(), j.memMb(), j.attempt(), j.maxRetries(), j.createdAt());
    }
}
