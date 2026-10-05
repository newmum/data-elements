package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceResourceTopic;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceTopicDailyStat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class SurveillanceTopicStatisticsRepository {
    private final JdbcTemplate jdbc;

    public SurveillanceTopicStatisticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordBatch(String tenantId, String engineCode, String resourceCode, String batchId,
                            BatchMetric metric) {
        int inserted = jdbc.update("""
                INSERT IGNORE INTO surveillance_metric_batch_t
                    (tid, tenant_id, engine_code, resource_code, batch_id, stat_date,
                     input_count, matched_count, unmatched_count, failure_count, created_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id(), tenantId, engineCode, resourceCode, batchId, metric.statDate(),
                metric.inputCount(), metric.matchedCount(), metric.unmatchedCount(), metric.failureCount(),
                Timestamp.from(Instant.now()));
        if (inserted == 0) return;

        jdbc.update("""
                INSERT INTO surveillance_topic_daily_stat_t
                    (tid, tenant_id, engine_code, channel_code, resource_code,
                     input_topic, result_topic, stat_date, input_count, matched_count,
                     unmatched_count, failure_count, last_event_time, updated_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_count = input_count + VALUES(input_count),
                    matched_count = matched_count + VALUES(matched_count),
                    unmatched_count = unmatched_count + VALUES(unmatched_count),
                    failure_count = failure_count + VALUES(failure_count),
                    last_event_time = CASE
                        WHEN VALUES(last_event_time) IS NULL THEN last_event_time
                        WHEN last_event_time IS NULL THEN VALUES(last_event_time)
                        ELSE GREATEST(last_event_time, VALUES(last_event_time))
                    END,
                    updated_time = VALUES(updated_time)
                """, id(), tenantId, engineCode, metric.channelCode(), resourceCode,
                metric.inputTopic(), metric.resultTopic(), metric.statDate(), metric.inputCount(),
                metric.matchedCount(), metric.unmatchedCount(), metric.failureCount(),
                metric.lastEventTime() == null ? null : Timestamp.from(metric.lastEventTime()),
                Timestamp.from(Instant.now()));
    }

    public List<SurveillanceTopicDailyStat> query(String tenantId, String engineCode,
                                                   String channelCode, String resourceCode,
                                                   LocalDate statDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.resource_code, r.channel_code, r.input_topic, r.result_topic,
                       COALESCE(SUM(s.input_count), 0) total_input_count,
                       COALESCE(SUM(s.matched_count), 0) total_matched_count,
                       COALESCE(SUM(CASE WHEN s.stat_date = ? THEN s.input_count ELSE 0 END), 0) today_input_count,
                       COALESCE(SUM(CASE WHEN s.stat_date = ? THEN s.matched_count ELSE 0 END), 0) today_matched_count,
                       COALESCE(SUM(CASE WHEN s.stat_date = ? THEN s.unmatched_count ELSE 0 END), 0) today_unmatched_count,
                       COALESCE(SUM(CASE WHEN s.stat_date = ? THEN s.failure_count ELSE 0 END), 0) today_failure_count,
                       MAX(s.last_event_time) last_event_time, MAX(s.updated_time) updated_time
                  FROM surveillance_resource_topic_t r
                  LEFT JOIN surveillance_topic_daily_stat_t s
                    ON s.tenant_id = r.tenant_id
                   AND s.engine_code = r.engine_code
                   AND s.resource_code = r.resource_code
                 WHERE r.tenant_id = ? AND r.engine_code = ? AND r.is_del = 0
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(statDate); args.add(statDate); args.add(statDate); args.add(statDate);
        args.add(tenantId); args.add(engineCode);
        if (channelCode != null && !channelCode.isBlank()) { sql.append(" AND r.channel_code = ?"); args.add(channelCode.trim()); }
        if (resourceCode != null && !resourceCode.isBlank()) { sql.append(" AND r.resource_code = ?"); args.add(resourceCode.trim()); }
        sql.append(" GROUP BY r.resource_code, r.channel_code, r.input_topic, r.result_topic ORDER BY r.channel_code, r.resource_code");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new SurveillanceTopicDailyStat(
                rs.getString("resource_code"), rs.getString("channel_code"), rs.getString("input_topic"),
                rs.getString("result_topic"), statDate, rs.getLong("today_input_count"),
                rs.getLong("today_matched_count"), rs.getLong("today_unmatched_count"),
                rs.getLong("today_failure_count"), rs.getLong("total_input_count"),
                rs.getLong("total_matched_count"), instant(rs.getTimestamp("last_event_time")),
                instant(rs.getTimestamp("updated_time"))), args.toArray());
    }

    public record BatchMetric(String channelCode, String inputTopic, String resultTopic,
                              LocalDate statDate, long inputCount, long matchedCount,
                              long unmatchedCount, long failureCount, Instant lastEventTime) {
    }

    private Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
