package com.trainqueue.scheduler.core;

import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.PriorityQueue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementOrderingTest {

    private static JobSubmittedEvent job(String name, int priority, Instant createdAt) {
        return new JobSubmittedEvent(UUID.randomUUID(), name, "worker-sim:latest", null,
                5, null, priority, 1000, 1024, 1, 0, createdAt);
    }

    @Test
    void higherPriorityComesFirstThenOlderFirst() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        PriorityQueue<JobSubmittedEvent> queue = new PriorityQueue<>(Scheduler.ORDER);

        queue.add(job("low-new", 1, t0.plusSeconds(10)));
        queue.add(job("high-new", 5, t0.plusSeconds(20)));
        queue.add(job("high-old", 5, t0.plusSeconds(5)));
        queue.add(job("low-old", 1, t0));

        // highest priority first; within a priority, oldest createdAt first
        assertThat(queue.poll().name()).isEqualTo("high-old");
        assertThat(queue.poll().name()).isEqualTo("high-new");
        assertThat(queue.poll().name()).isEqualTo("low-old");
        assertThat(queue.poll().name()).isEqualTo("low-new");
    }
}
