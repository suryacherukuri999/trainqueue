package com.trainqueue.api.job;

import org.junit.jupiter.api.Test;

import static com.trainqueue.api.job.JobStatus.CANCELLED;
import static com.trainqueue.api.job.JobStatus.FAILED;
import static com.trainqueue.api.job.JobStatus.QUEUED;
import static com.trainqueue.api.job.JobStatus.RUNNING;
import static com.trainqueue.api.job.JobStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTransitionTest {

    @Test
    void queuedMayStartOrCancel() {
        assertThat(QUEUED.canTransitionTo(RUNNING)).isTrue();
        assertThat(QUEUED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(QUEUED.canTransitionTo(SUCCEEDED)).isFalse();
        assertThat(QUEUED.canTransitionTo(FAILED)).isFalse();
    }

    @Test
    void runningMayFinishOrCancel() {
        assertThat(RUNNING.canTransitionTo(SUCCEEDED)).isTrue();
        assertThat(RUNNING.canTransitionTo(FAILED)).isTrue();
        assertThat(RUNNING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(RUNNING.canTransitionTo(QUEUED)).isFalse();
    }

    @Test
    void terminalStatesAreFinal() {
        for (JobStatus terminal : new JobStatus[]{SUCCEEDED, FAILED, CANCELLED}) {
            for (JobStatus target : JobStatus.values()) {
                assertThat(terminal.canTransitionTo(target))
                        .as("%s -> %s", terminal, target)
                        .isFalse();
            }
        }
    }
}
