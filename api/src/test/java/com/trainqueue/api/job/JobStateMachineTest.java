package com.trainqueue.api.job;

import com.trainqueue.api.messaging.JobStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobStateMachineTest {

    private Map<UUID, Job> store;
    private JobRepository repo;
    private JobStateMachine sm;
    private UUID id;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        repo = mock(JobRepository.class);
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        sm = new JobStateMachine(repo);

        Job job = new Job(UUID.randomUUID(), "j", "worker-sim:latest", null, 5, null, 1, 1000, 1024, 3);
        id = job.getId();
        store.put(id, job);
    }

    private void send(int attempt, JobStatus status) {
        sm.apply(new JobStatusEvent(id, attempt, status, Instant.now()));
    }

    @Test
    void appliesLegalForwardTransitions() {
        send(1, JobStatus.RUNNING);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        send(1, JobStatus.SUCCEEDED);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void ignoresDuplicateRunning() {
        send(1, JobStatus.RUNNING);
        Instant firstStart = store.get(id).getStartedAt();
        send(1, JobStatus.RUNNING);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(store.get(id).getStartedAt()).isEqualTo(firstStart);
    }

    @Test
    void ignoresIllegalAndOutOfOrderEvents() {
        // terminal-before-running is out of order for the same attempt: SUCCEEDED outranks RUNNING
        send(1, JobStatus.RUNNING);
        send(1, JobStatus.SUCCEEDED);
        send(1, JobStatus.RUNNING); // stale, must not revert
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        send(1, JobStatus.FAILED);  // already terminal at this attempt
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void higherAttemptResumesRunningAfterRetry() {
        send(1, JobStatus.RUNNING);
        // attempt 1 failed silently and was retried; attempt 2 starts
        send(2, JobStatus.RUNNING);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(store.get(id).getAttempt()).isEqualTo(2);
        send(2, JobStatus.FAILED);
        assertThat(store.get(id).getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(store.get(id).getAttempt()).isEqualTo(2);
    }
}
