package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Finalizes an attempt when it ends (success, failure, or cancel): on success uploads
 * the artifact, writes a terminal run document (with finishedAt), drains the log
 * buffer, and clears in-memory state. All best-effort so a storage hiccup never breaks
 * job completion or status reporting.
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

    /** Called once per terminal attempt (success/failure/cancel). */
    public void recordTerminal(JobSubmittedEvent event, Instant startedAt, Instant finishedAt,
                               boolean succeeded) {
        es.flush(); // drain buffered log lines before snapshotting metrics
        if (succeeded) {
            // docker: harvest model.bin from the bind mount. k8s: the worker already
            // uploaded its own artifact, so the local dir is empty and this no-ops.
            try {
                artifacts.upload(event.jobId(), event.attempt());
            } catch (Exception e) {
                log.warn("artifact upload failed for {}: {}", event.jobId(), e.getMessage());
            }
        }
        try {
            MetricsCollector.Snapshot snapshot = metrics.snapshot(event.jobId(), event.attempt());
            runs.save(RunDocumentMapper.completed(event, startedAt, finishedAt, snapshot));
        } catch (Exception e) {
            log.warn("run-doc save failed for {}: {}", event.jobId(), e.getMessage());
        }
        metrics.clear(event.jobId(), event.attempt());
        logProcessor.clear(event);
        artifacts.cleanup(event.jobId(), event.attempt());
    }
}
