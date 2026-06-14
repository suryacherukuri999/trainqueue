package com.trainqueue.api.logs;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LogSearchService {

    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations operations;

    public LogSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public List<LogResponse> search(UUID jobId, String q, Long from, Long to) {
        CriteriaQuery query = new CriteriaQuery(LogQueryBuilder.criteria(jobId, q, from, to),
                PageRequest.of(0, MAX_RESULTS, Sort.by("ts").ascending()));
        return operations.search(query, LogDoc.class).stream()
                .map(SearchHit::getContent)
                .map(LogResponse::from)
                .toList();
    }
}
