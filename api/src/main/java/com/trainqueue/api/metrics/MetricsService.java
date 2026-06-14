package com.trainqueue.api.metrics;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MetricsService {

    private final RunRepository runs;

    public MetricsService(RunRepository runs) {
        this.runs = runs;
    }

    /** Latest attempt's run metrics for a job, if any have been recorded. */
    public Optional<MetricsResponse> latest(UUID jobId) {
        List<RunDocument> docs = runs.findByJobIdOrderByAttemptDesc(jobId);
        return docs.isEmpty() ? Optional.empty() : Optional.of(MetricsResponse.from(docs.get(0)));
    }
}
