package com.trainqueue.scheduler.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/** One epoch's metrics parsed from a worker-sim stdout line. */
public record WorkerMetric(int epoch, double loss, double accuracy) {

    public static Optional<WorkerMetric> parse(ObjectMapper mapper, String line) {
        try {
            JsonNode n = mapper.readTree(line);
            if (!n.hasNonNull("epoch")) {
                return Optional.empty();
            }
            return Optional.of(new WorkerMetric(
                    n.get("epoch").asInt(),
                    n.path("loss").asDouble(),
                    n.path("accuracy").asDouble()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
