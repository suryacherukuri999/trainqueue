package com.trainqueue.scheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declared in both services so the topics exist whichever process starts first. */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic submittedTopic(SchedulerProperties props) {
        return TopicBuilder.name(props.topics().submitted()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic statusTopic(SchedulerProperties props) {
        return TopicBuilder.name(props.topics().status()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic controlTopic(SchedulerProperties props) {
        return TopicBuilder.name(props.topics().control()).partitions(1).replicas(1).build();
    }
}
