package com.linewell.dataelement.integration.nifi.canvas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nifi")
public record NifiProperties(
        Boolean monitorEnabled,
        Long monitorFixedDelayMs,
        Long monitorInitialDelayMs,
        Integer monitorBatchSize,
        Boolean provenanceSyncEnabled,
        Long provenanceSyncFixedDelayMs,
        Long provenanceSyncInitialDelayMs
) {
    public NifiProperties {
        if (monitorEnabled == null) {
            monitorEnabled = true;
        }
        if (monitorFixedDelayMs == null || monitorFixedDelayMs <= 0) {
            monitorFixedDelayMs = 30000L;
        }
        if (monitorInitialDelayMs == null || monitorInitialDelayMs < 0) {
            monitorInitialDelayMs = 10000L;
        }
        if (monitorBatchSize == null || monitorBatchSize <= 0) {
            monitorBatchSize = 500;
        }
        if (provenanceSyncEnabled == null) {
            provenanceSyncEnabled = true;
        }
        if (provenanceSyncFixedDelayMs == null || provenanceSyncFixedDelayMs <= 0) {
            provenanceSyncFixedDelayMs = 60000L;
        }
        if (provenanceSyncInitialDelayMs == null || provenanceSyncInitialDelayMs < 0) {
            provenanceSyncInitialDelayMs = 15000L;
        }
    }
}
