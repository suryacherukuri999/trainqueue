package com.trainqueue.api.metrics;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read view of the scheduler-written run metrics (MongoDB trainqueue.runs). */
@Document("runs")
public class RunDocument {

    @Id
    private String id;
    private UUID jobId;
    private int attempt;
    private long durationMs;
    private int epochs;
    private List<Double> lossCurve;
    private double finalAccuracy;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getAttempt() {
        return attempt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getEpochs() {
        return epochs;
    }

    public List<Double> getLossCurve() {
        return lossCurve;
    }

    public double getFinalAccuracy() {
        return finalAccuracy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
