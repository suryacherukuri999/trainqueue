package com.trainqueue.api.logs;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.query.Criteria;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogQueryBuilderTest {

    private static Set<String> fields(Criteria criteria) {
        Set<String> names = new HashSet<>();
        if (criteria.getField() != null) {
            names.add(criteria.getField().getName());
        }
        criteria.getCriteriaChain().forEach(c -> {
            if (c.getField() != null) {
                names.add(c.getField().getName());
            }
        });
        return names;
    }

    @Test
    void alwaysScopesToTheJobOnly() {
        Set<String> fields = fields(LogQueryBuilder.criteria(UUID.randomUUID(), null, null, null));
        assertThat(fields).containsExactly("jobId");
    }

    @Test
    void addsMessageMatchWhenQueryPresent() {
        Set<String> fields = fields(LogQueryBuilder.criteria(UUID.randomUUID(), "loss", null, null));
        assertThat(fields).contains("jobId", "message");
        assertThat(fields).doesNotContain("ts");
    }

    @Test
    void addsTimeRangeWhenFromOrToPresent() {
        Set<String> fields = fields(LogQueryBuilder.criteria(UUID.randomUUID(), null, 100L, 200L));
        assertThat(fields).contains("jobId", "ts");
        assertThat(fields).doesNotContain("message");
    }

    @Test
    void combinesAllThreeFilters() {
        Set<String> fields = fields(LogQueryBuilder.criteria(UUID.randomUUID(), "epoch", 1L, 2L));
        assertThat(fields).contains("jobId", "message", "ts");
    }
}
