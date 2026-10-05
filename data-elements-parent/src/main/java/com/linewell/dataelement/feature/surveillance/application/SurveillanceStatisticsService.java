package com.linewell.dataelement.feature.surveillance.application;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceTopicDailyStat;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceTopicStatisticsRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SurveillanceStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SurveillanceTopicStatisticsRepository repository;

    public SurveillanceStatisticsService(SurveillanceTopicStatisticsRepository repository) {
        this.repository = repository;
    }

    public void recordMatchBatch(String tenantId, String engineCode, String channelCode, String resourceCode,
                                 String inputTopic, String resultTopic, String batchId,
                                 List<SurveillanceRuleService.Event> events,
                                 List<SurveillanceRuleService.MatchResult> results) {
        if (resourceCode == null || resourceCode.isBlank()) return;
        Map<LocalDate, MutableMetric> metrics = new LinkedHashMap<>();
        for (SurveillanceRuleService.MatchResult result : results) {
            LocalDate date = dateOf(result.occurredAt());
            MutableMetric metric = metrics.computeIfAbsent(date, ignored -> new MutableMetric());
            metric.inputCount++;
            if (result.matched()) metric.matchedCount++;
            else metric.unmatchedCount++;
            if (result.occurredAt() != null && (metric.lastEventTime == null || result.occurredAt().isAfter(metric.lastEventTime))) {
                metric.lastEventTime = result.occurredAt();
            }
        }
        String effectiveBatchId = batchId == null || batchId.isBlank() ? derivedBatchId(events) : batchId.trim();
        metrics.forEach((date, metric) -> repository.recordBatch(tenantId, engineCode, resourceCode,
                effectiveBatchId, new SurveillanceTopicStatisticsRepository.BatchMetric(
                        channelCode, inputTopic, resultTopic, date, metric.inputCount, metric.matchedCount,
                        metric.unmatchedCount, 0, metric.lastEventTime)));
    }

    public List<SurveillanceTopicDailyStat> query(String tenantId, String engineCode,
                                                   String channelCode, String resourceCode,
                                                   LocalDate statDate) {
        return repository.query(tenantId, engineCode, channelCode, resourceCode,
                statDate == null ? LocalDate.now(BUSINESS_ZONE) : statDate);
    }

    public void recordFailureBatch(String tenantId, String engineCode, String channelCode, String resourceCode,
                                   String inputTopic, String resultTopic, String batchId, LocalDate statDate,
                                   long inputCount, long failureCount) {
        if (resourceCode == null || resourceCode.isBlank() || failureCount <= 0) return;
        repository.recordBatch(tenantId, engineCode, resourceCode,
                batchId == null || batchId.isBlank() ? "failure-" + Instant.now().toEpochMilli() : batchId.trim(),
                new SurveillanceTopicStatisticsRepository.BatchMetric(
                        channelCode, inputTopic, resultTopic,
                        statDate == null ? LocalDate.now(BUSINESS_ZONE) : statDate,
                        Math.max(inputCount, failureCount), 0, 0, failureCount, null));
    }

    private LocalDate dateOf(Instant occurredAt) {
        return (occurredAt == null ? Instant.now() : occurredAt).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private String derivedBatchId(List<SurveillanceRuleService.Event> events) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (SurveillanceRuleService.Event event : events) {
                digest.update(event.eventId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return "derived-" + java.util.HexFormat.of().formatHex(digest.digest(), 0, 16);
        } catch (Exception exception) {
            return "derived-" + events.hashCode();
        }
    }

    private static final class MutableMetric {
        private long inputCount;
        private long matchedCount;
        private long unmatchedCount;
        private Instant lastEventTime;
    }
}
