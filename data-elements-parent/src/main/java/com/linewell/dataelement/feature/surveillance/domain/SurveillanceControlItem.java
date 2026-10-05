package com.linewell.dataelement.feature.surveillance.domain;

import java.time.Instant;

/** A concrete object stored in surveillance_control_item_t and used by runtime matching. */
public record SurveillanceControlItem(
        String tid,
        String tenantId,
        String engineCode,
        String channelCode,
        String controlItemId,
        String ruleCode,
        String subjectType,
        String identifierType,
        String identifier,
        String subjectName,
        String sourceSystem,
        String controlReason,
        Instant effectiveFrom,
        Instant effectiveTo,
        String status,
        long snapshotVersion
) {
}
