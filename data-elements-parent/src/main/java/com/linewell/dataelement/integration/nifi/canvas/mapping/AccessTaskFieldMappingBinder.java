package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessFieldMapping;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessFieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Applies the field rules saved during data-access registration to the canvas DSL.
 *
 * <p>The registration workflow owns dictionary, mandatory-standard and ODS system
 * field rules in {@code data_access_field_mapping}.  A canvas generated only from
 * physical column names loses that semantic information and silently writes null
 * values for the generated {@code *_cn} columns.  Binding at deployment time keeps
 * both the legacy field-mapping node and the unified field-enrichment node aligned
 * with the registered task, including canvases that were created before this
 * binding existed.</p>
 */
@Service
public class AccessTaskFieldMappingBinder {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ODS_UUID_EXPRESSION =
            "CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) "
                    + "|| CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)";
    /**
     * Older registration drafts sometimes kept the dictionary selected in the
     * user-facing field title but lost {@code dict_enable/dict_value/dict_code}
     * while serializing the access-task mapping.  The marker is intentionally
     * strict: it accepts only the page-generated “【字典关联】table.column” form.
     */
    private static final Pattern DICTIONARY_MARKER = Pattern.compile(
            "【字典关联】([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");

    private final IDataAccessAggTaskTService taskService;
    private final IDataAccessFieldMappingService fieldMappingService;

    public AccessTaskFieldMappingBinder(IDataAccessAggTaskTService taskService,
                                        IDataAccessFieldMappingService fieldMappingService) {
        this.taskService = taskService;
        this.fieldMappingService = fieldMappingService;
    }

