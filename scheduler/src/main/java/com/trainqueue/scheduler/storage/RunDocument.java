package com.trainqueue.scheduler.storage;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Per-attempt run metrics persisted to MongoDB (trainqueue.runs), upserted by id. */
@Document("runs")
public class RunDocument {

    @Id
    private String id; // jobId-attempt
    private UUID jobId;
    private int attempt;
    private long durationMs;
    private int epochs;
    private List<Double> lossCurve;
    private double finalAccuracy;
    private Instant startedAt;
    private Instant finishedAt;

    public RunDocument() {
    }

    public RunDocument(String id, UUID jobId, int attempt, long durationMs, int epochs,
                       List<Double> lossCurve, double finalAccuracy, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.jobId = jobId;
        this.attempt = attempt;
        this.durationMs = durationMs;
        this.epochs = epochs;
        this.lossCurve = lossCurve;
        this.finalAccuracy = finalAccuracy;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

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

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
