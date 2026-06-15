package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.config.SchedulerProperties;
import com.trainqueue.scheduler.messaging.JobStatus;
import com.trainqueue.scheduler.messaging.JobStatusEvent;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.RedisStreamPublisher;
import com.trainqueue.scheduler.messaging.StatusPublisher;
import com.trainqueue.scheduler.storage.ArtifactStore;
import com.trainqueue.scheduler.storage.CompletionHandler;
import com.trainqueue.scheduler.storage.JobLogProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    /** Highest priority first, then oldest first. */
    public static final Comparator<JobSubmittedEvent> ORDER =
            Comparator.comparingInt(JobSubmittedEvent::priority).reversed()
                    .thenComparing(JobSubmittedEvent::createdAt);

    // A waiting top-of-queue job older than this holds the line so it can't be starved.
    private static final long AGING_MS = 30_000;

    private final JobLauncher launcher;
    private final StatusPublisher publisher;
    private final RedisStreamPublisher redisStream;
    private final JobLogProcessor logProcessor;
    private final CompletionHandler completion;
    private final ArtifactStore artifacts;
    private final ResourcePool pool;
    private final RetryPolicy retryPolicy;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private final PriorityQueue<JobSubmittedEvent> queue = new PriorityQueue<>(ORDER);
    private final Map<UUID, RunningContainer> running = new HashMap<>();
    // jobs pulled from the queue but not yet in `running` (the launch is in flight);
    // tracked so a concurrent submit/reconcile can't double-launch the same job.
    private final Set<UUID> launching = new HashSet<>();
    private final Set<UUID> cancelled = new HashSet<>();

    private volatile boolean active;
    private Thread placementThread;

    public Scheduler(JobLauncher launcher, StatusPublisher publisher, RedisStreamPublisher redisStream,
                     JobLogProcessor logProcessor, CompletionHandler completion, ArtifactStore artifacts,
                     SchedulerProperties props) {
        this.launcher = launcher;
        this.publisher = publisher;
        this.redisStream = redisStream;
        this.logProcessor = logProcessor;
        this.completion = completion;
        this.artifacts = artifacts;
        this.pool = new ResourcePool(props.pool().cpuMillis(), props.pool().memMb());
        this.retryPolicy = new RetryPolicy(props.retry().baseBackoffMs(), props.retry().maxBackoffMs());
    }

    @PostConstruct
    void start() {
        active = true;
        startPlacementThread();
    }

    private void startPlacementThread() {
        placementThread = new Thread(this::placementLoop, "placement");
        placementThread.setDaemon(true);
        placementThread.setUncaughtExceptionHandler((t, ex) ->
                log.error("placement thread terminated abnormally: {}", ex.toString(), ex));
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
    }

    /** Enqueue a submitted (or re-submitted) job. */
    public void submit(JobSubmittedEvent event) {
        UUID id = event.jobId();
        if (pool.exceedsCapacity(event.cpuMillis(), event.memMb())) {
            log.warn("job {} requests {}m cpu / {}MB > pool capacity; marking FAILED", id,
                    event.cpuMillis(), event.memMb());
            publisher.publishStatus(JobStatusEvent.now(id, event.attempt(), JobStatus.FAILED));
            redisStream.publishTerminal(event, Instant.now(), Instant.now(), JobStatus.FAILED);
            return;
        }
        lock.lock();
        try {
            if (cancelled.contains(id)) {
                log.info("ignoring submit for cancelled job {}", id);
                return;
            }
            if (running.containsKey(id) || launching.contains(id) || queueContains(id)) {
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
            redisStream.publishCancelled(jobId, rc.event().attempt());
        } else {
            log.info("cancel recorded for job {} (queued or not yet seen)", jobId);
        }
    }

    /** Re-attach to a worker container still running after a scheduler restart. */
    public void adopt(JobSubmittedEvent event, String containerId, Instant originalStartedAt) {
        Instant startedAt = originalStartedAt != null ? originalStartedAt : Instant.now();
        String outputDir = artifacts.outputDir(event.jobId(), event.attempt()).toString();
        lock.lock();
        try {
            if (running.containsKey(event.jobId())) {
                return;
            }
            pool.reserve(event.cpuMillis(), event.memMb());
            running.put(event.jobId(), new RunningContainer(event, containerId, startedAt, outputDir));
        } finally {
            lock.unlock();
        }
        redisStream.publishRunning(event, startedAt);
        launcher.watchExit(containerId, code -> onExit(event.jobId(), code));
        // replay from the start so earlier epochs are recovered; stable log ids + epoch-keyed
        // metrics keep the replay idempotent, and the original startedAt preserves duration
        launcher.streamLogs(containerId, 0, (line, err) -> logProcessor.process(event, startedAt, line, err));
        log.info("re-adopted running job {} (container {})", event.jobId(), containerId);
    }

    private void placementLoop() {
        while (active) {
            JobSubmittedEvent job = null;
            try {
                job = takeNextPlaceable();
                if (job == null) {
                    continue; // woken for shutdown
                }
                launchAndTrack(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A transient failure must never break scheduling (P0-3); recover and continue.
                log.error("placement error for {}: {}", job == null ? "?" : job.jobId(), e.toString(), e);
                if (job != null) {
                    try {
                        release(job);
                        failOrRetry(job, Instant.now());
                    } catch (Exception recovery) {
                        log.error("recovery failed for {}: {}", job.jobId(), recovery.toString());
                    }
                }
            }
            // JVM Errors (OOM etc.) are intentionally not caught; the supervisor restarts the thread.
        }
    }

    /** Blocks until a fitting job exists, then removes + reserves it. Returns null on shutdown. */
    private JobSubmittedEvent takeNextPlaceable() throws InterruptedException {
        lock.lock();
        try {
            while (active) {
                JobSubmittedEvent next = scanPlaceable();
                if (next != null) {
                    queue.remove(next);
                    pool.reserve(next.cpuMillis(), next.memMb());
                    launching.add(next.jobId()); // claimed but not yet in `running`
                    return next;
                }
                workAvailable.await();
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    // Caller holds the lock. Highest-priority job that fits now (scans past temporarily
    // unplaceable ones), with anti-starvation: if the top job has waited too long and can
    // eventually fit, hold the line until resources free for it.
    private JobSubmittedEvent scanPlaceable() {
        List<JobSubmittedEvent> sorted = queue.stream().sorted(ORDER).toList();
        if (sorted.isEmpty()) {
            return null;
        }
        JobSubmittedEvent head = sorted.get(0);
        boolean headAged = Duration.between(head.createdAt(), Instant.now()).toMillis() > AGING_MS;
        boolean headCanEventuallyFit = !pool.exceedsCapacity(head.cpuMillis(), head.memMb());
        if (headAged && headCanEventuallyFit && !pool.fits(head.cpuMillis(), head.memMb())) {
            return null;
        }
        for (JobSubmittedEvent job : sorted) {
            if (pool.fits(job.cpuMillis(), job.memMb())) {
                return job;
            }
        }
        return null;
    }

    private void launchAndTrack(JobSubmittedEvent event) throws IOException {
        Instant startedAt = Instant.now();
        Path outputDir = artifacts.prepareOutputDir(event.jobId(), event.attempt());
        String containerId = launcher.launch(event, outputDir.toString());
        boolean cancelledDuringLaunch;
        lock.lock();
        try {
            running.put(event.jobId(), new RunningContainer(event, containerId, startedAt, outputDir.toString()));
            launching.remove(event.jobId());
            cancelledDuringLaunch = cancelled.contains(event.jobId());
        } finally {
            lock.unlock();
        }
        publisher.publishStatus(JobStatusEvent.now(event.jobId(), event.attempt(), JobStatus.RUNNING));
        redisStream.publishRunning(event, startedAt);
        launcher.watchExit(containerId, code -> onExit(event.jobId(), code));
        launcher.streamLogs(containerId, 0, (line, err) -> logProcessor.process(event, startedAt, line, err));
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
        // grab the artifact (k8s copies it from the pod) before the worker is removed
        Optional<byte[]> artifact = exitCode == 0 ? launcher.readArtifact(rc.containerId()) : Optional.empty();
        launcher.remove(rc.containerId());
        if (wasCancelled) {
            log.info("job {} was cancelled; suppressing exit (code {})", jobId, exitCode);
            // Overwrite the cached snapshot (a late metric may have re-SET it to RUNNING)
            // so cache-aside reads agree with the api's CANCELLED.
            redisStream.publishTerminal(rc.event(), rc.startedAt(), Instant.now(), JobStatus.CANCELLED);
            completion.recordTerminal(rc.event(), rc.startedAt(), Instant.now(), Optional.empty());
            return;
        }
        if (exitCode == 0) {
            log.info("job {} attempt {} succeeded", jobId, rc.event().attempt());
            publisher.publishStatus(JobStatusEvent.now(jobId, rc.event().attempt(), JobStatus.SUCCEEDED));
            redisStream.publishTerminal(rc.event(), rc.startedAt(), Instant.now(), JobStatus.SUCCEEDED);
            completion.recordTerminal(rc.event(), rc.startedAt(), Instant.now(), artifact);
        } else {
            failOrRetry(rc.event(), rc.startedAt());
        }
    }

    private void failOrRetry(JobSubmittedEvent event, Instant startedAt) {
        int attempt = event.attempt();
        // finalize this attempt's metrics (finishedAt set) whether or not we retry
        completion.recordTerminal(event, startedAt, Instant.now(), Optional.empty());
        if (retryPolicy.shouldRetry(attempt, event.maxRetries())) {
            Duration backoff = retryPolicy.backoff(attempt);
            JobSubmittedEvent next = withAttempt(event, attempt + 1);
            log.info("job {} attempt {} failed; retrying as attempt {} in {}ms",
                    event.jobId(), attempt, attempt + 1, backoff.toMillis());
            publisher.scheduleResubmit(next, Instant.now().plus(backoff)); // durable, restart-safe
        } else {
            log.info("job {} attempt {} failed; no retries left -> FAILED", event.jobId(), attempt);
            publisher.publishStatus(JobStatusEvent.now(event.jobId(), attempt, JobStatus.FAILED));
            redisStream.publishTerminal(event, startedAt, Instant.now(), JobStatus.FAILED);
        }
    }

    @Scheduled(fixedDelayString = "${trainqueue.heartbeat-ms}", initialDelayString = "${trainqueue.heartbeat-ms}")
    void heartbeat() {
        // Supervisor: if the placement thread died (e.g. a JVM Error), bring it back.
        if (active && (placementThread == null || !placementThread.isAlive())) {
            log.error("placement thread is not alive; restarting it");
            startPlacementThread();
        }
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
            launching.remove(event.jobId());
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean queueContains(UUID jobId) {
        return queue.stream().anyMatch(e -> e.jobId().equals(jobId));
    }

    private static JobSubmittedEvent withAttempt(JobSubmittedEvent e, int attempt) {
        return new JobSubmittedEvent(UUID.randomUUID(), e.jobId(), e.name(), e.dockerImage(), e.command(),
                e.epochs(), e.failAtEpoch(), e.priority(), e.cpuMillis(), e.memMb(), attempt,
                e.maxRetries(), e.createdAt());
    }
}
