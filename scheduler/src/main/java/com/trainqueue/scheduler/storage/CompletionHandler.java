package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists artifacts and run metrics when an attempt ends. All best-effort: a
 * storage hiccup must not break job completion or status reporting.
 */
@Component
public class CompletionHandler {

    private static final Logger log = LoggerFactory.getLogger(CompletionHandler.class);

    private final ArtifactStore artifacts;
    private final RunRepository runs;
    private final MetricsCollector metrics;
    private final EsLogIndexer es;

    public CompletionHandler(ArtifactStore artifacts, RunRepository runs,
                             MetricsCollector metrics, EsLogIndexer es) {
        this.artifacts = artifacts;
        this.runs = runs;
        this.metrics = metrics;
        this.es = es;
    }

    public void onSuccess(JobSubmittedEvent event, Instant startedAt, Instant finishedAt) {
        try {
            artifacts.upload(event.jobId(), event.attempt());
        } catch (Exception e) {
            log.warn("artifact upload failed for {}: {}", event.jobId(), e.getMessage());
        }
        try {
            MetricsCollector.Snapshot snapshot = metrics.snapshot(event.jobId(), event.attempt());
            runs.save(RunDocumentMapper.map(event, startedAt, finishedAt, snapshot));
        } catch (Exception e) {
            log.warn("run-doc save failed for {}: {}", event.jobId(), e.getMessage());
        }
        es.flush();
        finish(event);
    }

    public void onTerminal(JobSubmittedEvent event) {
        es.flush();
        finish(event);
    }

    private void finish(JobSubmittedEvent event) {
        metrics.clear(event.jobId(), event.attempt());
        artifacts.cleanup(event.jobId(), event.attempt());
    }
}
