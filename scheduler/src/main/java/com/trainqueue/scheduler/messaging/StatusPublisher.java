package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.config.SchedulerProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Records status events and (delayed) re-submissions in the durable scheduler outbox.
 * The OutboxRelay publishes them to Kafka, so neither a broker outage nor a restart
 * during a retry backoff loses them.
 */
@Component
public class StatusPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final SchedulerProperties props;

    public StatusPublisher(OutboxRepository outbox, ObjectMapper mapper, SchedulerProperties props) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.props = props;
    }

    public void publishStatus(JobStatusEvent event) {
        outbox.save(new OutboxMessage(event.eventId(), props.topics().status(),
                event.jobId().toString(), toJson(event), Instant.now()));
    }

    /** Re-submit a job for another attempt, not before {@code dueAt} (restart-safe backoff). */
    public void scheduleResubmit(JobSubmittedEvent event, Instant dueAt) {
        outbox.save(new OutboxMessage(event.eventId(), props.topics().submitted(),
                event.jobId().toString(), toJson(event), dueAt));
    }

    /**
     * True if a retry for this job is scheduled but not yet fired. The startup reconciler
     * uses this so it won't also re-queue a job that the durable backoff already owns.
     */
    public boolean hasPendingResubmit(UUID jobId) {
        return outbox.existsByMsgKeyAndTopicAndPublishedAtIsNull(jobId.toString(), props.topics().submitted());
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event", e);
        }
    }
}
