package com.trainqueue.api.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.api.job.JobStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JobStatusListener {

    private static final Logger log = LoggerFactory.getLogger(JobStatusListener.class);

    private final ObjectMapper mapper;
    private final JobStateMachine stateMachine;

    public JobStatusListener(ObjectMapper mapper, JobStateMachine stateMachine) {
        this.mapper = mapper;
        this.stateMachine = stateMachine;
    }

    @KafkaListener(topics = "${trainqueue.topics.status}", groupId = "api")
    public void onStatus(String payload) {
        JobStatusEvent event;
        try {
            event = mapper.readValue(payload, JobStatusEvent.class);
        } catch (Exception e) {
            // Poison payload: skip so it doesn't stall the partition.
            log.error("skipping unparseable status event: {}", payload, e);
            return;
        }
        // Let transient failures propagate so the container retries (offset not committed).
        stateMachine.apply(event);
    }
}
