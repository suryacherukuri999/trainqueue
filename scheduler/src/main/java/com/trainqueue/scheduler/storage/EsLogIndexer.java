package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Buffers log docs and bulk-indexes them into Elasticsearch by count or on a timer. */
@Component
public class EsLogIndexer {

    private static final Logger log = LoggerFactory.getLogger(EsLogIndexer.class);

    private final ElasticsearchOperations operations;
    private final IndexCoordinates index;
    private final int flushCount;
    private final Queue<LogDoc> buffer = new ConcurrentLinkedQueue<>();

    public EsLogIndexer(ElasticsearchOperations operations, SchedulerProperties props) {
        this.operations = operations;
        this.index = IndexCoordinates.of(props.es().index());
        this.flushCount = props.es().flushCount();
    }

    public void add(LogDoc doc) {
        buffer.add(doc);
        if (buffer.size() >= flushCount) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${trainqueue.es.flush-ms}", initialDelayString = "${trainqueue.es.flush-ms}")
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<LogDoc> batch = new ArrayList<>();
        LogDoc doc;
        while ((doc = buffer.poll()) != null) {
            batch.add(doc);
        }
        try {
            operations.save(batch, index);
        } catch (Exception e) {
            log.warn("failed to index {} log line(s): {}", batch.size(), e.getMessage());
        }
    }
}
