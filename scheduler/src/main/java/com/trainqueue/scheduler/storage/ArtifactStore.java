package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Bind-mounts a per-attempt host directory into the worker (at /output), then
 * uploads the resulting model artifact to S3 (LocalStack in dev).
 */
@Component
public class ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStore.class);

    private final S3Client s3;
    private final String bucket;
    private final Path outputRoot;

    public ArtifactStore(S3Client s3, SchedulerProperties props) {
        this.s3 = s3;
        this.bucket = props.s3().bucket();
        this.outputRoot = Path.of(props.outputDir());
    }

    /** Deterministic host directory mounted at the container's /output. */
    public Path outputDir(UUID jobId, int attempt) {
        return outputRoot.resolve(jobId + "-" + attempt);
    }

    public Path prepareOutputDir(UUID jobId, int attempt) throws IOException {
        Path dir = outputDir(jobId, attempt);
        Files.createDirectories(dir);
        return dir;
    }

    /** Upload {output}/model.bin to s3://{bucket}/{jobId}/model.bin; returns the key if present. */
    public Optional<String> upload(UUID jobId, int attempt) {
        Path file = outputDir(jobId, attempt).resolve("model.bin");
        if (!Files.exists(file)) {
            log.warn("no artifact at {} to upload for job {}", file, jobId);
            return Optional.empty();
        }
        ensureBucket();
        String key = jobId + "/model.bin";
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromFile(file));
        log.info("uploaded artifact for job {} to s3://{}/{}", jobId, bucket, key);
        return Optional.of(key);
    }

    public void cleanup(UUID jobId, int attempt) {
        Path dir = outputDir(jobId, attempt);
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("cleanup of {} failed: {}", dir, e.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            s3.createBucket(b -> b.bucket(bucket));
        } catch (S3Exception e) {
            // already exists (BucketAlreadyOwnedByYou) — fine
            log.debug("createBucket {}: {}", bucket, e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorCode());
        }
    }
}
