package com.trainqueue.scheduler.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainqueue.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers log docs and bulk-indexes them into Elasticsearch by count or on a timer.
 * On failure the batch is written to a durable on-disk spool and retried later, so
 * an ES outage (even across a restart) never silently drops a job's logs.
 */
@Component
public class EsLogIndexer {

    private static final Logger log = LoggerFactory.getLogger(EsLogIndexer.class);

    private final ElasticsearchOperations operations;
    private final ObjectMapper mapper;
    private final IndexCoordinates index;
    private final int flushCount;
    private final Path spool;
    private final Queue<LogDoc> buffer = new ConcurrentLinkedQueue<>();
    private final ReentrantLock spoolLock = new ReentrantLock();

    public EsLogIndexer(ElasticsearchOperations operations, ObjectMapper mapper, SchedulerProperties props) {
        this.operations = operations;
        this.mapper = mapper;
        this.index = IndexCoordinates.of(props.es().index());
        this.flushCount = props.es().flushCount();
        this.spool = Path.of(props.es().spoolFile());
    }

    public void add(LogDoc doc) {
        buffer.add(doc);
        if (buffer.size() >= flushCount) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${trainqueue.es.flush-ms}", initialDelayString = "${trainqueue.es.flush-ms}")
    public void flush() {
        List<LogDoc> batch = drain();
        if (batch.isEmpty()) {
            return;
        }
        try {
            operations.save(batch, index);
        } catch (Exception e) {
            log.warn("indexing {} log line(s) failed; spooling: {}", batch.size(), e.getMessage());
            spool(batch);
        }
    }

    /** Retry spooled batches once ES is reachable again. */
    @Scheduled(fixedDelayString = "${trainqueue.es.flush-ms}", initialDelayString = "${trainqueue.es.flush-ms}")
    public void drainSpool() {
        spoolLock.lock();
        try {
            if (!Files.exists(spool)) {
                return;
            }
            List<LogDoc> docs = new ArrayList<>();
            for (String line : Files.readAllLines(spool, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    docs.add(mapper.readValue(line, LogDoc.class));
                }
            }
            if (docs.isEmpty()) {
                Files.deleteIfExists(spool);
                return;
            }
            operations.save(docs, index); // throws if ES still down -> keep the spool
            Files.deleteIfExists(spool);
            log.info("recovered {} spooled log line(s) into elasticsearch", docs.size());
        } catch (Exception e) {
            log.warn("spool drain deferred: {}", e.getMessage());
        } finally {
            spoolLock.unlock();
        }
    }

    private List<LogDoc> drain() {
        List<LogDoc> batch = new ArrayList<>();
        LogDoc doc;
        while ((doc = buffer.poll()) != null) {
            batch.add(doc);
        }
        return batch;
    }

    private void spool(List<LogDoc> batch) {
        spoolLock.lock();
        try (var writer = Files.newBufferedWriter(spool, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (LogDoc doc : batch) {
                writer.write(mapper.writeValueAsString(doc));
                writer.newLine();
            }
        } catch (IOException io) {
            log.error("failed to spool {} log line(s): {}", batch.size(), io.getMessage());
        } finally {
            spoolLock.unlock();
        }
    }
}
