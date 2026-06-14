package com.trainqueue.api.artifacts;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class ArtifactService {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public ArtifactService(S3Client s3, S3Presigner presigner, S3Properties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.bucket();
    }

    /** Presigned GET URL for a job's model artifact, if it exists in S3. */
    public Optional<String> presignedUrl(UUID jobId) {
        String key = jobId + "/model.bin";
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(TTL)
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build();
        return Optional.of(presigner.presignGetObject(request).url().toString());
    }
}
