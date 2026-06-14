package com.trainqueue.api.metrics;

import java.util.List;
import java.util.UUID;

public record MetricsResponse(
        UUID jobId,
        int attempt,
        long durationMs,
        int epochs,
        double finalAccuracy,
        List<Double> lossCurve
) {
    public static MetricsResponse from(RunDocument doc) {
        return new MetricsResponse(doc.getJobId(), doc.getAttempt(), doc.getDurationMs(),
                doc.getEpochs(), doc.getFinalAccuracy(), doc.getLossCurve());
    }
}
