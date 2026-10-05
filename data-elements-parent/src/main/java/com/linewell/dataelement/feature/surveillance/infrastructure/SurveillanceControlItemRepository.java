package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.surveillance.application.SurveillanceIdentifierCodec;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceControlItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.sql.Timestamp;
import java.time.Instant;

/** Runtime matcher store. Deliberately reads only surveillance_control_item_t. */
@Repository
public class SurveillanceControlItemRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SurveillanceControlItemRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, List<ControlItemMatch>> findActiveMatches(
            String tenantId,
            String engineCode,
            String channelCode,
            Set<String> identifierValues
    ) {
        if (identifierValues == null || identifierValues.isEmpty()) return Map.of();
        String identifierType = SurveillanceIdentifierCodec.identifierType(channelCode);
        Map<String, List<ControlItemMatch>> grouped = new LinkedHashMap<>();
        List<String> identifiers = new ArrayList<>(identifierValues);
        for (int offset = 0; offset < identifiers.size(); offset += 500) {
            List<String> chunk = identifiers.subList(offset, Math.min(offset + 500, identifiers.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            List<Object> parameters = new ArrayList<>();
            parameters.add(tenantId);
            parameters.add(engineCode);
            parameters.add(channelCode);
            parameters.add(identifierType);
            parameters.addAll(chunk);
            List<ControlItemMatch> rows = jdbc.query("""
                SELECT control_item_id, rule_code, subject_type, snapshot_version,
                       identifier_value, attributes_json,
                       source_rule_no, control_reason
                  FROM surveillance_control_item_t
                 WHERE tenant_id = ?
                   AND engine_code = ?
                   AND channel_code = ?
                   AND identifier_type = ?
                   AND identifier_value IN (""" + placeholders + """
                   )
                   AND status = 'ACTIVE'
                   AND (effective_from IS NULL OR effective_from <= CURRENT_TIMESTAMP(6))
                   AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP(6))
                   AND is_del = 0
                """, (rs, rowNum) -> new ControlItemMatch(
                rs.getString("control_item_id"),
                rs.getString("rule_code"),
                rs.getString("subject_type"),
                rs.getLong("snapshot_version"),
                rs.getString("identifier_value"),
                readMap(rs.getString("attributes_json")),
                rs.getString("source_rule_no"),
                rs.getString("control_reason")), parameters.toArray());
            rows.forEach(row -> grouped.computeIfAbsent(row.identifierValue(), key -> new ArrayList<>()).add(row));
        }
        return grouped;
    }

    public List<SurveillanceControlItem> findAll(String tenantId, String engineCode,
                                                 String channelCode, String status, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT tid, tenant_id, engine_code, channel_code, control_item_id,
                       rule_code, subject_type, identifier_type, identifier_value,
                       attributes_json, control_reason, effective_from, effective_to,
                       status, snapshot_version
                  FROM surveillance_control_item_t
                 WHERE tenant_id = ? AND engine_code = ? AND is_del = 0
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(engineCode);
        if (channelCode != null && !channelCode.isBlank()) {
            sql.append(" AND channel_code = ?");
            args.add(channelCode.trim().toLowerCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (control_item_id LIKE ? OR rule_code LIKE ? OR identifier_value LIKE ? OR attributes_json LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY channel_code, created_time, control_item_id");
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapView(rs), args.toArray());
    }

    private SurveillanceControlItem mapView(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> attributes = readMap(rs.getString("attributes_json"));
        return new SurveillanceControlItem(
                rs.getString("tid"),
                rs.getString("tenant_id"),
                rs.getString("engine_code"),
                rs.getString("channel_code"),
                rs.getString("control_item_id"),
                rs.getString("rule_code"),
                rs.getString("subject_type"),
                rs.getString("identifier_type"),
                firstAttribute(attributes, "displayIdentifier", "identifier", "value", rs.getString("identifier_value")),
                firstAttribute(attributes, "name", "subjectName", "personName", "vehicleInfo", ""),
                firstAttribute(attributes, "sourceSystem", "source", ""),
                rs.getString("control_reason"),
                instant(rs.getTimestamp("effective_from")),
                instant(rs.getTimestamp("effective_to")),
                rs.getString("status"),
                rs.getLong("snapshot_version"));
    }

    private String firstAttribute(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            Object value = attributes.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("布控数据属性 JSON 损坏", exception);
        }
    }

    public record ControlItemMatch(
            String controlItemId,
            String ruleCode,
            String subjectType,
            long snapshotVersion,
            String identifierValue,
            Map<String, Object> attributes,
            String sourceRuleNo,
            String controlReason
    ) {
    }
}
