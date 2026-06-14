package com.trainqueue.api.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.api.exception.JobNotFoundException;
import com.trainqueue.api.job.dto.CreateJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    JobService service;

    private Job sampleJob() {
        return new Job(UUID.randomUUID(), "demo", "worker-sim:latest", null,
                5, null, 1, 1000, 1024, 0);
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(sampleJob());

        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateJobRequest(
                                "demo", 5, 1, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("demo"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        // blank name + epochs 0 both violate validation
        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateJobRequest(
                                "", 0, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any());
    }

    @Test
    void listReturnsJobs() throws Exception {
        when(service.list(Optional.empty())).thenReturn(List.of(sampleJob()));

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.find(id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelReturnsCancelledJob() throws Exception {
        Job job = sampleJob();
        job.cancel();
        when(service.cancel(any())).thenReturn(job);

        mvc.perform(post("/api/jobs/{id}/cancel", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
