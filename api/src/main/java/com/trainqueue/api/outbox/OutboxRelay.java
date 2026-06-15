package com.trainqueue.api.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Polls the outbox and publishes pending events to Kafka exactly once. */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${trainqueue.outbox.poll-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockUnpublishedBatch(PageRequest.of(0, BATCH));
        for (OutboxEvent event : batch) {
            try {
                // Synchronous send so a Kafka outage leaves the row pending (retried next poll)
                // rather than marking it published. The eventId is the Kafka dedup anchor.
                kafka.send(event.getTopic(), event.getMsgKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Broker down/slow: stop this tick, keep the lock-released rows pending.
                log.warn("outbox publish stalled at {}: {}", event.getId(), e.getMessage());
                return;
            }
        }
        if (!batch.isEmpty()) {
            log.debug("relayed {} outbox event(s)", batch.size());
        }
    }
}
