package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunDocumentMapperTest {

    private JobSubmittedEvent event(UUID id, int attempt) {
        return new JobSubmittedEvent(UUID.randomUUID(), id, "demo", "worker-sim:latest", null, 3, null,
                1, 1000, 1024, attempt, 2, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void mapsTimingMetricsAndIdentity() {
        UUID id = UUID.randomUUID();
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant finished = started.plusMillis(6500);
        var snapshot = new MetricsCollector.Snapshot(3, List.of(0.7, 0.5, 0.3), 0.91);

        RunDocument doc = RunDocumentMapper.completed(event(id, 2), started, finished, snapshot);

        assertThat(doc.getId()).isEqualTo(id + "-2");
        assertThat(doc.getJobId()).isEqualTo(id);
        assertThat(doc.getAttempt()).isEqualTo(2);
        assertThat(doc.getDurationMs()).isEqualTo(6500);
        assertThat(doc.getEpochs()).isEqualTo(3);
        assertThat(doc.getLossCurve()).containsExactly(0.7, 0.5, 0.3);
        assertThat(doc.getFinalAccuracy()).isEqualTo(0.91);
        assertThat(doc.getStartedAt()).isEqualTo(started);
        assertThat(doc.getFinishedAt()).isEqualTo(finished);
    }

    @Test
    void inProgressHasNoFinishTime() {
        UUID id = UUID.randomUUID();
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        var snapshot = new MetricsCollector.Snapshot(2, List.of(0.7, 0.5), 0.8);

        RunDocument doc = RunDocumentMapper.inProgress(event(id, 1), started, snapshot);

        assertThat(doc.getFinishedAt()).isNull();
        assertThat(doc.getEpochs()).isEqualTo(2);
    }

    @Test
    void handlesAnEmptyMetricSnapshot() {
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        var snapshot = new MetricsCollector.Snapshot(0, List.of(), 0.0);

        RunDocument doc = RunDocumentMapper.completed(event(id, 1), t, t, snapshot);

        assertThat(doc.getEpochs()).isZero();
        assertThat(doc.getLossCurve()).isEmpty();
        assertThat(doc.getDurationMs()).isZero();
    }
}
