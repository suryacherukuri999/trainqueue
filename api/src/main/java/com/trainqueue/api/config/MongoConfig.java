package com.trainqueue.api.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Short Mongo timeouts so a Mongo outage fails the metrics read in seconds instead of
 * hanging on the driver's 30s default. Metrics are best-effort; the job table itself
 * lives in Postgres and is unaffected.
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
