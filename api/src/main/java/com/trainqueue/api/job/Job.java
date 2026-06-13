package com.trainqueue.api.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    private String name;
    private String dockerImage;
    private String command;
    private int epochs;
    private Integer failAtEpoch;
    private int priority;
    private int cpuMillis;
    private int memMb;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private int attempt;
    private int maxRetries;

    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    protected Job() {
    }

    public Job(UUID id, String name, String dockerImage, String command, int epochs,
               Integer failAtEpoch, int priority, int cpuMillis, int memMb, int maxRetries) {
        this.id = id;
        this.name = name;
        this.dockerImage = dockerImage;
        this.command = command;
        this.epochs = epochs;
        this.failAtEpoch = failAtEpoch;
        this.priority = priority;
        this.cpuMillis = cpuMillis;
        this.memMb = memMb;
        this.maxRetries = maxRetries;
        this.status = JobStatus.QUEUED;
        this.attempt = 1;
        this.createdAt = Instant.now();
    }

    public void markRunning(int attempt) {
        this.status = JobStatus.RUNNING;
        this.attempt = attempt;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public void markTerminal(JobStatus terminal, int attempt) {
        this.status = terminal;
        this.attempt = attempt;
        this.finishedAt = Instant.now();
    }

    /** Operator-initiated cancel from the API; the scheduler stops the container out of band. */
    public void cancel() {
        this.status = JobStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public String getCommand() {
        return command;
    }

    public int getEpochs() {
        return epochs;
    }

    public Integer getFailAtEpoch() {
        return failAtEpoch;
    }

    public int getPriority() {
        return priority;
    }

    public int getCpuMillis() {
        return cpuMillis;
    }

    public int getMemMb() {
        return memMb;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
