package com.trainqueue.api.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic submittedTopic(@org.springframework.beans.factory.annotation.Value("${trainqueue.topics.submitted}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic statusTopic(@org.springframework.beans.factory.annotation.Value("${trainqueue.topics.status}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic controlTopic(@org.springframework.beans.factory.annotation.Value("${trainqueue.topics.control}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
