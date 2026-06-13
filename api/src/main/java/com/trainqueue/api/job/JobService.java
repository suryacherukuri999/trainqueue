package com.trainqueue.api.job;

import com.trainqueue.api.exception.InvalidTransitionException;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.job.dto.CreateJobRequest;
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
    private final LocalDockerLauncher launcher;
    private final String defaultImage;

    public JobService(JobRepository jobs, LocalDockerLauncher launcher,
                      @Value("${trainqueue.worker-image}") String defaultImage) {
        this.jobs = jobs;
        this.launcher = launcher;
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

        // Launch only after this transaction commits. The launcher runs on a
        // background thread; if it starts before the QUEUED row is visible, its
        // RUNNING update reads stale state and is silently dropped, stranding the
        // job in QUEUED.
        LocalDockerLauncher.LaunchSpec spec = new LocalDockerLauncher.LaunchSpec(
                job.getId(), image, job.getEpochs(), job.getFailAtEpoch(),
                job.getCpuMillis(), job.getMemMb());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                launcher.launch(spec);
            }
        });
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
        job.finish(JobStatus.CANCELLED);
        launcher.stop(id);
        return job;
    }
}
