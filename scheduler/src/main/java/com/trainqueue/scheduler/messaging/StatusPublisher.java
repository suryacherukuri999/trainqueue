package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.config.SchedulerProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final SchedulerProperties props;

    public StatusPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, SchedulerProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.props = props;
    }

    public void publishStatus(JobStatusEvent event) {
        kafka.send(props.topics().status(), event.jobId().toString(), toJson(event));
    }

    /** Re-submit a job for another attempt; the scheduler's own consumer picks it back up. */
    public void republishSubmitted(JobSubmittedEvent event) {
        kafka.send(props.topics().submitted(), event.jobId().toString(), toJson(event));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event", e);
        }
    }
}
