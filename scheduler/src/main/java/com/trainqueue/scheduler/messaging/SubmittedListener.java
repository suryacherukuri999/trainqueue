package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.core.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SubmittedListener {

    private static final Logger log = LoggerFactory.getLogger(SubmittedListener.class);

    private final ObjectMapper mapper;
    private final Scheduler scheduler;

    public SubmittedListener(ObjectMapper mapper, Scheduler scheduler) {
        this.mapper = mapper;
        this.scheduler = scheduler;
    }

    @KafkaListener(topics = "${trainqueue.topics.submitted}", groupId = "scheduler")
    public void onSubmitted(String payload) {
        try {
            scheduler.submit(mapper.readValue(payload, JobSubmittedEvent.class));
        } catch (Exception e) {
            log.error("failed to handle submitted event: {}", payload, e);
        }
    }
}
