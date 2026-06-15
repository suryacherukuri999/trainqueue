package com.trainqueue.scheduler.recovery;

import com.trainqueue.scheduler.core.JobLauncher;
import com.trainqueue.scheduler.core.Scheduler;
import com.trainqueue.scheduler.messaging.JobSubmittedEvent;
import com.trainqueue.scheduler.messaging.StatusPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconcilerTest {

    private final ApiClient api = mock(ApiClient.class);
    private final JobLauncher launcher = mock(JobLauncher.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final StatusPublisher publisher = mock(StatusPublisher.class);
    private final Reconciler reconciler = new Reconciler(api, launcher, scheduler, publisher);

    private static ApiClient.JobInfo runningJob(UUID id) {
        return new ApiClient.JobInfo(id, "job", "worker-sim:latest", null, 5, null,
                1, 1000, 1024, 1, 2, "RUNNING", Instant.now(), Instant.now());
    }

    @Test
    void reQueuesAnActiveJobWithNoWorker() {
        UUID id = UUID.randomUUID();
        when(api.allJobs()).thenReturn(List.of(runningJob(id)));
        when(launcher.listManaged()).thenReturn(List.of());
        when(publisher.hasPendingResubmit(id)).thenReturn(false);

        reconciler.reconcile();

        verify(scheduler, times(1)).submit(any(JobSubmittedEvent.class));
    }

    @Test
    void doesNotReQueueWhenARetryIsAlreadyScheduled() {
        UUID id = UUID.randomUUID();
        when(api.allJobs()).thenReturn(List.of(runningJob(id)));
        when(launcher.listManaged()).thenReturn(List.of());
        when(publisher.hasPendingResubmit(id)).thenReturn(true); // backoff in flight

        reconciler.reconcile();

        // the durable retry owns the resume; re-queueing here would double-run the job
        verify(scheduler, never()).submit(any(JobSubmittedEvent.class));
    }
}
