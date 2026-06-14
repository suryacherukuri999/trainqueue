package com.trainqueue.api.logs;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/** Read view of an indexed log line (Elasticsearch index job-logs). */
@Document(indexName = "job-logs", createIndex = false)
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

    public long getTs() {
        return ts;
    }

    public Integer getEpoch() {
        return epoch;
    }

    public Double getLoss() {
        return loss;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
