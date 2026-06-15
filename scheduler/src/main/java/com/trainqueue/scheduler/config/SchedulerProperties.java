package com.trainqueue.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trainqueue")
public record SchedulerProperties(
        Topics topics,
        Api api,
        Pool pool,
        Retry retry,
        long heartbeatMs,
        String outputDir,
        S3 s3,
        Es es
) {
    public record Topics(String submitted, String status, String control) {
    }

    public record Api(String baseUrl) {
    }

    public record Pool(int cpuMillis, int memMb) {
    }

    public record Retry(long baseBackoffMs, long maxBackoffMs) {
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    }

    public record Es(String index, int flushCount, long flushMs, String spoolFile) {
    }
}
