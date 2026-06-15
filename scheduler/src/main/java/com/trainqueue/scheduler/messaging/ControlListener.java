package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.core.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ControlListener {

    private static final Logger log = LoggerFactory.getLogger(ControlListener.class);
    private static final String CONSUMER = "scheduler-control";

    private final ObjectMapper mapper;
    private final Scheduler scheduler;
    private final ConsumerInbox inbox;

    public ControlListener(ObjectMapper mapper, Scheduler scheduler, ConsumerInbox inbox) {
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.inbox = inbox;
    }

    @KafkaListener(topics = "${trainqueue.topics.control}", groupId = "scheduler")
    public void onControl(String payload, Acknowledgment ack) {
        CancelCommand command;
        try {
            command = mapper.readValue(payload, CancelCommand.class);
        } catch (Exception e) {
            log.error("skipping unparseable control command: {}", payload, e);
            ack.acknowledge();
            return;
        }
        if (inbox.alreadyProcessed(CONSUMER, command.eventId())) {
            log.debug("duplicate control command {} ignored", command.eventId());
            ack.acknowledge();
            return;
        }
        scheduler.cancel(command.jobId());
        inbox.markProcessed(CONSUMER, command.eventId());
        ack.acknowledge();
    }
}
