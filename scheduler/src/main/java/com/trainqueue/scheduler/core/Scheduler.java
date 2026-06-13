package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.config.SchedulerProperties;
import com.trainqueue.scheduler.messaging.JobStatus;
import com.trainqueue.scheduler.messaging.JobStatusEvent;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.StatusPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    /** Highest priority first, then oldest first. */
    public static final Comparator<JobSubmittedEvent> ORDER =
            Comparator.comparingInt(JobSubmittedEvent::priority).reversed()
                    .thenComparing(JobSubmittedEvent::createdAt);

    private final DockerLauncher launcher;
    private final StatusPublisher publisher;
    private final ResourcePool pool;
    private final RetryPolicy retryPolicy;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private final PriorityQueue<JobSubmittedEvent> queue = new PriorityQueue<>(ORDER);
    private final Map<UUID, RunningContainer> running = new HashMap<>();
    private final Set<UUID> cancelled = new HashSet<>();

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "retry");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean active;
    private Thread placementThread;

    public Scheduler(DockerLauncher launcher, StatusPublisher publisher, SchedulerProperties props) {
        this.launcher = launcher;
        this.publisher = publisher;
        this.pool = new ResourcePool(props.pool().cpuMillis(), props.pool().memMb());
        this.retryPolicy = new RetryPolicy(props.retry().baseBackoffMs(), props.retry().maxBackoffMs());
    }

    @PostConstruct
    void start() {
        active = true;
        placementThread = new Thread(this::placementLoop, "placement");
        placementThread.setDaemon(true);
        placementThread.start();
    }

    @PreDestroy
    void stop() {
        // Leave worker containers running: a restarted scheduler re-adopts them.
        active = false;
        lock.lock();
        try {
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
        if (placementThread != null) {
            placementThread.interrupt();
        }
        retryExecutor.shutdownNow();
    }

    /** Enqueue a submitted (or re-submitted) job. */
    public void submit(JobSubmittedEvent event) {
        UUID id = event.jobId();
        lock.lock();
        try {
            if (cancelled.contains(id)) {
                log.info("ignoring submit for cancelled job {}", id);
                return;
            }
            if (running.containsKey(id) || queueContains(id)) {
                log.info("ignoring duplicate submit for job {}", id);
                return;
            }
            queue.add(event);
            workAvailable.signalAll();
            log.info("queued job {} (priority {}, attempt {})", id, event.priority(), event.attempt());
        } finally {
            lock.unlock();
        }
    }

    public void cancel(UUID jobId) {
        RunningContainer rc;
        lock.lock();
        try {
            cancelled.add(jobId);
            queue.removeIf(e -> e.jobId().equals(jobId));
            rc = running.get(jobId);
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
        if (rc != null) {
            log.info("cancelling running job {}", jobId);
            launcher.stopAndRemove(rc.containerId());
        } else {
            log.info("cancel recorded for job {} (queued or not yet seen)", jobId);
        }
    }

    /** Re-attach to a worker container still running after a scheduler restart. */
    public void adopt(JobSubmittedEvent event, String containerId) {
        lock.lock();
        try {
            if (running.containsKey(event.jobId())) {
                return;
            }
            pool.reserve(event.cpuMillis(), event.memMb());
            running.put(event.jobId(), new RunningContainer(event, containerId));
        } finally {
            lock.unlock();
        }
        launcher.watchExit(containerId, code -> onExit(event.jobId(), code));
        log.info("re-adopted running job {} (container {})", event.jobId(), containerId);
    }

    private void placementLoop() {
        lock.lock();
        try {
            while (active) {
                JobSubmittedEvent head = peekPlaceable();
                if (head == null) {
                    workAvailable.await();
                    continue;
                }
                queue.remove(head);
                pool.reserve(head.cpuMillis(), head.memMb());
                lock.unlock();
                try {
                    launchAndTrack(head);
                } catch (Exception e) {
                    log.error("failed to launch job {}: {}", head.jobId(), e.toString());
                    release(head);
                    failOrRetry(head);
                } finally {
                    lock.lock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    // Caller holds the lock. Returns the head only if it fits right now (priority head-of-line).
    private JobSubmittedEvent peekPlaceable() {
        JobSubmittedEvent head = queue.peek();
        if (head != null && pool.fits(head.cpuMillis(), head.memMb())) {
            return head;
        }
        return null;
    }

    private void launchAndTrack(JobSubmittedEvent event) {
        String containerId = launcher.launch(event);
        boolean cancelledDuringLaunch;
        lock.lock();
        try {
            running.put(event.jobId(), new RunningContainer(event, containerId));
            cancelledDuringLaunch = cancelled.contains(event.jobId());
        } finally {
            lock.unlock();
        }
        publisher.publishStatus(JobStatusEvent.now(event.jobId(), event.attempt(), JobStatus.RUNNING));
        launcher.watchExit(containerId, code -> onExit(event.jobId(), code));
        log.info("launched job {} attempt {} (container {})", event.jobId(), event.attempt(), containerId);
        if (cancelledDuringLaunch) {
            launcher.stopAndRemove(containerId);
        }
    }

    private void onExit(UUID jobId, int exitCode) {
        RunningContainer rc;
        boolean wasCancelled;
        lock.lock();
        try {
            rc = running.remove(jobId);
            if (rc == null) {
                return; // already handled by the other of {wait callback, heartbeat}
            }
            pool.release(rc.event().cpuMillis(), rc.event().memMb());
            wasCancelled = cancelled.remove(jobId);
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
        launcher.remove(rc.containerId());
        if (wasCancelled) {
            log.info("job {} was cancelled; suppressing exit (code {})", jobId, exitCode);
            return;
        }
        if (exitCode == 0) {
            log.info("job {} attempt {} succeeded", jobId, rc.event().attempt());
            publisher.publishStatus(JobStatusEvent.now(jobId, rc.event().attempt(), JobStatus.SUCCEEDED));
        } else {
            failOrRetry(rc.event());
        }
    }

    private void failOrRetry(JobSubmittedEvent event) {
        int attempt = event.attempt();
        if (retryPolicy.shouldRetry(attempt, event.maxRetries())) {
            Duration backoff = retryPolicy.backoff(attempt);
            JobSubmittedEvent next = withAttempt(event, attempt + 1);
            log.info("job {} attempt {} failed; retrying as attempt {} in {}ms",
                    event.jobId(), attempt, attempt + 1, backoff.toMillis());
            retryExecutor.schedule(() -> publisher.republishSubmitted(next),
                    backoff.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            log.info("job {} attempt {} failed; no retries left -> FAILED", event.jobId(), attempt);
            publisher.publishStatus(JobStatusEvent.now(event.jobId(), attempt, JobStatus.FAILED));
        }
    }

    @Scheduled(fixedDelayString = "${trainqueue.heartbeat-ms}", initialDelayString = "${trainqueue.heartbeat-ms}")
    void heartbeat() {
        List<Map.Entry<UUID, RunningContainer>> snapshot;
        lock.lock();
        try {
            snapshot = new ArrayList<>(running.entrySet());
        } finally {
            lock.unlock();
        }
        for (Map.Entry<UUID, RunningContainer> e : snapshot) {
            if (!launcher.isRunning(e.getValue().containerId())) {
                log.warn("heartbeat: container for job {} vanished; treating as failure", e.getKey());
                onExit(e.getKey(), 137);
            }
        }
    }

    private void release(JobSubmittedEvent event) {
        lock.lock();
        try {
            pool.release(event.cpuMillis(), event.memMb());
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean queueContains(UUID jobId) {
        return queue.stream().anyMatch(e -> e.jobId().equals(jobId));
    }

    private static JobSubmittedEvent withAttempt(JobSubmittedEvent e, int attempt) {
        return new JobSubmittedEvent(e.jobId(), e.name(), e.dockerImage(), e.command(), e.epochs(),
                e.failAtEpoch(), e.priority(), e.cpuMillis(), e.memMb(), attempt, e.maxRetries(), e.createdAt());
    }
}
