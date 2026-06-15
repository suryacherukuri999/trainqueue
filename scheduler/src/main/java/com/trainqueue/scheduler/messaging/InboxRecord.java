package com.trainqueue.scheduler.messaging;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Records a consumed event so redeliveries are skipped (idempotent consumers). */
@Document("inbox")
public class InboxRecord {

    @Id
    private String id; // consumer + ":" + eventId
    private String consumer;
    private UUID eventId;
    private Instant processedAt;

    protected InboxRecord() {
    }

    public InboxRecord(String consumer, UUID eventId) {
        this.id = consumer + ":" + eventId;
        this.consumer = consumer;
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
