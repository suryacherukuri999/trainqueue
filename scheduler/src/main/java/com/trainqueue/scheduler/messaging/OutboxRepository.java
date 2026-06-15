package com.trainqueue.scheduler.messaging;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends MongoRepository<OutboxMessage, String> {

    List<OutboxMessage> findTop200ByPublishedAtIsNullAndDueAtLessThanEqualOrderByDueAtAsc(Instant now);
}
