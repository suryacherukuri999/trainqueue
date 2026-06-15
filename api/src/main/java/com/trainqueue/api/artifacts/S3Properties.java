package com.trainqueue.api.artifacts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trainqueue.s3")
public record S3Properties(
        String endpoint,
        String publicEndpoint, // host the browser can reach; used for presigned URLs
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
    public String publicEndpointOrDefault() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
