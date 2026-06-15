package com.trainqueue.api.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A domain event persisted in the job transaction and relayed to Kafka exactly once. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;
    private String topic;
    private String msgKey;
    @Column(columnDefinition = "text")
    private String payload;
    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String topic, String msgKey, String payload) {
        this.id = id;
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
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
