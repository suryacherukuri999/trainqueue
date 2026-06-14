package com.trainqueue.api.artifacts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trainqueue.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
}
