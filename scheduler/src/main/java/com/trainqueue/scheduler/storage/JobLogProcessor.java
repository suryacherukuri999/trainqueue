package com.trainqueue.scheduler.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.RedisStreamPublisher;
import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Handles one worker log line: broadcast live, collect metrics, persist, index for search. */
@Component
public class JobLogProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobLogProcessor.class);

    private final ObjectMapper mapper;
    private final RedisStreamPublisher redis;
    private final MetricsCollector metrics;
    private final EsLogIndexer es;
    private final RunRepository runs;
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public JobLogProcessor(ObjectMapper mapper, RedisStreamPublisher redis, MetricsCollector metrics,
                           EsLogIndexer es, RunRepository runs) {
        this.mapper = mapper;
        this.redis = redis;
        this.metrics = metrics;
        this.es = es;
        this.runs = runs;
    }

    public void process(JobSubmittedEvent event, Instant startedAt, String line, boolean stderr) {
        // Stable per-(job,attempt) sequence; a replay from line 1 reproduces the same ids.
        long seq = sequences.computeIfAbsent(key(event), k -> new AtomicLong()).incrementAndGet();
        Optional<WorkerMetric> parsed = WorkerMetric.parse(mapper, line);
        if (parsed.isPresent()) {
            WorkerMetric m = parsed.get();
            redis.publishMetric(event, startedAt, m);
            metrics.record(event.jobId(), event.attempt(), m);
            es.add(LogDoc.metric(event.jobId(), event.attempt(), seq, m));
            upsertProgress(event, startedAt);
        } else {
            es.add(LogDoc.message(event.jobId(), event.attempt(), seq, stderr ? "ERROR" : "INFO", line));
        }
    }

    public void clear(JobSubmittedEvent event) {
        sequences.remove(key(event));
    }

    private void upsertProgress(JobSubmittedEvent event, Instant startedAt) {
        try {
            runs.save(RunDocumentMapper.inProgress(event, startedAt,
                    metrics.snapshot(event.jobId(), event.attempt())));
        } catch (Exception e) {
            // best-effort: a Mongo blip must not break log streaming
            log.debug("incremental metrics upsert failed for {}: {}", event.jobId(), e.getMessage());
        }
    }

    private static String key(JobSubmittedEvent event) {
        return event.jobId() + "-" + event.attempt();
    }
}
