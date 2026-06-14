package com.trainqueue.api.job.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trainqueue.api.job.Job;
import com.trainqueue.api.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

// The cached Redis snapshot carries extra live fields (epoch/loss/accuracy); ignore them here.
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobResponse(
        UUID id,
        String name,
        String dockerImage,
        String command,
        int epochs,
        Integer failAtEpoch,
        int priority,
        int cpuMillis,
        int memMb,
        JobStatus status,
        int attempt,
        int maxRetries,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getDockerImage(),
                job.getCommand(),
                job.getEpochs(),
                job.getFailAtEpoch(),
                job.getPriority(),
                job.getCpuMillis(),
                job.getMemMb(),
                job.getStatus(),
                job.getAttempt(),
                job.getMaxRetries(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
