package com.trainqueue.api.logs;

import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.util.StringUtils;

import java.util.UUID;

/** Builds the Elasticsearch criteria for a job's log search: always scoped to the job,
 *  optionally full-text matching the message and bounding the ts (epoch millis) range. */
public final class LogQueryBuilder {

    private LogQueryBuilder() {
    }

    public static Criteria criteria(UUID jobId, String q, Long from, Long to) {
        Criteria criteria = new Criteria("jobId").is(jobId.toString());
        if (StringUtils.hasText(q)) {
            criteria = criteria.and(new Criteria("message").matches(q));
        }
        if (from != null) {
            criteria = criteria.and(new Criteria("ts").greaterThanEqual(from));
        }
        if (to != null) {
            criteria = criteria.and(new Criteria("ts").lessThanEqual(to));
        }
        return criteria;
    }
}
