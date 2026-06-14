package com.trainqueue.scheduler.storage;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RunRepository extends MongoRepository<RunDocument, String> {
}
