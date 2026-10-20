package com.linewell.dataelement.feature.surveillance.domain;

import java.time.Instant;
import java.time.LocalDate;

public record SurveillanceTopicDailyStat(
        String resourceCode,
        String channelCode,
        String inputTopic,
        String resultTopic,
        LocalDate statDate,
        long inputCount,
        long matchedCount,
        long unmatchedCount,
        long failureCount,
        long totalInputCount,
        long totalMatchedCount,
        Instant lastEventTime,
        Instant updatedTime
) {
}
