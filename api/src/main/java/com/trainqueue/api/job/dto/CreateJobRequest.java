package com.trainqueue.api.job.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Job submission. Note: there is no dockerImage field — the worker image is
 * server-selected, so a client can never choose what runs (P0-6).
 */
public record CreateJobRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer epochs,
        @Min(0) Integer priority,
        @Min(1) Integer failAtEpoch,
        @Min(1) Integer cpuMillis,
        @Min(1) Integer memMb,
        @Min(0) Integer maxRetries
) {
    @AssertTrue(message = "failAtEpoch must not exceed epochs")
    public boolean isFailAtEpochWithinEpochs() {
        return failAtEpoch == null || epochs == null || failAtEpoch <= epochs;
    }
}
