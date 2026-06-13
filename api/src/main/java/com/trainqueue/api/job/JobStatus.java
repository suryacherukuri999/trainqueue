package com.trainqueue.api.job;

import java.util.Set;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    // Legal transitions. The scheduler's JobStateMachine in milestone 2 enforces
    // the same rules over Kafka status events; keeping them here lets the
    // milestone-1 launcher reject illegal updates too.
    private static final Set<JobStatus> TERMINAL = Set.of(SUCCEEDED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(JobStatus target) {
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }
}
