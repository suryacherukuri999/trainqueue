package com.trainqueue.api.job;

import com.trainqueue.api.exception.InvalidTransitionException;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.job.dto.CreateJobRequest;
import com.trainqueue.api.messaging.CancelCommand;
import com.trainqueue.api.messaging.JobEventPublisher;
import com.trainqueue.api.messaging.JobSubmittedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobs;
    private final JobEventPublisher publisher;
    private final String defaultImage;

    public JobService(JobRepository jobs, JobEventPublisher publisher,
                      @Value("${trainqueue.default-image:worker-sim:latest}") String defaultImage) {
        this.jobs = jobs;
        this.publisher = publisher;
        this.defaultImage = defaultImage;
    }

    @Transactional
    public Job create(CreateJobRequest req) {
        String image = req.dockerImage() == null ? defaultImage : req.dockerImage();
        Job job = new Job(
                UUID.randomUUID(),
                req.name(),
                image,
                null,
                req.epochs(),
                req.failAtEpoch(),
                req.priority() == null ? 1 : req.priority(),
                req.cpuMillis() == null ? 1000 : req.cpuMillis(),
                req.memMb() == null ? 1024 : req.memMb(),
                req.maxRetries() == null ? 0 : req.maxRetries());
        jobs.save(job);

        // Publish only after commit, so the scheduler never consumes a submission
        // for a row that isn't yet visible (and a rolled-back submit emits nothing).
        JobSubmittedEvent event = new JobSubmittedEvent(
                job.getId(), job.getName(), job.getDockerImage(), job.getCommand(),
                job.getEpochs(), job.getFailAtEpoch(), job.getPriority(),
                job.getCpuMillis(), job.getMemMb(), job.getAttempt(), job.getMaxRetries(),
                job.getCreatedAt());
        afterCommit(() -> publisher.publishSubmitted(event));
        return job;
    }

    @Transactional(readOnly = true)
    public List<Job> list(Optional<JobStatus> status) {
        return status
                .map(jobs::findByStatusOrderByCreatedAtDesc)
                .orElseGet(jobs::findAllByOrderByCreatedAtDesc);
    }

    @Transactional(readOnly = true)
    public Job get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional
    public Job cancel(UUID id) {
        Job job = jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (!job.getStatus().canTransitionTo(JobStatus.CANCELLED)) {
            throw new InvalidTransitionException(job.getStatus(), JobStatus.CANCELLED);
        }
        job.cancel();
        afterCommit(() -> publisher.publishCancel(new CancelCommand(id)));
        return job;
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
