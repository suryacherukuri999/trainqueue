package com.trainqueue.api.metrics;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface RunRepository extends MongoRepository<RunDocument, String> {
    List<RunDocument> findByJobIdOrderByAttemptDesc(UUID jobId);
}
