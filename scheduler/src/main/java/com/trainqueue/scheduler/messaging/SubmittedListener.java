package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.core.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SubmittedListener {

    private static final Logger log = LoggerFactory.getLogger(SubmittedListener.class);
    private static final String CONSUMER = "scheduler-submitted";

    private final ObjectMapper mapper;
    private final Scheduler scheduler;
    private final ConsumerInbox inbox;

    public SubmittedListener(ObjectMapper mapper, Scheduler scheduler, ConsumerInbox inbox) {
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.inbox = inbox;
    }

    @KafkaListener(topics = "${trainqueue.topics.submitted}", groupId = "scheduler")
    public void onSubmitted(String payload, Acknowledgment ack) {
        JobSubmittedEvent event;
        try {
            event = mapper.readValue(payload, JobSubmittedEvent.class);
        } catch (Exception e) {
            log.error("skipping unparseable submitted event: {}", payload, e);
            ack.acknowledge();
            return;
        }
        if (inbox.alreadyProcessed(CONSUMER, event.eventId())) {
            log.debug("duplicate submitted event {} ignored", event.eventId());
            ack.acknowledge();
            return;
        }
        scheduler.submit(event);                       // 1. idempotent action
        inbox.markProcessed(CONSUMER, event.eventId()); // 2. record the marker
        ack.acknowledge();                              // 3. only now commit the offset
    }
}
