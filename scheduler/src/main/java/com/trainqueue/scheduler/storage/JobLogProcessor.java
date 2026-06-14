package com.trainqueue.scheduler.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.RedisStreamPublisher;
import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Handles one worker stdout line: broadcast live, collect metrics, index for search. */
@Component
public class JobLogProcessor {

    private final ObjectMapper mapper;
    private final RedisStreamPublisher redis;
    private final MetricsCollector metrics;
    private final EsLogIndexer es;

    public JobLogProcessor(ObjectMapper mapper, RedisStreamPublisher redis,
                           MetricsCollector metrics, EsLogIndexer es) {
        this.mapper = mapper;
        this.redis = redis;
        this.metrics = metrics;
        this.es = es;
    }

    public void process(JobSubmittedEvent event, Instant startedAt, String line) {
        Optional<WorkerMetric> parsed = WorkerMetric.parse(mapper, line);
        if (parsed.isPresent()) {
            WorkerMetric m = parsed.get();
            redis.publishMetric(event, startedAt, m);
            metrics.record(event.jobId(), event.attempt(), m);
            es.add(LogDoc.metric(event.jobId(), event.attempt(), m));
        } else {
            es.add(LogDoc.message(event.jobId(), event.attempt(), line));
        }
    }
}
