package com.trainqueue.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trainqueue")
public record SchedulerProperties(
        Topics topics,
        Api api,
        Pool pool,
        Retry retry,
        long heartbeatMs
) {
    public record Topics(String submitted, String status, String control) {
    }

    public record Api(String baseUrl) {
    }

    public record Pool(int cpuMillis, int memMb) {
    }

    public record Retry(long baseBackoffMs, long maxBackoffMs) {
    }
}
