package com.trainqueue.api.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.api.messaging.JobStateCache;
import com.trainqueue.api.outbox.OutboxEvent;
import com.trainqueue.api.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final JobRepository jobs = mock(JobRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final JobStateCache cache = mock(JobStateCache.class);
    private final JobService service = new JobService(
            jobs,
            outbox,
            cache,
            new ObjectMapper(),
            "worker-sim:latest",
            "jobs.submitted",
            "jobs.control",
            200,
            4000,
            8192);

    @Test
    void deleteRemovesJobEvictsCacheAndRequestsStopForActiveJob() {
        Job job = sampleJob();
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        service.delete(job.getId());

        verify(outbox).save(argThat(event ->
                event.getTopic().equals("jobs.control")
                        && event.getMsgKey().equals(job.getId().toString())));
        verify(jobs).delete(job);
        verify(cache).evict(job.getId());
    }

    @Test
    void deleteTerminalJobDoesNotEmitCancelCommand() {
        Job job = sampleJob();
        job.markRunning(1);
        job.markTerminal(JobStatus.SUCCEEDED, 1);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        service.delete(job.getId());

        verify(outbox, never()).save(any(OutboxEvent.class));
        verify(jobs).delete(job);
        verify(cache).evict(job.getId());
    }

    @Test
    void deleteAllRemovesAllJobsAndEvictsCaches() {
        Job active = sampleJob();
        Job terminal = sampleJob();
        terminal.markRunning(1);
        terminal.markTerminal(JobStatus.FAILED, 1);
        when(jobs.findAll()).thenReturn(List.of(active, terminal));

        service.deleteAll();

        verify(outbox).save(argThat(event -> event.getMsgKey().equals(active.getId().toString())));
        verify(outbox, never()).save(argThat(event -> event.getMsgKey().equals(terminal.getId().toString())));
        verify(jobs).deleteAllInBatch(List.of(active, terminal));
        verify(cache).evict(active.getId());
        verify(cache).evict(terminal.getId());
    }

    private static Job sampleJob() {
        return new Job(UUID.randomUUID(), "demo", "worker-sim:latest", null,
                5, null, 1, 1000, 1024, 0);
    }
}
