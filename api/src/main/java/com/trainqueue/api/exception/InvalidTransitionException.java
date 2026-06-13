package com.trainqueue.api.exception;

import com.trainqueue.api.job.JobStatus;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(JobStatus from, JobStatus to) {
        super("illegal status transition: " + from + " -> " + to);
    }
}
