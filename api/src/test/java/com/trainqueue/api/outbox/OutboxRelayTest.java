package com.trainqueue.api.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fault-injection: a Kafka outage must leave the outbox row pending, then publish on recovery. */
class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRepository repo = mock(OutboxRepository.class);
    private final OutboxRelay relay = new OutboxRelay(repo, kafka);

    private OutboxEvent pending() {
        return new OutboxEvent(UUID.randomUUID(), "jobs.submitted", "key", "{}");
    }

    @Test
    void leavesRowPendingWhenKafkaIsDown() {
        OutboxEvent event = pending();
        when(repo.lockUnpublishedBatch(any())).thenReturn(List.of(event));
        when(kafka.send(eq("jobs.submitted"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull(); // not marked published -> retried next poll
    }

    @Test
    void marksPublishedWhenKafkaRecovers() {
        OutboxEvent event = pending();
        when(repo.lockUnpublishedBatch(any())).thenReturn(List.of(event));
        when(kafka.send(eq("jobs.submitted"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
    }
}
