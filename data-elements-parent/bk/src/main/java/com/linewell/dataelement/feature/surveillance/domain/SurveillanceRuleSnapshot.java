package com.linewell.dataelement.feature.surveillance.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A versioned snapshot containing matching definitions and control items. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveillanceRuleSnapshot(
        String engineCode,
        String channelCode,
        long version,
        Instant generatedAt,
        Instant expiresAt,
        String digest,
        String sourceType,
        String sourceRef,
        String status,
        List<RuleDefinition> ruleDefinitions,
        List<ControlItem> controlItems,
        @Deprecated List<Rule> rules
) {
    public SurveillanceRuleSnapshot {
        status = status == null || status.isBlank() ? "ACTIVE" : status;
        ruleDefinitions = ruleDefinitions == null ? List.of() : List.copyOf(ruleDefinitions);
        controlItems = controlItems == null ? List.of() : List.copyOf(controlItems);
        long activeDefinitionCount = ruleDefinitions.stream()
                .filter(definition -> "ACTIVE".equalsIgnoreCase(definition.status()))
                .count();
        if (activeDefinitionCount > 1) {
            throw new IllegalArgumentException("一期每个通道只能有一个 ACTIVE 规则定义");
        }
        rules = rules == null ? compatibilityRules(ruleDefinitions, controlItems) : List.copyOf(rules);
    }

    /** Compatibility constructor for the original rules[] contract. */
    public SurveillanceRuleSnapshot(
            String engineCode, String channelCode, long version,
            Instant generatedAt, Instant expiresAt, String digest,
            String sourceType, String sourceRef, String status,
            List<RuleDefinition> ruleDefinitions, List<ControlItem> controlItems
    ) {
        this(engineCode, channelCode, version, generatedAt, expiresAt, digest,
                sourceType, sourceRef, status, ruleDefinitions, controlItems, null);
    }

    /** Compatibility constructor for the original rules[] contract. */
    public SurveillanceRuleSnapshot(
            String engineCode, String channelCode, long version,
            Instant generatedAt, Instant expiresAt, String digest,
            String sourceType, String sourceRef, String status, List<Rule> rules
    ) {
        this(engineCode, channelCode, version, generatedAt, expiresAt, digest,
                sourceType, sourceRef, status, List.of(), List.of(), rules);
    }

    public boolean usesNormalizedModel() {
        return !ruleDefinitions.isEmpty() || !controlItems.isEmpty();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleDefinition(
            String ruleCode,
            String ruleName,
            String subjectType,
            String matchMode,
            List<String> matchFields,
            int priority,
            String status,
            Instant effectiveFrom,
            Instant effectiveTo,
            String sourceSystem,
            String reason,
            Map<String, Object> metadata
    ) {
        public RuleDefinition {
            matchMode = matchMode == null || matchMode.isBlank() ? "AND" : matchMode;
            matchFields = matchFields == null ? List.of() : List.copyOf(matchFields);
            status = status == null || status.isBlank() ? "ACTIVE" : status;
            metadata = immutableMap(metadata);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlItem(
            String controlItemId,
            String ruleCode,
            String subjectType,
            List<Identifier> identifiers,
            Map<String, Object> attributes,
            String sourceRuleNo,
            String controlReason,
            Instant effectiveFrom,
            Instant effectiveTo,
            String status,
            Map<String, Object> metadata
    ) {
        public ControlItem {
            identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
            attributes = immutableMap(attributes);
            metadata = immutableMap(metadata);
            status = status == null || status.isBlank() ? "ACTIVE" : status;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Identifier(String type, String value) {
    }

    /** Legacy shape retained for old snapshots and source compatibility. */
    @Deprecated
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rule(
            String ruleId,
            String ruleCode,
            String controlItemId,
            String subjectType,
            int priority,
            String status,
            Instant effectiveFrom,
            Instant effectiveTo,
            Map<String, Object> match,
            Map<String, Object> metadata
    ) {
        public Rule(
                String ruleId, String subjectType, int priority, String status,
                Instant effectiveFrom, Instant effectiveTo,
                Map<String, Object> match, Map<String, Object> metadata
        ) {
            this(ruleId, ruleId, ruleId, subjectType, priority, status,
                    effectiveFrom, effectiveTo, match, metadata);
        }

        public Rule {
            match = immutableMap(match);
            metadata = immutableMap(metadata);
            status = status == null || status.isBlank() ? "ACTIVE" : status;
        }
    }

    private static List<Rule> compatibilityRules(
            List<RuleDefinition> definitions,
            List<ControlItem> items
    ) {
        if (definitions == null || definitions.isEmpty() || items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, RuleDefinition> byCode = new LinkedHashMap<>();
        for (RuleDefinition definition : definitions) {
            if (definition.ruleCode() != null) {
                byCode.put(definition.ruleCode(), definition);
            }
        }
        List<Rule> result = new java.util.ArrayList<>();
        for (ControlItem item : items) {
            RuleDefinition definition = byCode.get(item.ruleCode());
            if (definition == null) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Identifier identifier : item.identifiers()) {
                String field = identifierField(identifier.type());
                if (field != null) {
                    values.put(field, identifier.value());
                }
            }
            values.putAll(item.attributes());
            Map<String, Object> match = new LinkedHashMap<>();
            if (definition.matchFields().isEmpty()) {
                match.putAll(values);
            } else {
                for (String field : definition.matchFields()) {
                    if (values.containsKey(field)) {
                        match.put(field, values.get(field));
                    }
                }
            }
            result.add(new Rule(
                    item.controlItemId(), definition.ruleCode(), item.controlItemId(),
                    item.subjectType(), definition.priority(), item.status(),
                    item.effectiveFrom() == null ? definition.effectiveFrom() : item.effectiveFrom(),
                    item.effectiveTo() == null ? definition.effectiveTo() : item.effectiveTo(),
                    match, item.metadata()
            ));
        }
        return List.copyOf(result);
    }

    private static String identifierField(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "ID_CARD_NO", "IDCARDNO" -> "idCardNo";
            case "PHONE_NO", "MOBILE_NO", "PHONENO" -> "phoneNo";
            case "PLATE_NO", "LICENSE_PLATE", "PLATENO" -> "plateNo";
            default -> type;
        };
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