    /**
     * Returns the original pipeline when it already contains every registered
     * governed rule.  This deliberately preserves user-authored canvas rules
     * after the required registration semantics have been materialized once.
     */
    public Pipeline bind(Pipeline pipeline) {
        if (pipeline == null || pipeline.id() == null || pipeline.dsl() == null
                || pipeline.dsl().nodes() == null) {
            return pipeline;
        }
        DataAccessAggTaskT task = taskService.findByPipelineId(pipeline.id());
        if (task == null || isBlank(task.getTid())) {
            return pipeline;
        }
        List<DataAccessFieldMapping> rules = fieldMappingService.list(
                new LambdaQueryWrapper<DataAccessFieldMapping>()
                        .eq(DataAccessFieldMapping::getTaskId, task.getTid())
                        .eq(DataAccessFieldMapping::getIsDel, 0)
                        .orderByAsc(DataAccessFieldMapping::getSortNo)
                        .orderByAsc(DataAccessFieldMapping::getCreatedTime));
        if (rules == null || rules.isEmpty()) {
            return pipeline;
        }

        // A legacy registration could retain only the generated dictionary title
        // (for example, “【字典关联】dict_gender.gender_code”) while losing the
        // connection contract.  The lookup is then still local to the source
        // database, so use the source node's resolved connection as the
        // compatibility fallback.  Without it the SQL compiler falls back to
        // the ANSI dialect and generates invalid MySQL aliases.
        ObjectNode sourceLookupDataSource = sourceLookupDataSource(pipeline);
        boolean changed = false;
        List<Pipeline.Node> nodes = new java.util.ArrayList<>();
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (!isFieldMappingNode(node)) {
                nodes.add(node);
                continue;
            }
            Object raw = node.config() == null ? null : node.config().get("mappings");
            ObjectNode existing = existingSpec(raw);
            if (existing != null && !needsBinding(existing.path("mappings"), rules, sourceLookupDataSource)) {
                nodes.add(node);
                continue;
            }
            Map<String, Object> config = new LinkedHashMap<>(node.config() == null ? Map.of() : node.config());
            config.put("mappings", buildSpec(existing, rules, sourceLookupDataSource));
            nodes.add(new Pipeline.Node(node.id(), node.manifestKey(), node.label(), node.category(),
                    node.x(), node.y(), config));
            changed = true;
        }
        if (!changed) {
            return pipeline;
        }
        return new Pipeline(pipeline.id(), pipeline.name(), pipeline.description(), pipeline.createdAt(),
                pipeline.updatedAt(), new Pipeline.Dsl(pipeline.dsl().version(), List.copyOf(nodes), pipeline.dsl().edges()),
                pipeline.nifiProcessGroupId(), pipeline.status(), pipeline.lastDeployedHash(),
                pipeline.lastDeployedAt(), pipeline.lastStoppedAt(), pipeline.nodeMapping(), pipeline.lastBulletinId());
    }

    private boolean needsBinding(JsonNode mappings, List<DataAccessFieldMapping> rules,
                                 ObjectNode sourceLookupDataSource) {
        if (!mappings.isArray()) {
            return true;
        }
        for (DataAccessFieldMapping rule : rules) {
            JsonNode existing = mappingForTarget(mappings, rule.getTargetField());
            if (needsRuleRepair(existing, rule, sourceLookupDataSource)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsRuleRepair(JsonNode existing, DataAccessFieldMapping rule,
                                    ObjectNode sourceLookupDataSource) {
        if (isBlank(rule.getTargetField())) return false;
        // Only the known historical marker-only raw-code lookup is invalid.
        // An ordinary registered field may have a legitimate manually authored
        // lookup; a later ODS repair must not silently replace that user rule.
        if (existing != null && existing.has("lookup") && !isLookup(rule)
                && trim(rule.getSourceField()).equalsIgnoreCase(trim(rule.getTargetField()))
                && DICTIONARY_MARKER.matcher(trim(rule.getSourceFieldCn())).find()
                && !existing.path("lookup").path("dataSource").isObject()) return true;
        if (!requiresGeneratedRule(rule)) return false;
        if (existing == null) return true;
        if (isLookup(rule)) {
            LookupDefinition definition = lookupDefinition(rule);
            boolean hasRegisteredConnection = definition != null && definition.config() != null
                    && definition.config().path("dataSource").isObject();
            if (!existing.has("lookup") || ((sourceLookupDataSource != null || hasRegisteredConnection)
                    && !existing.path("lookup").path("dataSource").isObject())) return true;
            if (definition != null && definition.config() != null
                    && definition.config().path("multiValue").asBoolean(false)
                    != existing.path("lookup").path("multiValue").asBoolean(false)) return true;
            if (definition != null && definition.config() != null
                    && definition.config().path("multiValue").asBoolean(false)
                    && !MultiValueTranslation.isRecordLookup(existing.path("lookup"))) return true;
        }
        if (isEnum(rule)) {
            if (isMultiStandard(rule)) return !existing.path("lookup").path("values").equals(
                    JSON.valueToTree(enumValues(rule)))
                    || !existing.path("lookup").path("multiValue").asBoolean(false);
            if (!hasCurrentEnumMap(existing, rule)) return true;
        }
        return isExpression(rule) && !existing.hasNonNull("expression");
    }

    /**
     * Enum rules belong to the registration contract.  Compare the generated
     * values as well as the function name so a later compatibility addition is
     * propagated to an already-created canvas instead of retaining an obsolete
     * fallback-only enum map forever.
     */
    private boolean hasCurrentEnumMap(JsonNode existing, DataAccessFieldMapping rule) {
        if (!"enumMap".equals(existing.path("transform").path("fn").asText())) {
            return false;
        }
        JsonNode values = existing.path("transform").path("args").path(0);
        if (!values.isObject()) {
            return false;
        }
        for (Map.Entry<String, String> expected : enumValues(rule).entrySet()) {
            if (!expected.getValue().equals(values.path(expected.getKey()).asText())) {
                return false;
            }
        }
        return true;
    }

    private ObjectNode existingSpec(Object raw) {
        if (raw == null || (raw instanceof String text && text.isBlank())) return null;
        try {
            JsonNode spec = raw instanceof String text ? JSON.readTree(text) : JSON.valueToTree(raw);
            if (spec == null || spec.isNull()) return null;
            if (!spec.isObject() || !spec.path("mappings").isArray()) {
                throw new IllegalArgumentException("mappings 必须是数组");
            }
            return ((ObjectNode) spec).deepCopy();
        } catch (Exception exception) {
            // Invalid persisted user content is not a request to erase it.
            throw new IllegalStateException("已保存的字段映射格式无效，请修正后再部署；未覆盖原有规则", exception);
        }
    }

    private JsonNode mappingForTarget(JsonNode mappings, String target) {
        if (isBlank(target)) {
            return null;
        }
        for (JsonNode item : mappings) {
            if (target.equalsIgnoreCase(unpath(item.path("to").asText()))) {
                return item;
            }
        }
        return null;
    }

    private String buildSpec(ObjectNode existing, List<DataAccessFieldMapping> rules,
                             ObjectNode sourceLookupDataSource) {
        ObjectNode spec = existing == null ? JSON.createObjectNode() : existing.deepCopy();
        if (existing == null) {
            spec.put("version", "1.0");
            spec.put("passthroughUnmapped", false);
            spec.put("passthroughCaseSensitive", true);
            spec.put("onMissingSource", "NULL");
            spec.put("onTypeMismatch", "CAST");
            spec.put("defaultLocale", "zh-CN");
            spec.putArray("mappings");
        }
        ArrayNode mappings = (ArrayNode) spec.path("mappings");
        for (DataAccessFieldMapping rule : rules) {
            JsonNode previous = mappingForTarget(mappings, rule.getTargetField());
            if (isBlank(rule.getTargetField()) || (existing != null
                    && !needsRuleRepair(previous, rule, sourceLookupDataSource))) {
                continue;
            }
            ObjectNode mapping = JSON.createObjectNode();
            mapping.put("to", path(rule.getTargetField()));
            if (isExpression(rule)) {
                mapping.put("expression", requiredExpression(rule));
            } else {
                if (isBlank(rule.getSourceField())) {
                    continue;
                }
                mapping.put("from", path(rule.getSourceField()));
                if (isLookup(rule)) {
                    appendLookup(mapping, rule, sourceLookupDataSource);
                } else if (isEnum(rule)) {
                    if (isMultiStandard(rule)) appendMultiStandardLookup(mapping, rule, sourceLookupDataSource);
                    else appendEnumMap(mapping, rule);
                }
            }
            if (previous == null) {
                mappings.add(mapping);
            } else {
                for (int index = 0; index < mappings.size(); index++) {
                    if (mappings.get(index) == previous) {
                        mappings.set(index, mapping);
                        break;
                    }
                }
            }
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成接入任务字段映射: " + exception.getMessage(), exception);
        }
    }

    private void appendLookup(ObjectNode mapping, DataAccessFieldMapping rule, ObjectNode sourceLookupDataSource) {
        LookupDefinition definition = lookupDefinition(rule);
        if (definition == null) {
            throw new IllegalStateException("字典关联字段 " + rule.getTargetField()
                    + " 缺少字典表、编码字段或名称字段配置");
        }
        String table = requiredIdentifier(definition.table(), "字典表");
        String source = requiredIdentifier(definition.codeField(), "字典编码字段");
        String result = requiredIdentifier(definition.labelField(), "字典名称字段");
        String target = requiredIdentifier(rule.getTargetField(), "目标字段");
        ObjectNode lookup = mapping.putObject("lookup");
        String configuredSql = definition.config() == null ? "" : definition.config().path("sql").asText("").trim();
        lookup.put("sql", configuredSql.isBlank()
                ? "SELECT " + result + " AS " + target + " FROM " + table + " WHERE " + source + " = ?"
                : configuredSql);
        lookup.put("resultColumn", target);
        if (definition.config() != null && definition.config().has("dataSource")
                && definition.config().path("dataSource").isObject()) {
            lookup.set("dataSource", definition.config().path("dataSource").deepCopy());
        } else if (sourceLookupDataSource != null) {
            lookup.set("dataSource", sourceLookupDataSource.deepCopy());
        }
        lookup.put("onMissing", "NULL");
        if (definition.config() != null && definition.config().path("multiValue").asBoolean(false)) {
            lookup.put("multiValue", true);
            lookup.put("multiValueSeparator", definition.config().path("multiValueSeparator").asText(","));
            String query = definition.config().path("query").asText("");
            lookup.put("query", query.isBlank()
                    ? MultiValueTranslation.legacyDictionaryQuery(configuredSql, table, source, result)
                    : query);
            // Old generated JSON_TABLE SQL is never executed as a single-value lookup.
            lookup.remove("sql");
        }
    }

    /**
     * Builds the lookup connection contract from the source node for old
     * registration rows that predate persisted dictionary datasource metadata.
     * The values are copied from an already resolved canvas node rather than
     * guessed from a field name, so tenant-specific connection settings and
     * credential references continue to be honoured.
     */
    private ObjectNode sourceLookupDataSource(Pipeline pipeline) {
        if (pipeline == null || pipeline.dsl() == null || pipeline.dsl().nodes() == null) {
            return null;
        }
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (node == null || node.manifestKey() == null || !node.manifestKey().startsWith("source.")) {
                continue;
            }
            Map<String, Object> config = node.config();
            if (config == null || config.isEmpty()) {
                continue;
            }
            String dbType = firstNonBlank(value(config, "dbType"),
                    node.manifestKey().substring("source.".length()));
            if (dbType.isBlank()) {
                continue;
            }
            ObjectNode dataSource = JSON.createObjectNode();
            putIfPresent(dataSource, "datasourceId", firstNonBlank(value(config, "datasourceId"),
                    value(config, "sourceDbId"), value(config, "dbId")));
            dataSource.put("dbType", dbType);
            putIfPresent(dataSource, "host", value(config, "host"));
            putIfPresent(dataSource, "port", value(config, "port"));
            putIfPresent(dataSource, "database", firstNonBlank(value(config, "database"), value(config, "dbName")));
            putIfPresent(dataSource, "jdbcUrl", value(config, "jdbcUrl"));
            putIfPresent(dataSource, "username", value(config, "username"));
            putIfPresent(dataSource, "password", value(config, "password"));
            putIfPresent(dataSource, "defaultSchema", value(config, "defaultSchema"));
            return dataSource;
        }
        return null;
    }

    private String value(Map<String, Object> config, String key) {
        if (config == null || key == null) return "";
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return trim(value);
        }
        return "";
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (!isBlank(value)) node.put(field, trim(value));
    }

    private LookupDefinition lookupDefinition(DataAccessFieldMapping rule) {
        String executable = trim(rule.getFuncValue());
        if (executable.startsWith("{")) {
            try {
                JsonNode config = JSON.readTree(executable);
                if ("DICTIONARY".equalsIgnoreCase(config.path("ruleKind").asText())) {
                    String table = trim(config.path("table").asText());
                    String codeField = trim(config.path("keyField").asText());
                    String labelField = trim(config.path("labelField").asText());
                    if (!table.isBlank() && !codeField.isBlank() && !labelField.isBlank()) {
                        return new LookupDefinition(table, codeField, labelField, config);
                    }
                }
            } catch (Exception ignored) {
                // Fall through to the legacy columns.
            }
        }
        String table = trim(rule.getDictValue());
        String config = trim(rule.getDictCode());
        if (!table.isBlank() && !config.isBlank()) {
            String[] fields = splitPair(config, "字典字段配置");
            return new LookupDefinition(table, fields[0], fields[1], null);
        }

        // Compatibility repair for registrations written before dictionary
        // metadata was fully serialized.  The source-field title is generated
        // from the selected dictionary and therefore remains a trustworthy
        // reference.  It applies only to a separate translation target.  The
        // raw source-to-same-name mapping must stay a pass-through; otherwise
        // a reopened canvas creates an extra lookup without datasource config.
        if (trim(rule.getSourceField()).equalsIgnoreCase(trim(rule.getTargetField()))) {
            return null;
        }
        Matcher matcher = DICTIONARY_MARKER.matcher(trim(rule.getSourceFieldCn()));
        if (!matcher.find()) {
            return null;
        }
        String codeField = matcher.group(2);
        String labelField = inferredLabelField(codeField);
        return labelField == null ? null : new LookupDefinition(matcher.group(1), codeField, labelField, null);
    }

    private String inferredLabelField(String codeField) {
        if (codeField.endsWith("_code")) {
            return codeField.substring(0, codeField.length() - "_code".length()) + "_name";
        }
        if (codeField.endsWith("_id")) {
            return codeField.substring(0, codeField.length() - "_id".length()) + "_name";
        }
        return null;
    }

    private void appendEnumMap(ObjectNode mapping, DataAccessFieldMapping rule) {
        Map<String, String> values = enumValues(rule);
        if (values.isEmpty()) {
            throw new IllegalStateException("统一格式字段 " + rule.getTargetField()
                    + " 未保存可执行的代码映射，请在登记配置中补充枚举值后重新部署");
        }
        ObjectNode transform = mapping.putObject("transform");
        transform.put("fn", "enumMap");
        ArrayNode args = transform.putArray("args");
        ObjectNode enumMap = args.addObject();
        values.forEach(enumMap::put);
        // Preserve the non-null contract while making an unregistered source
        // code visible to governance follow-up instead of silently persisting
        // a null into the generated display column.
        args.add("未映射");
    }

    private boolean isMultiStandard(DataAccessFieldMapping rule) {
        try {
            JsonNode config = JSON.readTree(trim(rule.getFuncValue()));
            return "MANDATORY_STANDARD".equalsIgnoreCase(config.path("ruleKind").asText())
                    && config.path("multiValue").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void appendMultiStandardLookup(ObjectNode mapping, DataAccessFieldMapping rule,
                                           ObjectNode sourceLookupDataSource) {
        JsonNode config;
        try {
            config = JSON.readTree(trim(rule.getFuncValue()));
        } catch (Exception exception) {
            throw new IllegalStateException("多值标准翻译配置无效", exception);
        }
        ObjectNode lookup = mapping.putObject("lookup");
        lookup.set("values", JSON.valueToTree(enumValues(rule)));
        lookup.put("multiValueSeparator", config.path("multiValueSeparator").asText(","));
        lookup.put("resultColumn", rule.getTargetField());
        lookup.put("onMissing", "NULL");
        lookup.put("multiValue", true);
    }

    private Map<String, String> enumValues(DataAccessFieldMapping rule) {
        Map<String, String> result = new LinkedHashMap<>();
        String configured = trim(rule.getFuncValue());
        if (configured.startsWith("{")) {
            try {
                JsonNode values = JSON.readTree(configured);
                JsonNode mappingValues = values.path("values");
                if (mappingValues.isObject()) values = mappingValues;
                if (values.isObject() && !values.has("ruleKind")) {
                    Map<String, String> configuredValues = new LinkedHashMap<>();
                    values.fields().forEachRemaining(entry ->
                            configuredValues.put(entry.getKey(), entry.getValue().asText()));
                    result.putAll(configuredValues);
                }
            } catch (Exception ignored) {
                // Fall through to the registered standard profile below.
            }
        }
        if (result.isEmpty() && "MANDATORY_STANDARD".equalsIgnoreCase(configured)) {
            String field = normalizedRuleField(rule);
            if (field.contains("certificate") || field.contains("cert_type")) {
                result.putAll(Map.of("IDCARD", "身份证", "PASSPORT", "护照", "HKM_PERMIT", "港澳居民来往内地通行证", "OTHER", "其他"));
            } else if (field.contains("marital") || field.contains("marriage")) {
                result.putAll(Map.of("MARRIED", "已婚", "SINGLE", "未婚", "DIVORCED", "离异", "WIDOWED", "丧偶", "UNKNOWN", "未知"));
            }
        }
        return addLegacySourceAliases(rule, result);
    }

    /**
     * Some source systems use readable legacy codes while the selected national
     * standard stores numeric codes.  Keep the registered numeric mapping and
     * add only well-known aliases whose display label already exists in that
     * mapping.  This prevents a valid value such as IDCARD or MARRIED from
     * being sent to the deliberately visible "未映射" fallback.
     */
    private Map<String, String> addLegacySourceAliases(DataAccessFieldMapping rule, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>(values);
        String field = normalizedRuleField(rule);
        if (field.contains("certificate") || field.contains("cert_type")) {
            addAliasForLabel(result, "IDCARD", "居民身份证", "身份证");
            addAliasForLabel(result, "PASSPORT", "普通护照", "护照");
            addAliasForLabel(result, "HKM_PERMIT", "港澳居民来往内地通行证");
            addAliasForLabel(result, "OTHER", "其他");
        } else if (field.contains("marital") || field.contains("marriage")) {
            addAliasForLabel(result, "MARRIED", "已婚");
            addAliasForLabel(result, "SINGLE", "未婚");
            addAliasForLabel(result, "DIVORCED", "离婚", "离异");
            addAliasForLabel(result, "WIDOWED", "丧偶");
            addAliasForLabel(result, "UNKNOWN", "未知", "其他");
        }
        return result;
    }

    private String normalizedRuleField(DataAccessFieldMapping rule) {
        return (trim(rule.getSourceField()) + " " + trim(rule.getTargetField())).toLowerCase(Locale.ROOT);
    }

    private void addAliasForLabel(Map<String, String> values, String alias, String... labels) {
        if (values.containsKey(alias)) {
            return;
        }
        for (String label : labels) {
            for (String value : values.values()) {
                if (label.equals(value)) {
                    values.put(alias, value);
                    return;
                }
            }
        }
    }

    private String requiredExpression(DataAccessFieldMapping rule) {
        String configured = trim(rule.getFuncValue());
        if (!configured.isBlank()) {
            return configured;
        }
        return switch (trim(rule.getTargetField()).toUpperCase(Locale.ROOT)) {
            case "ODS_RKSJ", "ODS_GXSJ" -> "LOCALTIMESTAMP";
            case "ODS_UUID" -> ODS_UUID_EXPRESSION;
            default -> throw new IllegalStateException("系统字段 " + rule.getTargetField() + " 未配置表达式");
        };
    }

    private boolean isFieldMappingNode(Pipeline.Node node) {
        return node != null && ("transform.field-mapping".equals(node.manifestKey())
                || "transform.field-enrichment".equals(node.manifestKey()));
    }

    private boolean requiresGeneratedRule(DataAccessFieldMapping rule) {
        return isLookup(rule) || isEnum(rule) || isExpression(rule);
    }

    private boolean isLookup(DataAccessFieldMapping rule) {
        return truthy(rule.getDictEnable()) || "LOOKUP".equalsIgnoreCase(trim(rule.getFuncCode()))
                || lookupDefinition(rule) != null;
    }

    private boolean isEnum(DataAccessFieldMapping rule) {
        // Registration may retain the original enum marker after a field has
        // been promoted to a dictionary translation.  The dictionary is the
        // authoritative, maintainable description source and must win over
        // that legacy marker.
        return !isLookup(rule) && "ENUM_MAP".equalsIgnoreCase(trim(rule.getFuncCode()));
    }

    private boolean isExpression(DataAccessFieldMapping rule) {
        return "EXPRESSION".equalsIgnoreCase(trim(rule.getFuncCode()))
                || trim(rule.getSourceField()).toUpperCase(Locale.ROOT).startsWith("SYSTEM:");
    }

    private boolean truthy(Integer value) {
        return value != null && value != 0;
    }

    private String[] splitPair(String value, String label) {
        String[] parts = trim(value).split("->", -1);
        if (parts.length != 2 || isBlank(parts[0]) || isBlank(parts[1])) {
            throw new IllegalStateException(label + "必须按 编码字段->名称字段 配置");
        }
        return parts;
    }

    private String requiredIdentifier(String value, String label) {
        String identifier = trim(value);
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException(label + "不合法: " + identifier);
        }
        return identifier;
    }

    private String path(String field) {
        return "/" + trim(field).replaceFirst("^/+", "");
    }

    private String unpath(String value) {
        return trim(value).replaceFirst("^/+", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return trim(value).isBlank();
    }

    private record LookupDefinition(String table, String codeField, String labelField, JsonNode config) {
    }
}
