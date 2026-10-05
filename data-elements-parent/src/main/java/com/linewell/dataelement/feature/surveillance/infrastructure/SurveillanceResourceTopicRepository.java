package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceResourceTopic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SurveillanceResourceTopicRepository {
    private final JdbcTemplate jdbc;

    public SurveillanceResourceTopicRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SurveillanceResourceTopic> findAll(String tenantId, String engineCode,
                                                    String channelCode, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT tid, tenant_id, engine_code, channel_code, resource_code,
                       source_table, input_topic, result_topic, identifier_type,
                       key_field, schema_json, status, created_time, updated_time
                  FROM surveillance_resource_topic_t
                 WHERE tenant_id = ? AND engine_code = ? AND is_del = 0
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        args.add(engineCode);
        if (channelCode != null && !channelCode.isBlank()) {
            sql.append(" AND channel_code = ?");
            args.add(channelCode.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY channel_code, resource_code");
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    public Optional<SurveillanceResourceTopic> find(String tenantId, String engineCode, String resourceCode) {
        return jdbc.query("""
                SELECT tid, tenant_id, engine_code, channel_code, resource_code,
                       source_table, input_topic, result_topic, identifier_type,
                       key_field, schema_json, status, created_time, updated_time
                  FROM surveillance_resource_topic_t
                 WHERE tenant_id = ? AND engine_code = ? AND resource_code = ? AND is_del = 0
                """, this::map, tenantId, engineCode, resourceCode).stream().findFirst();
    }

    public SurveillanceResourceTopic insert(SurveillanceResourceTopic item) {
        Instant now = Instant.now();
        String tid = item.tid() == null || item.tid().isBlank() ? id() : item.tid();
        jdbc.update("""
                INSERT INTO surveillance_resource_topic_t
                    (tid, tenant_id, engine_code, channel_code, resource_code,
                     source_table, input_topic, result_topic, identifier_type,
                     key_field, schema_json, status, created_time, updated_time, is_del)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, tid, item.tenantId(), item.engineCode(), item.channelCode(), item.resourceCode(),
                item.sourceTable(), item.inputTopic(), item.resultTopic(), item.identifierType(),
                item.keyField(), item.schemaJson(), item.status(), Timestamp.from(now), Timestamp.from(now));
        return new SurveillanceResourceTopic(tid, item.tenantId(), item.engineCode(), item.channelCode(),
                item.resourceCode(), item.sourceTable(), item.inputTopic(), item.resultTopic(), item.identifierType(),
                item.keyField(), item.schemaJson(), item.status(), now, now);
    }

    public SurveillanceResourceTopic update(SurveillanceResourceTopic item) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE surveillance_resource_topic_t
                   SET channel_code = ?, source_table = ?, input_topic = ?, result_topic = ?,
                       identifier_type = ?, key_field = ?, schema_json = ?,
                       status = ?, updated_time = ?
                 WHERE tenant_id = ? AND engine_code = ? AND resource_code = ? AND is_del = 0
                """, item.channelCode(), item.sourceTable(), item.inputTopic(), item.resultTopic(), item.identifierType(),
                item.keyField(), item.schemaJson(), item.status(), Timestamp.from(now), item.tenantId(),
                item.engineCode(), item.resourceCode());
        if (updated == 0) throw new IllegalArgumentException("布控资源 Topic 不存在: " + item.resourceCode());
        return find(item.tenantId(), item.engineCode(), item.resourceCode()).orElseThrow();
    }

    public void softDelete(String tenantId, String engineCode, String resourceCode) {
        jdbc.update("""
                UPDATE surveillance_resource_topic_t
                   SET status = 'DISABLED', updated_time = ?, is_del = 1
                 WHERE tenant_id = ? AND engine_code = ? AND resource_code = ? AND is_del = 0
                """, Timestamp.from(Instant.now()), tenantId, engineCode, resourceCode);
    }

    private SurveillanceResourceTopic map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SurveillanceResourceTopic(
                rs.getString("tid"), rs.getString("tenant_id"), rs.getString("engine_code"),
                rs.getString("channel_code"), rs.getString("resource_code"), rs.getString("source_table"),
                rs.getString("input_topic"), rs.getString("result_topic"), rs.getString("identifier_type"),
                rs.getString("key_field"), rs.getString("schema_json"), rs.getString("status"),
                instant(rs.getTimestamp("created_time")), instant(rs.getTimestamp("updated_time")));
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
