package com.linewell.dataelement.integration.nifi.canvas.monitor;

import java.time.LocalDateTime;

public record LatencyMonitorSnapshot(
    LocalDateTime monitorTime,
    String delayLevel,
    Long delayMsEstimate,
    Long thresholdMs,
    Integer isTimeout,
    Long queuedCount,
    Long queuedBytes,
    Integer activeThreadCount,
    Long flowFilesIn,
    Long flowFilesOut,
    Long bytesIn,
    Long bytesOut,
    String monitorStatus,
    String monitorMsg
) {
}
