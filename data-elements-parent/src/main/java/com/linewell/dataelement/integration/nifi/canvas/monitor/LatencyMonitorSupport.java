package com.linewell.dataelement.integration.nifi.canvas.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class LatencyMonitorSupport {

    private static final long THRESHOLD_MS = 300_000L;

    public LatencyMonitorSnapshot fromStatus(JsonNode raw) {
        JsonNode snapshot = raw == null
            ? null
            : raw.path("processGroupStatus").path("aggregateSnapshot");
        long queuedCount = longValue(snapshot, "queuedCount");
        long queuedBytes = parseLong(snapshot == null ? null : snapshot.path("queuedSize").asText(null));
        int activeThreadCount = intValue(snapshot, "activeThreadCount");
        long flowFilesIn = longValue(snapshot, "flowFilesIn");
        long flowFilesOut = longValue(snapshot, "flowFilesOut");
        long bytesIn = longValue(snapshot, "bytesIn");
        long bytesOut = longValue(snapshot, "bytesOut");

        String delayLevel = classify(queuedCount, activeThreadCount);
        Long delayMsEstimate = estimateDelay(delayLevel);
        Integer isTimeout = isTimeout(delayLevel, delayMsEstimate) ? 1 : 0;
        String monitorStatus = "BLOCKED".equals(delayLevel) ? "BLOCKED" : "SUCCESS";
        String monitorMsg = message(delayLevel);

        return new LatencyMonitorSnapshot(
            LocalDateTime.now(),
            delayLevel,
            delayMsEstimate,
            THRESHOLD_MS,
            isTimeout,
            queuedCount,
            queuedBytes,
            activeThreadCount,
            flowFilesIn,
            flowFilesOut,
            bytesIn,
            bytesOut,
            monitorStatus,
            monitorMsg
        );
    }

    public LatencyMonitorSnapshot errorSnapshot(String message) {
        return new LatencyMonitorSnapshot(
            LocalDateTime.now(),
            "ERROR",
            null,
            THRESHOLD_MS,
            1,
            0L,
            0L,
            0,
            0L,
            0L,
            0L,
            0L,
            "ERROR",
            message
        );
    }

    private String classify(long queuedCount, int activeThreadCount) {
        if (queuedCount > 0 && activeThreadCount == 0) {
            return "BLOCKED";
        }
        if (queuedCount == 0) {
            return "NORMAL";
        }
        if (queuedCount < 1000) {
            return "MINOR";
        }
        return "SEVERE";
    }

    private Long estimateDelay(String delayLevel) {
        return switch (delayLevel) {
            case "NORMAL" -> 0L;
            case "MINOR" -> 30_000L;
            case "SEVERE" -> 300_000L;
            case "BLOCKED" -> -1L;
            default -> null;
        };
    }

    private boolean isTimeout(String delayLevel, Long delayMsEstimate) {
        if ("BLOCKED".equals(delayLevel)) {
            return true;
        }
        return delayMsEstimate != null && delayMsEstimate >= THRESHOLD_MS;
    }

    private String message(String delayLevel) {
        return switch (delayLevel) {
            case "NORMAL" -> "pipeline running normally";
            case "MINOR" -> "queue backlog detected";
            case "SEVERE" -> "severe queue backlog detected";
            case "BLOCKED" -> "queue blocked with no active threads";
            default -> "monitoring error";
        };
    }

    private long longValue(JsonNode node, String field) {
        if (node == null) {
            return 0L;
        }
        return parseLong(node.path(field).asText(null));
    }

    private int intValue(JsonNode node, String field) {
        if (node == null) {
            return 0;
        }
        return (int) parseLong(node.path(field).asText(null));
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
