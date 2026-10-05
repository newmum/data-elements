package com.linewell.dataelement.feature.surveillance.infrastructure;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleProvider;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TableSurveillanceRuleProvider implements SurveillanceRuleProvider {
    private final JdbcTemplate jdbcTemplate;

    public TableSurveillanceRuleProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "TABLE";
    }

    @Override
    public SurveillanceRuleSnapshot load(String engineCode, String channelCode, String sourceRef) {
        if (sourceRef == null || !sourceRef.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("规则表标识不合法");
        }
        String tenantId = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from " + sourceRef + " where tenant_id = ?", tenantId);
        String subjectType = rows.isEmpty()
                ? subjectType(channelCode)
                : text(rows.getFirst(), "subject_type", "subjectType");
        String ruleCode = channelCode + ".default";
        String matchField = defaultMatchField(channelCode);
        SurveillanceRuleSnapshot.RuleDefinition definition = new SurveillanceRuleSnapshot.RuleDefinition(
                ruleCode, channelCode + "默认匹配规则", subjectType, "AND", List.of(matchField), 0,
                "ACTIVE", null, null, sourceRef, null, Map.of());
        List<SurveillanceRuleSnapshot.ControlItem> items = rows.stream().map(row -> {
            IdentifierValue identifier = identifier(row, channelCode);
            Map<String, Object> attributes = new LinkedHashMap<>(row);
            removeReserved(attributes);
            if (identifier != null) attributes.remove(identifier.column());
            return new SurveillanceRuleSnapshot.ControlItem(
                    text(row, "control_item_id", "controlItemId", "rule_id", "id", "tid"),
                    ruleCode, subjectType,
                    identifier == null ? List.of() : List.of(new SurveillanceRuleSnapshot.Identifier(identifier.type(), identifier.value())),
                    attributes, text(row, "source_rule_no", "sourceRuleNo"),
                    text(row, "control_reason", "reason"), instant(row.get("effective_from")),
                    instant(row.get("effective_to")), text(row, "status", "rule_status"), Map.of());
        }).toList();
        return new SurveillanceRuleSnapshot(engineCode, channelCode, System.currentTimeMillis(),
                Instant.now(), null, null, "TABLE", sourceRef, "ACTIVE", List.of(definition), items);
    }

    private String subjectType(String channelCode) {
        return switch (channelCode.toLowerCase()) {
            case "person" -> "PERSON";
            case "mobile" -> "MOBILE";
            case "vehicle" -> "VEHICLE";
            default -> "UNKNOWN";
        };
    }

    private String defaultMatchField(String channelCode) {
        return switch (channelCode.toLowerCase()) {
            case "person" -> "idCardNo";
            case "mobile" -> "phoneNo";
            case "vehicle" -> "plateNo";
            default -> "identifier";
        };
    }

    private IdentifierValue identifier(Map<String, Object> row, String channelCode) {
        String[] keys = switch (channelCode.toLowerCase()) {
            case "person" -> new String[]{"idCardNo", "id_card_no", "id_card"};
            case "mobile" -> new String[]{"phoneNo", "phone_no", "mobile"};
            case "vehicle" -> new String[]{"plateNo", "plate_no", "license_plate"};
            default -> new String[]{"identifier", "identifier_value"};
        };
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                String type = switch (channelCode.toLowerCase()) {
                    case "person" -> "ID_CARD_NO";
                    case "mobile" -> "PHONE_NO";
                    case "vehicle" -> "PLATE_NO";
                    default -> "IDENTIFIER";
                };
                return new IdentifierValue(key, type, String.valueOf(value));
            }
        }
        return null;
    }

    private void removeReserved(Map<String, Object> values) {
        for (String key : List.of("tenant_id", "tid", "id", "rule_id", "control_item_id", "controlItemId",
                "subject_type", "subjectType", "priority", "status", "rule_status", "effective_from",
                "effective_to", "created_time", "updated_time", "source_rule_no", "sourceRuleNo",
                "control_reason", "reason")) values.remove(key);
    }

    private record IdentifierValue(String column, String type, String value) {}

    private String text(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "row-" + row.hashCode();
    }

    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value == null) return null;
        try { return Instant.parse(String.valueOf(value)); } catch (Exception ignored) { return null; }
    }
}
