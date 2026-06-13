package com.trainqueue.scheduler.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(2000, 30000);

    @Test
    void retriesWhileAttemptBelowMaxRetries() {
        assertThat(policy.shouldRetry(1, 3)).isTrue();
        assertThat(policy.shouldRetry(2, 3)).isTrue();
        assertThat(policy.shouldRetry(3, 3)).isFalse(); // exhausted
    }

    @Test
    void neverRetriesWhenMaxRetriesIsZero() {
        assertThat(policy.shouldRetry(1, 0)).isFalse();
    }

    @Test
    void backoffGrowsExponentiallyThenCaps() {
        assertThat(policy.backoff(1).toMillis()).isEqualTo(2000);
        assertThat(policy.backoff(2).toMillis()).isEqualTo(4000);
        assertThat(policy.backoff(3).toMillis()).isEqualTo(8000);
        assertThat(policy.backoff(10).toMillis()).isEqualTo(30000); // capped at max
    }
}
