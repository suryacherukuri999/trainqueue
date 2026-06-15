package com.trainqueue.scheduler.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publishes due scheduler-outbox messages to Kafka; survives broker outages and restarts. */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${trainqueue.outbox-poll-ms:1000}")
    public void publishDue() {
        List<OutboxMessage> due = outbox.findTop200ByPublishedAtIsNullAndDueAtLessThanEqualOrderByDueAtAsc(Instant.now());
        for (OutboxMessage msg : due) {
            try {
                kafka.send(msg.getTopic(), msg.getMsgKey(), msg.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                msg.markPublished();
                outbox.save(msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // broker down: stop this tick, leave the rest pending for the next poll
                log.warn("scheduler outbox publish stalled at {}: {}", msg.getId(), e.getMessage());
                return;
            }
        }
    }
}
