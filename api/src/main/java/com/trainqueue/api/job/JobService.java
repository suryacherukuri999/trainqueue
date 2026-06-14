package com.trainqueue.api.job;

import com.trainqueue.api.exception.InvalidTransitionException;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.job.dto.CreateJobRequest;
import com.trainqueue.api.job.dto.JobResponse;
import com.trainqueue.api.messaging.CancelCommand;
import com.trainqueue.api.messaging.JobEventPublisher;
import com.trainqueue.api.messaging.JobStateCache;
import com.trainqueue.api.messaging.JobSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final JobEventPublisher publisher;
    private final JobStateCache cache;
    private final String defaultImage;

    public JobService(JobRepository jobs, JobEventPublisher publisher, JobStateCache cache,
                      @Value("${trainqueue.default-image:worker-sim:latest}") String defaultImage) {
        this.jobs = jobs;
        this.publisher = publisher;
        this.cache = cache;
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

    /** Cache-aside read: serve the live Redis snapshot when present, else fall back to Postgres. */
    public JobResponse find(UUID id) {
        Optional<JobResponse> cached = cache.read(id);
        if (cached.isPresent()) {
            log.info("cache hit for job {}", id);
            return cached.get();
        }
        return JobResponse.from(get(id));
    }

    @Transactional
    public Job cancel(UUID id) {
        Job job = jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (!job.getStatus().canTransitionTo(JobStatus.CANCELLED)) {
            throw new InvalidTransitionException(job.getStatus(), JobStatus.CANCELLED);
        }
        job.cancel();
        // Drop the stale cached snapshot so the next read reflects CANCELLED from Postgres.
        cache.evict(id);
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
