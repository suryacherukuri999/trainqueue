package com.trainqueue.scheduler.recovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.config.SchedulerProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Reads the api's job table on startup to reconcile what should be running. */
@Component
public class ApiClient {

    private final RestClient client;
    private final ObjectMapper mapper;

    public ApiClient(SchedulerProperties props, ObjectMapper mapper) {
        this.client = RestClient.builder().baseUrl(props.api().baseUrl()).build();
        this.mapper = mapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobInfo(
            UUID id, String name, String dockerImage, String command, int epochs, Integer failAtEpoch,
            int priority, int cpuMillis, int memMb, int attempt, int maxRetries, Instant createdAt) {
    }

    public List<JobInfo> runningJobs() {
        String body = client.get().uri("/api/jobs?status=RUNNING").retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.asList(mapper.readValue(body, JobInfo[].class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse running jobs", e);
        }
    }
}
