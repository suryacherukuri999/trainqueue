package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

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
    private final JobLogProcessor logProcessor;

    public CompletionHandler(ArtifactStore artifacts, RunRepository runs, MetricsCollector metrics,
                             EsLogIndexer es, JobLogProcessor logProcessor) {
        this.artifacts = artifacts;
        this.runs = runs;
        this.metrics = metrics;
        this.es = es;
        this.logProcessor = logProcessor;
    }

    public void onSuccess(JobSubmittedEvent event, Instant startedAt, Instant finishedAt,
                          Optional<byte[]> artifact) {
        try {
            if (artifact.isPresent()) {
                artifacts.uploadBytes(event.jobId(), artifact.get()); // k8s: copied from the pod
            } else {
                artifacts.upload(event.jobId(), event.attempt());      // docker: host bind mount
            }
        } catch (Exception e) {
            log.warn("artifact upload failed for {}: {}", event.jobId(), e.getMessage());
        }
        try {
            MetricsCollector.Snapshot snapshot = metrics.snapshot(event.jobId(), event.attempt());
            runs.save(RunDocumentMapper.completed(event, startedAt, finishedAt, snapshot));
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
        logProcessor.clear(event);
        artifacts.cleanup(event.jobId(), event.attempt());
    }
}
