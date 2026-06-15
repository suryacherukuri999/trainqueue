package com.trainqueue.scheduler.core;

import java.time.Duration;

/** Exponential backoff between attempts; retry while attempt &lt; maxRetries. */
public class RetryPolicy {

    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public RetryPolicy(long baseBackoffMs, long maxBackoffMs) {
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    // One-based attempts: maxRetries=N means up to N retries after the first try.
    public boolean shouldRetry(int attempt, int maxRetries) {
        return attempt <= maxRetries;
    }

    public Duration backoff(int attempt) {
        int exponent = Math.max(0, attempt - 1);
        long scaled = baseBackoffMs << Math.min(exponent, 16); // cap the shift to avoid overflow
        return Duration.ofMillis(Math.min(scaled, maxBackoffMs));
    }
}
