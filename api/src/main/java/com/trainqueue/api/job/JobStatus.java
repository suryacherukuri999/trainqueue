package com.trainqueue.api.job;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    // Legal transitions. The state machine that applies Kafka status events enforces
    // the same rules, and the cancel endpoint uses this to reject illegal cancels.
    public boolean canTransitionTo(JobStatus target) {
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }
}
