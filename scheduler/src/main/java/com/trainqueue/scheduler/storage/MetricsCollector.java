package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accumulates a running attempt's per-epoch metrics so a run document can be built
 * on completion. Keyed by (jobId, attempt) so a retry never mixes with a prior try.
 */
@Component
public class MetricsCollector {

    private final Map<String, List<WorkerMetric>> byRun = new ConcurrentHashMap<>();

    public void record(UUID jobId, int attempt, WorkerMetric metric) {
        List<WorkerMetric> metrics = byRun.computeIfAbsent(key(jobId, attempt), k -> new ArrayList<>());
        synchronized (metrics) {
            metrics.add(metric);
        }
    }

    public Snapshot snapshot(UUID jobId, int attempt) {
        List<WorkerMetric> metrics = byRun.getOrDefault(key(jobId, attempt), List.of());
        synchronized (metrics) {
            List<WorkerMetric> ordered = metrics.stream()
                    .sorted((a, b) -> Integer.compare(a.epoch(), b.epoch()))
                    .toList();
            List<Double> lossCurve = ordered.stream().map(WorkerMetric::loss).toList();
            double finalAccuracy = ordered.isEmpty() ? 0.0 : ordered.get(ordered.size() - 1).accuracy();
            return new Snapshot(ordered.size(), lossCurve, finalAccuracy);
        }
    }

    public void clear(UUID jobId, int attempt) {
        byRun.remove(key(jobId, attempt));
    }

    private static String key(UUID jobId, int attempt) {
        return jobId + "-" + attempt;
    }

    public record Snapshot(int epochs, List<Double> lossCurve, double finalAccuracy) {
    }
}
