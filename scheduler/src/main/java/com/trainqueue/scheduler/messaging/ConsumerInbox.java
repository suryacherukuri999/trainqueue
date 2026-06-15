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

    /** True if this (consumer, eventId) was not seen before (and is now recorded). */
    public boolean firstSeen(String consumer, UUID eventId) {
        String id = consumer + ":" + eventId;
        if (repo.existsById(id)) {
            return false;
        }
        try {
            repo.insert(new InboxRecord(consumer, eventId));
            return true;
        } catch (DuplicateKeyException e) {
            return false; // lost a race; another delivery already recorded it
        }
    }
}
