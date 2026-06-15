package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Pushes live job activity to Redis: per-job events on the pub/sub channel
 * job:{id}:events (fanned out by the gateway), and a cached snapshot at
 * job:{id}:state for the api's cache-aside reads.
 */
@Component
public class RedisStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamPublisher.class);
    private static final Duration STATE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisStreamPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void publishRunning(JobSubmittedEvent e, Instant startedAt) {
        setState(JobState.of(e, JobStatus.RUNNING, startedAt, null, null, null, null));
        publish(e.jobId(), StreamEvent.status(e.jobId(), JobStatus.RUNNING, e.attempt()));
    }

    public void publishTerminal(JobSubmittedEvent e, Instant startedAt, Instant finishedAt, JobStatus status) {
        setState(JobState.of(e, status, startedAt, finishedAt, null, null, null));
        publish(e.jobId(), StreamEvent.status(e.jobId(), status, e.attempt()));
    }

    public void publishCancelled(UUID jobId, int attempt) {
        // The api owns CANCELLED state (it deletes the cache key); just notify live viewers.
        publish(jobId, StreamEvent.status(jobId, JobStatus.CANCELLED, attempt));
    }

    /** Cache the latest metric in the job snapshot and broadcast it to live viewers. */
    public void publishMetric(JobSubmittedEvent e, Instant startedAt, WorkerMetric m) {
        setState(JobState.of(e, JobStatus.RUNNING, startedAt, null, m.epoch(), m.loss(), m.accuracy()));
        publish(e.jobId(), StreamEvent.metric(e.jobId(), e.attempt(), m.epoch(), m.loss(), m.accuracy()));
    }

    // Streaming/cache writes are best-effort: Redis is never allowed to affect
    // authoritative execution state (a Redis outage must not fail a running job).
    private void setState(JobState state) {
        try {
            redis.opsForValue().set("job:" + state.id() + ":state", json(state), STATE_TTL);
        } catch (Exception e) {
            log.warn("redis setState failed for {}: {}", state.id(), e.getMessage());
        }
    }

    private void publish(UUID jobId, StreamEvent event) {
        try {
            redis.convertAndSend("job:" + jobId + ":events", json(event));
        } catch (Exception e) {
            log.warn("redis publish failed for {}: {}", jobId, e.getMessage());
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize stream payload", e);
        }
    }
}
