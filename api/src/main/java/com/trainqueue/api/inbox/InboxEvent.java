package com.trainqueue.api.inbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Marks a consumed event as processed so redeliveries are ignored (idempotent consumers). */
@Entity
@Table(name = "inbox_events")
public class InboxEvent {

    @Id
    private String id; // consumer + ":" + eventId
    private String consumer;
    private UUID eventId;
    private Instant processedAt;

    protected InboxEvent() {
    }

    public InboxEvent(String consumer, UUID eventId) {
        this.id = consumer + ":" + eventId;
        this.consumer = consumer;
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public static String idFor(String consumer, UUID eventId) {
        return consumer + ":" + eventId;
    }

    public String getId() {
        return id;
    }
}
