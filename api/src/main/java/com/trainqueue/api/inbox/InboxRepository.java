package com.trainqueue.api.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<InboxEvent, String> {
}
