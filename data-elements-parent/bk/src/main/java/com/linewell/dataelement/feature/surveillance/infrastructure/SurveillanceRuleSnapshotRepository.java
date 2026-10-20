package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped durable store for the latest normalized rule snapshot versions. */
@Repository
public class SurveillanceRuleSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SurveillanceRuleSnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(String tenantId, SurveillanceRuleSnapshot snapshot) {
        try {
            String rulesJson = mapper.writeValueAsString(new SnapshotPayload(
                    snapshot.ruleDefinitions(), snapshot.controlItems(), snapshot.rules()));
            Instant now = Instant.now();
            String snapshotTid = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                    INSERT INTO surveillance_rule_snapshot_t
                    (tid, tenant_id, engine_code, channel_code, snapshot_version, digest, source_type,
                     source_ref, rules_json, generated_at, expires_at, snapshot_status, created_time, updated_time, is_del)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE digest = VALUES(digest), source_type = VALUES(source_type),
                    source_ref = VALUES(source_ref), rules_json = VALUES(rules_json), generated_at = VALUES(generated_at),
                    expires_at = VALUES(expires_at), snapshot_status = VALUES(snapshot_status), updated_time = VALUES(updated_time)
                    """,
                    snapshotTid, tenantId, snapshot.engineCode(), snapshot.channelCode(),
                    snapshot.version(), snapshot.digest(), snapshot.sourceType(), snapshot.sourceRef(), rulesJson,
                    Timestamp.from(orNow(snapshot.generatedAt())), timestamp(snapshot.expiresAt()), snapshot.status(),
                    Timestamp.from(now), Timestamp.from(now));
            String persistedSnapshotTid = jdbc.queryForObject("""
                    SELECT tid FROM surveillance_rule_snapshot_t
                     WHERE tenant_id = ? AND engine_code = ? AND channel_code = ?
                       AND snapshot_version = ? AND is_del = 0
                    """, String.class, tenantId, snapshot.engineCode(), snapshot.channelCode(), snapshot.version());
            persistChildren(tenantId, snapshot, persistedSnapshotTid, now);
        } catch (Exception exception) {
            throw new IllegalStateException("保存布控规则快照失败", exception);
        }
    }

    private void persistChildren(String tenantId, SurveillanceRuleSnapshot snapshot, String snapshotTid, Instant now) throws Exception {
        jdbc.update("DELETE FROM surveillance_control_item_t WHERE snapshot_tid = ?", snapshotTid);
        jdbc.update("DELETE FROM surveillance_rule_definition_t WHERE snapshot_tid = ?", snapshotTid);
        Map<String, String> definitionTids = new LinkedHashMap<>();
        for (SurveillanceRuleSnapshot.RuleDefinition definition : snapshot.ruleDefinitions()) {
            String definitionTid = UUID.randomUUID().toString().replace("-", "");
            definitionTids.put(definition.ruleCode(), definitionTid);
            jdbc.update("""
                    INSERT INTO surveillance_rule_definition_t
                    (tid, snapshot_tid, tenant_id, engine_code, channel_code, rule_code, rule_name,
                     subject_type, match_mode, match_fields_json, priority, status, source_system, reason,
                     effective_from, effective_to, metadata_json, created_time, updated_time, is_del)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, definitionTid, snapshotTid, tenantId, snapshot.engineCode(), snapshot.channelCode(),
                    definition.ruleCode(), definition.ruleName(), definition.subjectType(), definition.matchMode(),
                    mapper.writeValueAsString(definition.matchFields()), definition.priority(), definition.status(),
                    definition.sourceSystem(), definition.reason(), timestamp(definition.effectiveFrom()),
                    timestamp(definition.effectiveTo()), mapper.writeValueAsString(definition.metadata()),
                    Timestamp.from(now), Timestamp.from(now));
        }
        for (SurveillanceRuleSnapshot.ControlItem item : snapshot.controlItems()) {
            String definitionTid = definitionTids.get(item.ruleCode());
            if (definitionTid == null) continue;
            jdbc.update("""
                    INSERT INTO surveillance_control_item_t
                    (tid, rule_definition_tid, snapshot_tid, tenant_id, engine_code, channel_code,
                     snapshot_version, control_item_id, rule_code, subject_type, identifier_type,
                     identifier_value, identifiers_json,
                     attributes_json, source_rule_no, control_reason, effective_from, effective_to,
                     status, metadata_json, created_time, updated_time, is_del)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, UUID.randomUUID().toString().replace("-", ""), definitionTid, snapshotTid,
                    tenantId, snapshot.engineCode(), snapshot.channelCode(), snapshot.version(), item.controlItemId(),
                    item.ruleCode(), item.subjectType(), item.identifiers().isEmpty() ? null : item.identifiers().getFirst().type(),
                    item.identifiers().isEmpty() ? null : item.identifiers().getFirst().value(),
                    mapper.writeValueAsString(item.identifiers()), mapper.writeValueAsString(item.attributes()),
                    item.sourceRuleNo(), item.controlReason(), timestamp(item.effectiveFrom()),
                    timestamp(item.effectiveTo()), item.status(), mapper.writeValueAsString(item.metadata()),
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    public Optional<SurveillanceRuleSnapshot> findLatest(String tenantId, String engineCode, String channelCode) {
        List<SurveillanceRuleSnapshot> rows = jdbc.query("""
                SELECT snapshot_version, digest, source_type, source_ref, rules_json,
                       generated_at, expires_at, snapshot_status
                  FROM surveillance_rule_snapshot_t
                 WHERE tenant_id = ? AND engine_code = ? AND channel_code = ?
                   AND is_del = 0 AND snapshot_status = 'ACTIVE'
                 ORDER BY snapshot_version DESC LIMIT 1
                """, (rs, rowNum) -> new SurveillanceRuleSnapshot(
                engineCode, channelCode, rs.getLong("snapshot_version"),
                instant(rs.getTimestamp("generated_at")), instant(rs.getTimestamp("expires_at")),
                rs.getString("digest"), rs.getString("source_type"), rs.getString("source_ref"),
                rs.getString("snapshot_status"), readStoredRules(rs.getString("rules_json"))),
                tenantId, engineCode, channelCode);
        return rows.stream().findFirst();
    }

    private List<SurveillanceRuleSnapshot.Rule> readStoredRules(String json) {
        try {
            SnapshotPayload payload = mapper.readValue(json, SnapshotPayload.class);
            if (payload.ruleDefinitions() != null && !payload.ruleDefinitions().isEmpty()) {
                return new SurveillanceRuleSnapshot("", "", 1, null, null, null, null, null,
                        "ACTIVE", payload.ruleDefinitions(), payload.controlItems()).rules();
            }
            return payload.rules() == null ? List.of() : payload.rules();
        } catch (Exception exception) {
            try {
                return mapper.readValue(json, new TypeReference<List<SurveillanceRuleSnapshot.Rule>>() {});
            } catch (Exception legacyException) {
                throw new IllegalStateException("布控规则快照 JSON 损坏", legacyException);
            }
        }
    }

    private record SnapshotPayload(
            List<SurveillanceRuleSnapshot.RuleDefinition> ruleDefinitions,
            List<SurveillanceRuleSnapshot.ControlItem> controlItems,
            List<SurveillanceRuleSnapshot.Rule> rules
    ) {}

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Instant orNow(Instant value) {
        return value == null ? Instant.now() : value;
    }
}
