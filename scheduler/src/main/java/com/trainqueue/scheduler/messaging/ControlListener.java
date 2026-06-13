package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.core.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ControlListener {

    private static final Logger log = LoggerFactory.getLogger(ControlListener.class);

    private final ObjectMapper mapper;
    private final Scheduler scheduler;

    public ControlListener(ObjectMapper mapper, Scheduler scheduler) {
        this.mapper = mapper;
        this.scheduler = scheduler;
    }

    @KafkaListener(topics = "${trainqueue.topics.control}", groupId = "scheduler")
    public void onControl(String payload) {
        try {
            CancelCommand command = mapper.readValue(payload, CancelCommand.class);
            scheduler.cancel(command.jobId());
        } catch (Exception e) {
            log.error("failed to handle control command: {}", payload, e);
        }
    }
}
