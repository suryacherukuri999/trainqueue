package com.trainqueue.scheduler.messaging;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InboxRepository extends MongoRepository<InboxRecord, String> {
}
