package com.trainqueue.scheduler.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(2000, 30000);

    @Test
    void retriesUpToAndIncludingMaxRetries() {
        // maxRetries=2 -> attempts 1,2,3 (one initial + two retries)
        assertThat(policy.shouldRetry(1, 2)).isTrue();
        assertThat(policy.shouldRetry(2, 2)).isTrue();
        assertThat(policy.shouldRetry(3, 2)).isFalse(); // exhausted
    }

    @Test
    void maxRetriesOneGivesExactlyOneRetry() {
        assertThat(policy.shouldRetry(1, 1)).isTrue();  // the bug: was false
        assertThat(policy.shouldRetry(2, 1)).isFalse();
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
