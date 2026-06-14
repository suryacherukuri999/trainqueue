package com.trainqueue.scheduler.storage;

import com.trainqueue.scheduler.messaging.WorkerMetric;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

/** One indexed log line (Elasticsearch index job-logs). */
@Document(indexName = "job-logs")
public class LogDoc {

    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String jobId;
    @Field(type = FieldType.Integer)
    private int attempt;
    @Field(type = FieldType.Long)
    private long ts; // epoch millis
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

    public static LogDoc metric(UUID jobId, int attempt, WorkerMetric m) {
        LogDoc d = new LogDoc();
        d.jobId = jobId.toString();
        d.attempt = attempt;
        d.ts = System.currentTimeMillis();
        d.epoch = m.epoch();
        d.loss = m.loss();
        d.accuracy = m.accuracy();
        d.level = "INFO";
        d.message = String.format("epoch %d loss %.4f accuracy %.4f", m.epoch(), m.loss(), m.accuracy());
        return d;
    }

    public static LogDoc message(UUID jobId, int attempt, String message) {
        LogDoc d = new LogDoc();
        d.jobId = jobId.toString();
        d.attempt = attempt;
        d.ts = System.currentTimeMillis();
        d.level = "INFO";
        d.message = message;
        return d;
    }

    public String getId() {
        return id;
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(ts);
    }
}
