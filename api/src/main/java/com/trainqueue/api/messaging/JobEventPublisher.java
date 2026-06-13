package com.trainqueue.api.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String submittedTopic;
    private final String controlTopic;

    public JobEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                             @Value("${trainqueue.topics.submitted}") String submittedTopic,
                             @Value("${trainqueue.topics.control}") String controlTopic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.submittedTopic = submittedTopic;
        this.controlTopic = controlTopic;
    }

    public void publishSubmitted(JobSubmittedEvent event) {
        kafka.send(submittedTopic, event.jobId().toString(), toJson(event));
    }

    public void publishCancel(CancelCommand command) {
        kafka.send(controlTopic, command.jobId().toString(), toJson(command));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event", e);
        }
    }
}
