package com.trainqueue.api.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.api.exception.InvalidTransitionException;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.job.dto.CreateJobRequest;
import com.trainqueue.api.job.dto.JobResponse;
import com.trainqueue.api.messaging.CancelCommand;
import com.trainqueue.api.messaging.JobStateCache;
import com.trainqueue.api.messaging.JobSubmittedEvent;
import com.trainqueue.api.outbox.OutboxEvent;
import com.trainqueue.api.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final OutboxRepository outbox;
    private final JobStateCache cache;
    private final ObjectMapper mapper;
    private final String defaultImage;
    private final String submittedTopic;
    private final String controlTopic;

    public JobService(JobRepository jobs, OutboxRepository outbox, JobStateCache cache, ObjectMapper mapper,
                      @Value("${trainqueue.default-image:worker-sim:latest}") String defaultImage,
                      @Value("${trainqueue.topics.submitted}") String submittedTopic,
                      @Value("${trainqueue.topics.control}") String controlTopic) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.cache = cache;
        this.mapper = mapper;
        this.defaultImage = defaultImage;
        this.submittedTopic = submittedTopic;
        this.controlTopic = controlTopic;
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

        // Event goes to the outbox in the same transaction; the relay publishes it.
        // A Kafka outage can't lose the event or hang the request.
        UUID eventId = UUID.randomUUID();
        JobSubmittedEvent event = new JobSubmittedEvent(eventId, job.getId(), job.getName(),
                job.getDockerImage(), job.getCommand(), job.getEpochs(), job.getFailAtEpoch(),
                job.getPriority(), job.getCpuMillis(), job.getMemMb(), job.getAttempt(),
                job.getMaxRetries(), job.getCreatedAt());
        outbox.save(new OutboxEvent(eventId, submittedTopic, job.getId().toString(), toJson(event)));
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
        UUID eventId = UUID.randomUUID();
        outbox.save(new OutboxEvent(eventId, controlTopic, id.toString(),
                toJson(new CancelCommand(eventId, id))));
        cache.evict(id);
        return job;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize outbox event", e);
        }
    }
}
