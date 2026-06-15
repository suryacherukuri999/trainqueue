package com.trainqueue.scheduler.messaging;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable scheduler-side outbox (MongoDB). Status events and delayed re-submissions
 * are recorded here and relayed to Kafka, so a broker outage or a restart during a
 * retry backoff can't lose them. {@code dueAt} lets a retry survive a restart.
 */
@Document("scheduler_outbox")
public class OutboxMessage {

    @Id
    private String id; // eventId
    private String topic;
    private String msgKey;
    private String payload;
    private Instant dueAt;
    private Instant publishedAt;

    public OutboxMessage() {
    }

    public OutboxMessage(UUID eventId, String topic, String msgKey, String payload, Instant dueAt) {
        this.id = eventId.toString();
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.dueAt = dueAt;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
