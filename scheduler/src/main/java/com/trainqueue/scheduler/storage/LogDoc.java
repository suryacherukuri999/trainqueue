package com.trainqueue.scheduler.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.UUID;

/**
 * One indexed log line (Elasticsearch index job-logs). The id is stable —
 * (jobId, attempt, sequence) — so replaying a job's logs after recovery upserts
 * rather than duplicating. Field-level Jackson visibility lets it round-trip
 * through the durable spool.
 */
@Document(indexName = "job-logs")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LogDoc {

    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String jobId;
    @Field(type = FieldType.Integer)
    private int attempt;
    @Field(type = FieldType.Long)
    private long ts;
    @Field(type = FieldType.Integer)
    private Integer epoch;
    @Field(type = FieldType.Double)
    private Double loss;
    @Field(type = FieldType.Double)
    private Double accuracy;
    @Field(type = FieldType.Keyword)
    private String level;
    @Field(type = FieldType.Text)
    private String message;

    public LogDoc() {
    }

    public static LogDoc metric(UUID jobId, int attempt, long sequence, WorkerMetric m) {
        LogDoc d = base(jobId, attempt, sequence, "INFO");
        d.epoch = m.epoch();
        d.loss = m.loss();
        d.accuracy = m.accuracy();
        d.message = String.format("epoch %d loss %.4f accuracy %.4f", m.epoch(), m.loss(), m.accuracy());
        return d;
    }

    public static LogDoc message(UUID jobId, int attempt, long sequence, String level, String message) {
        LogDoc d = base(jobId, attempt, sequence, level);
        d.message = message;
        return d;
    }

    private static LogDoc base(UUID jobId, int attempt, long sequence, String level) {
        LogDoc d = new LogDoc();
        d.id = jobId + "-" + attempt + "-" + sequence;
        d.jobId = jobId.toString();
        d.attempt = attempt;
        d.ts = System.currentTimeMillis();
        d.level = level;
        return d;
    }
}
