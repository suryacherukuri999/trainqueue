package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accumulates a running attempt's per-epoch metrics, keyed by (jobId, attempt) then
 * epoch. Upserting by epoch makes a replay after scheduler recovery idempotent — the
 * loss curve is rebuilt, not duplicated.
 */
@Component
public class MetricsCollector {

    private final Map<String, Map<Integer, WorkerMetric>> byRun = new ConcurrentHashMap<>();

    public void record(UUID jobId, int attempt, WorkerMetric metric) {
        byRun.computeIfAbsent(key(jobId, attempt), k -> new ConcurrentHashMap<>())
                .put(metric.epoch(), metric);
    }

    public Snapshot snapshot(UUID jobId, int attempt) {
        Map<Integer, WorkerMetric> metrics = byRun.getOrDefault(key(jobId, attempt), Map.of());
        List<WorkerMetric> ordered = metrics.values().stream()
                .sorted((a, b) -> Integer.compare(a.epoch(), b.epoch()))
                .toList();
        List<Double> lossCurve = ordered.stream().map(WorkerMetric::loss).toList();
        double finalAccuracy = ordered.isEmpty() ? 0.0 : ordered.get(ordered.size() - 1).accuracy();
        return new Snapshot(ordered.size(), lossCurve, finalAccuracy);
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
