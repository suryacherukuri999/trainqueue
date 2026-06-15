package com.trainqueue.api.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.api.job.dto.JobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Cache-aside reads of the scheduler-maintained job:{id}:state snapshot. Holds the
 * hot status of running jobs so frequent polls avoid Postgres.
 */
@Component
public class JobStateCache {

    private static final Logger log = LoggerFactory.getLogger(JobStateCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public JobStateCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Optional<JobResponse> read(UUID id) {
        String json;
        try {
            json = redis.opsForValue().get(key(id));
        } catch (Exception e) {
            // Redis down/slow: treat as a miss so the caller falls back to Postgres.
            log.warn("redis read failed for {}, falling back to db: {}", id, e.getMessage());
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, JobResponse.class));
        } catch (Exception e) {
            log.warn("ignoring unparseable cached state for {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public void evict(UUID id) {
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("redis evict failed for {}: {}", id, e.getMessage());
        }
    }

    private static String key(UUID id) {
        return "job:" + id + ":state";
    }
}
