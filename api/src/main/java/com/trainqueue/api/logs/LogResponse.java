package com.trainqueue.api.logs;

public record LogResponse(
        long ts,
        Integer epoch,
        Double loss,
        Double accuracy,
        String level,
        String message
) {
    public static LogResponse from(LogDoc doc) {
        return new LogResponse(doc.getTs(), doc.getEpoch(), doc.getLoss(),
                doc.getAccuracy(), doc.getLevel(), doc.getMessage());
    }
}
