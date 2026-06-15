package com.trainqueue.scheduler.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Short Mongo timeouts. The outbox/inbox sit on the job-completion path, so if Mongo is
 * unreachable we want calls to fail in seconds (and recover when it returns) rather than
 * block worker threads on the driver's 30s default. This is degrade-gracefully, not
 * fault tolerance: while Mongo is down, status events and run metrics don't get recorded.
 */
@Configuration
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return builder -> builder
                .applyToClusterSettings(s -> s.serverSelectionTimeout(2, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s.connectTimeout(2, TimeUnit.SECONDS));
    }
}
