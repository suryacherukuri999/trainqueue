package com.trainqueue.api.job;

import com.trainqueue.api.inbox.InboxEvent;
import com.trainqueue.api.inbox.InboxRepository;
import com.trainqueue.api.messaging.JobStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobStateMachineTest {

    private Map<UUID, Job> store;
    private Set<String> inboxSeen;
    private JobStateMachine sm;
    private UUID id;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        inboxSeen = new HashSet<>();

        JobRepository repo = mock(JobRepository.class);
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));

        InboxRepository inbox = mock(InboxRepository.class);
        when(inbox.existsById(any())).thenAnswer(inv -> inboxSeen.contains(inv.getArgument(0)));
        when(inbox.save(any())).thenAnswer(inv -> {
            InboxEvent e = inv.getArgument(0);
            inboxSeen.add(e.getId());
            return e;
        });

        sm = new JobStateMachine(repo, inbox);

        Job job = new Job(UUID.randomUUID(), "j", "worker-sim:latest", null, 5, null, 1, 1000, 1024, 3);
        id = job.getId();
        store.put(id, job);
    }

    private void send(int attempt, JobStatus status) {
        sm.apply(new JobStatusEvent(UUID.randomUUID(), id, attempt, status, Instant.now()));
    }

    @Test
    void appliesLegalForwardTransitions() {
        send(1, JobStatus.RUNNING);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        send(1, JobStatus.SUCCEEDED);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void higherAttemptResumesRunningAfterRetry() {
        send(1, JobStatus.RUNNING);
        send(2, JobStatus.RUNNING); // retry resumed
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(store.get(id).getAttempt()).isEqualTo(2);
        send(2, JobStatus.FAILED);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void terminalStatesAreAbsorbing() {
        send(1, JobStatus.RUNNING);
        send(1, JobStatus.CANCELLED);
        Instant finishedAt = store.get(id).getFinishedAt();
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.CANCELLED);

        // the original bug: RUNNING(2) must NOT revive a cancelled job
        send(2, JobStatus.RUNNING);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(store.get(id).getFinishedAt()).isEqualTo(finishedAt);

        send(2, JobStatus.SUCCEEDED);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void ignoresStaleAndOutOfOrderEvents() {
        send(1, JobStatus.RUNNING);
        send(1, JobStatus.SUCCEEDED);
        send(1, JobStatus.RUNNING); // stale, terminal already
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void ignoresDuplicateEventId() {
        UUID eventId = UUID.randomUUID();
        sm.apply(new JobStatusEvent(eventId, id, 1, JobStatus.RUNNING, Instant.now()));
        Instant startedAt = store.get(id).getStartedAt();
        // same eventId redelivered: no-op
        sm.apply(new JobStatusEvent(eventId, id, 1, JobStatus.SUCCEEDED, Instant.now()));
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(store.get(id).getStartedAt()).isEqualTo(startedAt);
    }
}
