package com.trainqueue.scheduler.messaging;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Idempotency guard for Kafka consumers, backed by the Mongo inbox collection. */
@Component
public class ConsumerInbox {

    private final InboxRepository repo;

    public ConsumerInbox(InboxRepository repo) {
        this.repo = repo;
    }

    public boolean alreadyProcessed(String consumer, UUID eventId) {
        return repo.existsById(consumer + ":" + eventId);
    }

    /** Record after the action succeeds (and before acking Kafka), so we never mark
     *  an event done without having done it. */
    public void markProcessed(String consumer, UUID eventId) {
        try {
            repo.insert(new InboxRecord(consumer, eventId));
        } catch (DuplicateKeyException ignored) {
            // already recorded by a concurrent/redelivered copy
        }
    }
}
