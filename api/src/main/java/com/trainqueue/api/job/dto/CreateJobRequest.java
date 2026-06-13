package com.trainqueue.api.job.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateJobRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer epochs,
        @Min(0) Integer priority,
        @Min(1) Integer failAtEpoch,
        String dockerImage,
        @Min(1) Integer cpuMillis,
        @Min(1) Integer memMb,
        @Min(0) Integer maxRetries
) {
}
