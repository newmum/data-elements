package com.linewell.dataelement.feature.surveillance.domain;

import java.time.Instant;

public record SurveillanceResourceTopic(
        String tid,
        String tenantId,
        String engineCode,
        String channelCode,
        String resourceCode,
        String sourceTable,
        String inputTopic,
        String resultTopic,
        String identifierType,
        String keyField,
        String schemaJson,
        String status,
        Instant createdTime,
        Instant updatedTime
) {
    public SurveillanceResourceTopic {
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
    }

    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
