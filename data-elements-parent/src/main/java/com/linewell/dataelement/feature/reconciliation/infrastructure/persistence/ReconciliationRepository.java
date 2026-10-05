package com.linewell.dataelement.feature.reconciliation.infrastructure.persistence;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.FieldRule;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Plan;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Stats;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.RegisteredDataSourceResolver;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationControlMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReconciliationRepository {

    private final ReconciliationControlMapper controlMapper;
    private final ObjectMapper objectMapper;
    private final RegisteredDataSourceResolver dataSources;
    private final ReconciliationProperties properties;

    public ReconciliationRepository(
            ReconciliationControlMapper controlMapper,
            ObjectMapper objectMapper,
            RegisteredDataSourceResolver dataSources,
            ReconciliationProperties properties
    ) {
        this.controlMapper = controlMapper;
        this.objectMapper = objectMapper;
        this.dataSources = dataSources;
        this.properties = properties;
    }

    public Map<String, Object> policyPage(
            String tenantId,
            String keyword,
            int page,
            int size
    ) {
        String search = keyword == null ? "" : keyword.trim();
        IPage<Map<String, Object>> result = controlMapper.selectPolicyPage(
                Page.of(page, size),
                tenantId,
                search
        );
        return page(result.getRecords(), result.getTotal(), page, size);
    }

    public Map<String, Object> runPage(
            String tenantId,
            String status,
            String keyword,
            int page,
            int size
    ) {
        String state = status == null ? "" : status.trim();
        String search = keyword == null ? "" : keyword.trim();
        IPage<Map<String, Object>> result = controlMapper.selectRunPage(
                Page.of(page, size),
                tenantId,
                state,
                search
        );
        return page(result.getRecords(), result.getTotal(), page, size);
    }

    public Map<String, Object> diffPage(
            String tenantId,
            String runId,
            String type,
            int page,
            int size
    ) {
        String diffType = type == null ? "" : type.trim();
        IPage<Map<String, Object>> result = controlMapper.selectDiffPage(
                Page.of(page, size),
                tenantId,
                runId,
                diffType
        );
        return page(result.getRecords(), result.getTotal(), page, size);
    }

    public Map<String, Object> policyDetail(String tenantId, String policyId) {
        Map<String, Object> policy = requiredRow(
                controlMapper.selectPolicyDetail(tenantId, policyId)
        );
        policy.put("rules", ruleMaps(tenantId, policyId));
        return policy;
    }

    public Map<String, Object> runDetail(String tenantId, String runId) {
        Map<String, Object> run = requiredRow(
                controlMapper.selectRunDetail(tenantId, runId)
        );
        run.put("buckets", controlMapper.selectBuckets(tenantId, runId));
        run.put("diffSummary", controlMapper.selectDiffSummary(tenantId, runId));
        return run;
    }

    public List<Map<String, Object>> accessTasks(String tenantId) {
        return controlMapper.selectAccessTasks(tenantId);
    }

    public List<String> accessEventPolicyIds(String tenantId, String accessTaskId) {
        return controlMapper.selectAccessEventPolicyIds(tenantId, accessTaskId);
    }

    public Map<String, Object> summary(String tenantId, LocalDateTime createdSince) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyTotal", controlMapper.countPolicies(tenantId, null));
        result.put("policyEnabled", controlMapper.countPolicies(tenantId, 1));
        result.put(
                "runStatus",
                controlMapper.selectRunStatusSummary(tenantId, createdSince)
        );
        result.put("openDiff", controlMapper.countOpenDiffs(tenantId));
        return result;
    }

    @Transactional
    public String savePolicy(String tenantId, String operator, Map<String, Object> body) {
        String policyId = text(body.get("tid"));
        String accessTaskId = required(body, "accessTaskId", "请选择数据接入任务");
        Map<String, Object> task = requiredRow(
                controlMapper.selectAccessTask(tenantId, accessTaskId)
        );
        String sourceKeys = defaultText(
                text(body.get("sourceKeyFields")),
                text(task.get("source_table_primary_key"))
        );
        String targetKeys = defaultText(
                text(body.get("targetKeyFields")),
                text(task.get("target_table_primary_key"))
        );
        if (blank(sourceKeys) || blank(targetKeys)) {
            throw new IllegalArgumentException("来源表和目标表必须配置稳定的对账主键");
        }
        int fetchSize = clamp(
                integer(body.get("fetchSize"), properties.getDefaultFetchSize()),
                100,
                properties.getMaxFetchSize()
        );
        int bucketCount = clamp(
                integer(body.get("bucketCount"), 1),
                1,
                properties.getMaxBucketCount()
        );
        LocalDateTime now = LocalDateTime.now();
        String policyName = required(body, "policyName", "策略名称不能为空");
        String policyCode = defaultText(text(body.get("policyCode")), null);
        String compareMode = enumValue(
                body.get("compareMode"),
                List.of("SUMMARY", "KEY", "CONTENT"),
                "KEY"
        );
        String triggerMode = enumValue(
                body.get("triggerMode"),
                List.of("MANUAL", "CRON", "ACCESS_EVENT"),
                "MANUAL"
        );
        String cron = text(body.get("cronExpression"));
        if ("CRON".equals(triggerMode) && blank(cron)) {
            throw new IllegalArgumentException("定时策略必须配置 Cron 表达式");
        }
        if (policyId == null) {
            policyId = NumericId.nextId();
            controlMapper.insertPolicy(policyParameters(
                    policyId, tenantId, policyName, policyCode, accessTaskId,
                    compareMode, triggerMode, cron, sourceKeys, targetKeys,
                    task, body, bucketCount, fetchSize, operator, now
            ));
        } else {
            int affected = controlMapper.updatePolicy(policyParameters(
                    policyId, tenantId, policyName, policyCode, accessTaskId,
                    compareMode, triggerMode, cron, sourceKeys, targetKeys,
                    task, body, bucketCount, fetchSize, operator, now
            ));
            if (affected == 0) {
                throw new TenantAccessException("RECONCILE-POLICY-NOT-FOUND", "对账策略不存在");
            }
            controlMapper.softDeleteRules(tenantId, policyId, operator, now);
        }
        saveRules(tenantId, policyId, accessTaskId, operator, body.get("rules"));
        return policyId;
    }

    private Map<String, Object> policyParameters(
            String policyId,
            String tenantId,
            String policyName,
            String policyCode,
            String accessTaskId,
            String compareMode,
            String triggerMode,
            String cron,
            String sourceKeys,
            String targetKeys,
            Map<String, Object> task,
            Map<String, Object> body,
            int bucketCount,
            int fetchSize,
            String operator,
            LocalDateTime now
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("tid", policyId);
        policy.put("tenantId", tenantId);
        policy.put("policyName", policyName);
        policy.put("policyCode", policyCode);
        policy.put("accessTaskId", accessTaskId);
        policy.put("compareMode", compareMode);
        policy.put("triggerMode", triggerMode);
        policy.put("cronExpression", cron);
        policy.put("sourceKeyFields", sourceKeys);
        policy.put("targetKeyFields", targetKeys);
        policy.put(
                "sourceIncrementField",
                defaultText(
                        text(body.get("sourceIncrementField")),
                        text(task.get("source_table_increment_key"))
                )
        );
        policy.put("targetIncrementField", text(body.get("targetIncrementField")));
        policy.put("bucketCount", bucketCount);
        policy.put("fetchSize", fetchSize);
        policy.put("diffLimit", longValue(body.get("diffLimit"), 100_000L));
        policy.put(
                "numericTolerance",
                decimal(body.get("numericTolerance"), BigDecimal.ZERO)
        );
        policy.put("nullEqualsEmpty", bool(body.get("nullEqualsEmpty")) ? 1 : 0);
        policy.put(
                "trimStrings",
                body.containsKey("trimStrings") && !bool(body.get("trimStrings")) ? 0 : 1
        );
        policy.put("ignoreCase", bool(body.get("ignoreCase")) ? 1 : 0);
        policy.put("remark", text(body.get("remark")));
        policy.put("operator", operator);
        policy.put("now", now);
        return policy;
    }

    private void saveRules(
            String tenantId,
            String policyId,
            String accessTaskId,
            String operator,
            Object submittedRules
    ) {
        List<Map<String, Object>> rules;
        if (submittedRules instanceof List<?> list && !list.isEmpty()) {
            rules = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    rules.add(copy);
                }
            }
        } else {
            rules = controlMapper.selectAccessFieldMappings(tenantId, accessTaskId);
        }
        LocalDateTime now = LocalDateTime.now();
        int sort = 0;
        for (Map<String, Object> rule : rules) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tid", NumericId.nextId());
            params.put("tenantId", tenantId);
            params.put("policyId", policyId);
            params.put("sourceField", first(rule, "sourceField", "source_field"));
            params.put("targetField", first(rule, "targetField", "target_field"));
            params.put("sourceDataType", first(rule, "sourceDataType", "source_data_type"));
            params.put("targetDataType", first(rule, "targetDataType", "target_data_type"));
            params.put(
                    "compareEnabled",
                    rule.containsKey("compareEnabled") && !bool(rule.get("compareEnabled"))
                            ? 0 : 1
            );
            params.put("normalization", defaultText(first(rule, "normalization"), "DEFAULT"));
            params.put(
                    "numericTolerance",
                    decimal(firstObject(rule, "numericTolerance", "numeric_tolerance"), null)
            );
            params.put("datePrecision", first(rule, "datePrecision", "date_precision"));
            params.put(
                    "nullEqualsEmpty",
                    rule.containsKey("nullEqualsEmpty")
                            ? (bool(rule.get("nullEqualsEmpty")) ? 1 : 0)
                            : null
            );
            params.put("maskRule", defaultText(first(rule, "maskRule", "mask_rule"), "PARTIAL"));
            params.put("sortNo", integer(firstObject(rule, "sortNo", "sort_no"), sort++));
            params.put("operator", operator);
            params.put("now", now);
            controlMapper.insertRule(params);
        }
    }

    public Plan loadPlan(String tenantId, String runId) {
        Map<String, Object> row = requiredRow(
                controlMapper.selectRunPlan(tenantId, runId)
        );
        String policyId = text(row.get("policy_id"));
        return new Plan(
                tenantId,
                runId,
                policyId,
                text(row.get("compare_mode")),
                fields(row.get("source_key_fields")),
                fields(row.get("target_key_fields")),
                text(row.get("source_increment_field")),
                text(row.get("target_increment_field")),
                text(row.get("lower_watermark")),
                text(row.get("upper_watermark")),
                integer(row.get("fetch_size"), properties.getDefaultFetchSize()),
                longValue(row.get("diff_limit"), 100_000L),
                decimal(row.get("numeric_tolerance"), BigDecimal.ZERO),
                bool(row.get("null_equals_empty")),
                bool(row.get("trim_strings")),
                bool(row.get("ignore_case")),
                dataSources.resolve(
                        tenantId,
                        text(row.get("source_db_id")),
                        text(row.get("source_table_id"))
                ),
                dataSources.resolve(
                        tenantId,
                        text(row.get("target_db_id")),
                        text(row.get("target_table_id"))
                ),
                rules(tenantId, policyId)
        );
    }

    public Plan loadPolicyPlan(String tenantId, String policyId) {
        String syntheticRunId = "precheck-" + policyId;
        Map<String, Object> policy = requiredRow(
                controlMapper.selectPolicyPlan(tenantId, policyId)
        );
        return new Plan(
                tenantId,
                syntheticRunId,
                policyId,
                text(policy.get("compare_mode")),
                fields(policy.get("source_key_fields")),
                fields(policy.get("target_key_fields")),
                text(policy.get("source_increment_field")),
                text(policy.get("target_increment_field")),
                null,
                null,
                integer(policy.get("fetch_size"), properties.getDefaultFetchSize()),
                longValue(policy.get("diff_limit"), 100_000L),
                decimal(policy.get("numeric_tolerance"), BigDecimal.ZERO),
                bool(policy.get("null_equals_empty")),
                bool(policy.get("trim_strings")),
                bool(policy.get("ignore_case")),
                dataSources.resolve(
                        tenantId,
                        text(policy.get("source_db_id")),
                        text(policy.get("source_table_id"))
                ),
                dataSources.resolve(
                        tenantId,
                        text(policy.get("target_db_id")),
                        text(policy.get("target_table_id"))
                ),
                rules(tenantId, policyId)
        );
    }

    public void savePrecheck(String tenantId, String policyId, Map<String, Object> result) {
        boolean success = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenantId);
        params.put("policyId", policyId);
        params.put("status", success ? "PASSED" : "FAILED");
        params.put("message", text(result.get("message")));
        params.put("detail", json(result));
        params.put("now", LocalDateTime.now());
        controlMapper.updatePrecheck(params);
    }

    public void enablePolicy(String tenantId, String policyId, boolean enabled) {
        String precheckStatus = controlMapper.selectPrecheckStatus(tenantId, policyId);
        if (precheckStatus == null) {
            throw new TenantAccessException(
                    "RECONCILE-NOT-FOUND",
                    "对账数据不存在或无权访问"
            );
        }
        if (enabled && !"PASSED".equals(precheckStatus)) {
            throw new IllegalStateException("策略启用前必须通过连通性和字段预检");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenantId);
        params.put("policyId", policyId);
        params.put("status", enabled ? 1 : 2);
        params.put("enabled", enabled ? 1 : 0);
        params.put("now", LocalDateTime.now());
        controlMapper.updatePolicyEnabled(params);
    }

    public String latestSuccessfulUpperWatermark(String tenantId, String policyId) {
        return controlMapper.selectLatestSuccessfulUpperWatermark(tenantId, policyId);
    }

    public String createRun(
            String tenantId,
            String policyId,
            String triggerType,
            String accessRunId,
            String lower,
            String upper,
            String requestId,
            String operator
    ) {
        Map<String, Object> policy = policyDetail(tenantId, policyId);
        if ("CRON".equals(triggerType) || "ACCESS_EVENT".equals(triggerType)) {
            if (integer(policy.get("status"), 0) != 1) {
                throw new IllegalStateException("对账策略未启用");
            }
        }
        String dedupeSource = tenantId + "|" + policyId + "|" + triggerType
                + "|" + defaultText(accessRunId, "")
                + "|" + defaultText(lower, "")
                + "|" + defaultText(upper, "")
                + "|" + defaultText(requestId, "");
        String dedupeKey = sha256(dedupeSource);
        String runId = NumericId.nextId();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tid", runId);
        run.put("tenantId", tenantId);
        run.put("policyId", policyId);
        run.put("policyVersion", integer(policy.get("version"), 1));
        run.put("policySnapshot", json(policy));
        run.put("accessRunId", accessRunId);
        run.put("triggerType", triggerType);
        run.put("lowerWatermark", lower);
        run.put("upperWatermark", upper);
        run.put("dedupeKey", dedupeKey);
        run.put("operator", operator);
        run.put("now", now);
        try {
            controlMapper.insertRun(run);
        } catch (DuplicateKeyException duplicate) {
            return controlMapper.selectRunIdByDedupe(tenantId, dedupeKey);
        }
        controlMapper.touchPolicyLastRun(tenantId, policyId, now);
        return runId;
    }

    public void requestCancel(String tenantId, String runId) {
        int affected = controlMapper.requestCancel(
                tenantId,
                runId,
                LocalDateTime.now()
        );
        if (affected == 0) {
            throw new IllegalStateException("当前对账实例不存在或已经结束");
        }
    }

    public List<Map<String, Object>> duePolicies() {
        Page<Map<String, Object>> page = Page.of(1, 100, false);
        return controlMapper.selectDuePolicies(page).getRecords();
    }

    public void updateNextRun(String tenantId, String policyId, LocalDateTime next) {
        controlMapper.updateNextRun(tenantId, policyId, next);
    }

    private List<FieldRule> rules(String tenantId, String policyId) {
        return ruleMaps(tenantId, policyId).stream()
                .map(row -> new FieldRule(
                        text(row.get("source_field")),
                        text(row.get("target_field")),
                        text(row.get("source_data_type")),
                        text(row.get("target_data_type")),
                        bool(row.get("compare_enabled")),
                        text(row.get("normalization")),
                        decimal(row.get("numeric_tolerance"), null),
                        text(row.get("date_precision")),
                        row.get("null_equals_empty") == null
                                ? null
                                : bool(row.get("null_equals_empty")),
                        text(row.get("mask_rule"))
                ))
                .toList();
    }

    private List<Map<String, Object>> ruleMaps(String tenantId, String policyId) {
        return controlMapper.selectRules(tenantId, policyId);
    }

    private Map<String, Object> requiredRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            throw new TenantAccessException("RECONCILE-NOT-FOUND", "对账数据不存在或无权访问");
        }
        return new LinkedHashMap<>(row);
    }

    private Map<String, Object> page(
            List<Map<String, Object>> list,
            long total,
            int page,
            int size
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private List<String> fields(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(field -> !field.isEmpty())
                .toList();
    }

    private String enumValue(
            Object value,
            List<String> allowed,
            String fallback
    ) {
        String normalized = defaultText(text(value), fallback).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("不支持的枚举值: " + normalized);
        }
        return normalized;
    }

    private String required(Map<String, Object> body, String key, String message) {
        String value = text(body.get(key));
        if (blank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String first(Map<String, Object> row, String... keys) {
        return text(firstObject(row, keys));
    }

    private Object firstObject(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化对账配置失败", e);
        }
    }

    public List<Object> jsonList(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<List<Object>>() {
                    }
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    public String toJson(Object value) {
        return json(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value);
    }

    private String defaultText(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return blank(text(value)) ? fallback : Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return blank(text(value)) ? fallback : Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null || blank(text(value))) {
            return fallback;
        }
        try {
            return new BigDecimal(text(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = text(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
