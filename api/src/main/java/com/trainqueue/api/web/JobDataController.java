package com.trainqueue.api.web;

import com.trainqueue.api.artifacts.ArtifactResponse;
import com.trainqueue.api.artifacts.ArtifactService;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.logs.LogResponse;
import com.trainqueue.api.logs.LogSearchService;
import com.trainqueue.api.metrics.MetricsResponse;
import com.trainqueue.api.metrics.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Sub-resources backed by Mongo (metrics), S3 (artifacts), and Elasticsearch (logs). */
@RestController
@RequestMapping("/api/jobs/{id}")
public class JobDataController {

    private final MetricsService metrics;
    private final ArtifactService artifacts;
    private final LogSearchService logs;

    public JobDataController(MetricsService metrics, ArtifactService artifacts, LogSearchService logs) {
        this.metrics = metrics;
        this.artifacts = artifacts;
        this.logs = logs;
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics(@PathVariable UUID id) {
        return metrics.latest(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @GetMapping("/artifacts")
    public ResponseEntity<ArtifactResponse> artifacts(@PathVariable UUID id) {
        return artifacts.presignedUrl(id)
                .map(url -> ResponseEntity.ok(new ArtifactResponse(url)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/logs")
    public List<LogResponse> logs(@PathVariable UUID id,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) Long from,
                                  @RequestParam(required = false) Long to) {
        return logs.search(id, q, from, to);
    }
}
