package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FieldMappingService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper(new JsonFactory()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature()));
    private static final Pattern FIELD_REF = Pattern.compile("\\$\\{field:([^}]+)}");
    private static final Pattern TARGET_REF = Pattern.compile("\\$\\{target:([^}]+)}");
    private static final Pattern NUMERIC_ENUM_CODE = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Set<String> NUMERIC_ENUM_TYPES = Set.of(
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "INT2", "INT4", "INT8",
            "NUMBER", "NUMERIC", "DECIMAL", "DEC", "FLOAT", "REAL", "DOUBLE", "DOUBLE PRECISION",
            "FLOAT4", "FLOAT8", "BINARY_FLOAT", "BINARY_DOUBLE", "SMALLSERIAL", "SERIAL", "BIGSERIAL");
    private static final Set<String> SUPPORTED_SQL_TRANSFORMS = Set.of(
            "upper", "lower", "trim", "ltrim", "rtrim", "toInt", "toLong", "toDouble", "toDecimal", "cast", "coalesce", "nvl", "enumMap", "jsonValue");
    private final DataSourceConnectionPropertyResolver registeredSources;
    public FieldMappingService(){this(null);}
    @Autowired public FieldMappingService(DataSourceConnectionPropertyResolver registeredSources){this.registeredSources=registeredSources;}

    public ObjectNode normalizeSpec(Object raw) {
        JsonNode node = toJsonNode(raw);
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) {
            return emptySpec();
        }
        if (node.isTextual()) {
            return parseTextSpec(node.asText());
        }
        if (node.isObject() && node.has("mappings")) {
            return normalizeNewSpec((ObjectNode) node);
        }
        return migrateLegacy(node);
    }

    public String compileQuery(Object raw) {
        return compilePlan(raw).baseQuery();
    }

    public CompiledMapping compilePlan(Object raw) {
        ObjectNode spec = normalizeSpec(raw);
        validateForCompile(spec);
        boolean passthrough = spec.path("passthroughUnmapped").asBoolean(false);
        ArrayNode mappings = (ArrayNode) spec.path("mappings");
        List<String> baseProjections = new ArrayList<>();
        List<String> finalProjections = new ArrayList<>();
        List<LookupPlan> lookups = new ArrayList<>();
        LinkedHashSet<String> baseFields = new LinkedHashSet<>();
        LinkedHashSet<String> outputFields = new LinkedHashSet<>();
        LinkedHashSet<String> runtimeFields = new LinkedHashSet<>();

        int lookupIndex = 0;
        Map<String, Map<String, Object>> registered = registeredLookupSources(spec);
        for (JsonNode mapping : mappings) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            if (isPlaceholderMapping(mapping)) continue;
            String to = columnName(requiredText(mapping, "to", "每条映射必须配置目标字段 to"), "目标字段");
            outputFields.add(to);
            if (mapping.has("lookup")) {
                LookupPlan lookup = compileLookupPlan(mapping, lookupIndex++, registered);
                lookups.add(lookup);
                for (int i = 0; i < lookup.parameterFields().size(); i++) {
                    String sourceField = lookup.parameterFields().get(i);
                    String tempField = lookup.parameterRecordFields().get(i);
                    baseProjections.add(quoteSqlIdentifier(sourceField) + " AS " + quoteSqlIdentifier(tempField));
                    baseFields.add(tempField);
                    runtimeFields.add(tempField);
                }
                finalProjections.add(quoteSqlIdentifier(to) + " AS " + quoteSqlIdentifier(to));
                runtimeFields.add(to);
            } else {
                String expr = sqlExpression(mapping);
                baseProjections.add(expr + " AS " + quoteSqlIdentifier(to));
                finalProjections.add(quoteSqlIdentifier(to) + " AS " + quoteSqlIdentifier(to));
                baseFields.add(to);
                runtimeFields.add(to);
            }
        }
        if (baseProjections.isEmpty() && !passthrough) {
            throw new IllegalStateException("字段映射至少需要一条真实映射规则，请删除默认占位字段并配置源字段和目标字段");
        }
        if (passthrough && spec.hasNonNull("exclude") && spec.path("exclude").size() > 0) {
            throw new IllegalStateException("当前 NiFi QueryRecord 模式暂不支持 passthroughUnmapped=true 时的 exclude，请改为显式 mappings");
        }

        String baseQuery = passthrough
                ? (baseProjections.isEmpty()
                ? "SELECT * FROM FLOWFILE"
                : "SELECT *, " + String.join(", ", baseProjections) + " FROM FLOWFILE")
                : "SELECT " + String.join(", ", baseProjections) + " FROM FLOWFILE";
        String finalQuery = passthrough
                ? (finalProjections.isEmpty()
                ? "SELECT * FROM FLOWFILE"
                : "SELECT *, " + String.join(", ", finalProjections) + " FROM FLOWFILE")
                : "SELECT " + String.join(", ", finalProjections) + " FROM FLOWFILE";
        return new CompiledMapping(baseQuery, finalQuery, List.copyOf(lookups),
                List.copyOf(baseFields), List.copyOf(outputFields), List.copyOf(runtimeFields));
    }

    /**
     * Compiles a field-mapping specification into one source-database SELECT.
     *
     * <p>The normal NiFi implementation must split a RecordSet before invoking
     * an external dictionary query.  When the fact table and every dictionary
     * table use the same JDBC datasource, that is unnecessary work: the source
     * database can evaluate scalar dictionary lookups and enum CASE expressions
     * for an entire result set in one query.  This method deliberately returns
     * only a SELECT statement; scheduling, JDBC credentials, and the target
     * writer remain the compiler's responsibility.</p>
     *
     * <p>Callers must first ensure that lookup data sources are co-located with
     * the source.  A {@code FAIL} lookup policy is not accepted here because a
     * scalar SQL expression cannot preserve the record-level error routing
     * contract of the standard lookup chain.</p>
     */
    public String compileSourceDbQuery(Object raw, String sourceTableSql, String sourceAlias,
                                       Function<String, String> identifier) {
        if (sourceTableSql == null || sourceTableSql.isBlank()) {
            throw new IllegalStateException("来源表不能为空，无法编译字段增强 SQL");
        }
        if (sourceAlias == null || !sourceAlias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("sourceAlias 必须是合法 SQL 标识符");
        }
        Objects.requireNonNull(identifier, "identifier");

        ObjectNode spec = normalizeSpec(raw);
        validateForCompile(spec);
        boolean passthrough = spec.path("passthroughUnmapped").asBoolean(false);
        if (passthrough) {
            throw new IllegalStateException("来源 SQL 下推不支持 passthroughUnmapped=true，请使用显式字段映射");
        }

        List<String> projections = new ArrayList<>();
        int lookupIndex = 0;
        Map<String, Map<String, Object>> registered = registeredLookupSources(spec);
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            if (isPlaceholderMapping(mapping)) continue;
            String target = columnName(requiredText(mapping, "to", "每条映射必须配置目标字段 to"), "目标字段");
            String expression;
            if (mapping.has("lookup")) {
                LookupPlan lookup = compileLookupPlan(mapping, lookupIndex++, registered);
                if ("FAIL".equalsIgnoreCase(lookup.onMissing())) {
                    throw new IllegalStateException("lookup.onMissing=FAIL 需要记录级错误路由，不能使用来源 SQL 下推");
                }
                List<String> parameters = lookup.parameterFields().stream()
                        .map(field -> sourceAlias + "." + identifier.apply(field))
                        .toList();
                expression = "(" + replaceLookupParameters(lookup.parameterizedSql(), parameters) + ")";
                if ("KEEP_SOURCE".equalsIgnoreCase(lookup.onMissing())) {
                    expression = "COALESCE(" + expression + ", " + parameters.getFirst() + ")";
                }
            } else {
                expression = sourceSqlExpression(mapping, sourceAlias, identifier);
            }
            projections.add(expression + " AS " + identifier.apply(target));
        }
        if (projections.isEmpty()) {
            throw new IllegalStateException("字段增强至少需要一条真实映射规则");
        }
        return "SELECT " + String.join(", ", projections) + " FROM " + sourceTableSql + " " + sourceAlias;
    }

    /**
     * Whether a mapping can safely be evaluated by the source database.
     *
     * <p>Expressions in the mapping DSL are Calcite/NiFi expressions.  They are
     * intentionally richer than one database dialect (for example
     * {@code LOCALTIMESTAMP} and {@code RAND_INTEGER}), so they must stay in the
     * record-mode {@code QueryRecord} processor.  Passing them to a MySQL,
     * Oracle, or DM source query changes their meaning at best and produces a
     * syntax error at worst.</p>
     */
    public boolean supportsSourceDbPushdown(Object raw) {
        ObjectNode spec = normalizeSpec(raw);
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            if (isPlaceholderMapping(mapping)) continue;
            if (mapping.hasNonNull("expression")) return false;
            // Multi-value translation is deliberately visible as a native
            // lookup chain.  Pushing it into the source SELECT would also run
            // a dictionary query against the wrong datasource when the two
            // registrations are different.
            if (mapping.path("lookup").path("multiValue").asBoolean(false)) return false;
            JsonNode transform = mapping.path("transform");
            if (!transform.isMissingNode() && !transform.isNull()) {
                String fn = transform.path("fn").asText("");
                if (!fn.isBlank() && !"enumMap".equals(fn) && !"concat".equals(fn)) return false;
            }
        }
        return true;
    }

    private String sourceSqlExpression(JsonNode mapping, String sourceAlias,
                                       Function<String, String> identifier) {
        if (mapping.has("constant")) return literal(mapping.path("constant"));
        if (mapping.hasNonNull("expression")) {
            String expression = mapping.path("expression").asText();
            Matcher matcher = FIELD_REF.matcher(expression);
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String field = columnName(matcher.group(1), "表达式源字段");
                matcher.appendReplacement(result, Matcher.quoteReplacement(sourceAlias + "." + identifier.apply(field)));
            }
            matcher.appendTail(result);
            String sql = result.toString();
            if (sql.matches(".*\\$\\{[^}]+}.*")
                    || !sql.matches("[A-Za-z0-9_ \\t\\r\\n'\"().,+\\-*/%<>=!|&?:]+")) {
                throw new IllegalStateException("表达式包含来源 SQL 下推不支持的内容: " + expression);
            }
            return sql.replace("upper(", "UPPER(").replace("lower(", "LOWER(")
                    .replace("coalesce(", "COALESCE(");
        }
        if (mapping.has("fromList")) {
            JsonNode fromList = mapping.path("fromList");
            String fn = mapping.path("transform").path("fn").asText("");
            if (!fromList.isArray() || fromList.isEmpty() || !"concat".equals(fn)) {
                throw new IllegalStateException("来源 SQL 下推的多字段映射仅支持 concat");
            }
            String separator = mapping.path("transform").path("args").isArray()
                    && !mapping.path("transform").path("args").isEmpty()
                    ? mapping.path("transform").path("args").get(0).asText("") : "";
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < fromList.size(); i++) {
                if (i > 0 && !separator.isEmpty()) parts.add(literal(MAPPER.getNodeFactory().textNode(separator)));
                String field = columnName(fromList.get(i).asText(), "源字段");
                parts.add("COALESCE(" + sourceAlias + "." + identifier.apply(field) + ", '')");
            }
            return String.join(" || ", parts);
        }
        String field = columnName(requiredText(mapping, "from", "映射必须配置源字段、常量、表达式或字典查询"), "源字段");
        String expression = sourceAlias + "." + identifier.apply(field);
        return mapping.has("transform") ? transformSql(expression, mapping.path("transform"), false,
                mapping.path("sourceDataType").asText("")) : expression;
    }

    private String replaceLookupParameters(String sql, List<String> parameters) {
        StringBuilder output = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int parameterIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !doubleQuoted) {
                if (singleQuoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    output.append("''");
                    i++;
                    continue;
                }
                singleQuoted = !singleQuoted;
            } else if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            }
            if (ch == '?' && !singleQuoted && !doubleQuoted) {
                if (parameterIndex >= parameters.size()) {
                    throw new IllegalStateException("lookup.sql 参数数量与映射字段数量不一致");
                }
                output.append(parameters.get(parameterIndex++));
            } else {
                output.append(ch);
            }
        }
        if (parameterIndex != parameters.size()) {
            throw new IllegalStateException("lookup.sql 参数数量与映射字段数量不一致");
        }
        return output.toString();
    }

    public ValidationResponse validate(Object rawSpec, List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema) {
        List<MappingIssue> errors = new ArrayList<>();
        List<MappingIssue> warnings = new ArrayList<>();
        ObjectNode spec;
        try {
            spec = normalizeSpec(rawSpec);
        } catch (RuntimeException ex) {
            errors.add(new MappingIssue("spec", null, ex.getMessage(), "ERROR", "INVALID_JSON", List.of()));
            return new ValidationResponse(false, errors, warnings, schemaCompare(sourceSchema, targetSchema));
        }
        validateSpec(spec, sourceSchema == null ? List.of() : sourceSchema, targetSchema == null ? List.of() : targetSchema, errors, warnings);
        return new ValidationResponse(errors.isEmpty(), errors, warnings, schemaCompare(sourceSchema, targetSchema));
    }

    public PreviewResponse preview(Object rawSpec, List<Map<String, Object>> sampleRows) {
        return preview(rawSpec, Map.of(), sampleRows);
    }

    public PreviewResponse preview(Object rawSpec, Map<String, Object> sourceConfig, List<Map<String, Object>> sampleRows) {
        ObjectNode spec = normalizeSpec(rawSpec);
        List<Map<String, Object>> rows = sampleRows == null ? List.of() : sampleRows;
        List<Map<String, Object>> resultRows = new ArrayList<>();
        List<RowResult> rowResults = new ArrayList<>();
        Map<String, List<Object>> multiValues = previewMultiValues(spec, sourceConfig, rows);
        for (int i = 0; i < rows.size(); i++) {
            try {
                resultRows.add(applyToRow(spec, sourceConfig, rows.get(i), i, multiValues));
                rowResults.add(new RowResult(i, true, List.of()));
            } catch (RuntimeException ex) {
                resultRows.add(Map.of());
                rowResults.add(new RowResult(i, false, List.of(ex.getMessage())));
            }
        }
        return new PreviewResponse(resultRows, rowResults, Map.of("inputRows", rows.size(), "outputRows", resultRows.size()));
    }

    public RecommendResponse recommend(List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema) {
        List<FieldMeta> sources = sourceSchema == null ? List.of() : sourceSchema;
        List<FieldMeta> targets = targetSchema == null ? List.of() : targetSchema;
        List<Recommendation> recommendations = new ArrayList<>();
        ObjectNode spec = emptySpec();
        ArrayNode mappings = spec.putArray("mappings");
        int index = 0;
        for (FieldMeta target : targets) {
            FieldMeta best = null;
            double confidence = 0;
            String reason = "";
            String targetIdentifier = recommendationIdentifier(target);
            List<FieldMeta> sameIdentifier = targetIdentifier.isBlank() ? List.of() : sources.stream()
                    .filter(source -> targetIdentifier.equalsIgnoreCase(recommendationIdentifier(source))).toList();
            // name can contain a display comment. Compare actual record paths
            // first, retaining the original source/target spelling in the rule.
            if (sameIdentifier.size() > 1 || (!targetIdentifier.isBlank() && targets.stream()
                    .filter(candidate -> targetIdentifier.equalsIgnoreCase(recommendationIdentifier(candidate))).count() > 1)) {
                continue;
            }
            if (sameIdentifier.size() == 1) {
                best = sameIdentifier.getFirst();
                MatchScore match = score(recommendationIdentifier(best), targetIdentifier);
                confidence = match.confidence();
                reason = match.reason();
            } else {
                for (FieldMeta source : sources) {
                    MatchScore score = score(source.name(), target.name());
                    if (score.confidence() > confidence) {
                        confidence = score.confidence();
                        reason = score.reason();
                        best = source;
                    }
                }
            }
            if (best != null && confidence >= 0.55) {
                ObjectNode mapping = mappings.addObject();
                mapping.put("from", normalizePath(best.pathOrName()));
                mapping.put("to", normalizePath(target.pathOrName()));
                recommendations.add(new Recommendation("rec-" + index++, mapping.path("from").asText(), mapping.path("to").asText(),
                        best.type(), target.type(), reason, confidence));
            }
        }
        return new RecommendResponse(spec, recommendations);
    }

    private Map<String, List<Object>> previewMultiValues(ObjectNode spec, Map<String, Object> sourceConfig,
                                                        List<Map<String, Object>> rows) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> registered = registeredLookupSources(spec);
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            if (!MultiValueTranslation.isRecordLookup(mapping.path("lookup"))) continue;
            LookupPlan lookup = compileLookupPlan(mapping, 0, registered);
            List<Object> translated = new ArrayList<>();
            result.put(lookup.targetField(), translated);
            if (rows.isEmpty()) continue;
            Map<String, Object> cfg = lookup.dataSource().isEmpty() ? sourceConfig : lookup.dataSource();
            try (Connection connection = lookup.recordLookup().inline() ? null : DriverManager.getConnection(
                    buildJdbcUrl(cfg), stringValue(cfg.get("username")), stringValue(cfg.get("password")))) {
                for (int start = 0; start < rows.size(); start += MultiValueTranslation.RECORD_BATCH_SIZE) {
                    List<Map<String, Object>> batch = rows.subList(start,
                            Math.min(rows.size(), start + MultiValueTranslation.RECORD_BATCH_SIZE));
                    Set<String> codes = new LinkedHashSet<>();
                    for (Map<String, Object> row : batch) codes.addAll(MultiValueTranslation.tokens(
                            row.get(lookup.parameterFields().getFirst()), lookup.recordLookup().separator()));
                    Map<String, String> values = MultiValueTranslation.dictionary(connection, lookup.recordLookup(), codes);
                    for (Map<String, Object> row : batch) translated.add(MultiValueTranslation.translate(
                            row.get(lookup.parameterFields().getFirst()), lookup.recordLookup(), values));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("多值字典预览失败: " + exception.getMessage(), exception);
            }
        }
        return result;
    }

    private String recommendationIdentifier(FieldMeta field) {
        String path = field.pathOrName();
        return path == null ? "" : path.trim().replaceFirst("^/+", "");
    }

    private void validateForCompile(ObjectNode spec) {
        ValidationResponse response = validate(spec, List.of(), List.of());
        if (!response.errors().isEmpty()) {
            throw new IllegalStateException(formatIssue(response.errors().get(0)));
        }
    }

    private String formatIssue(MappingIssue issue) {
        StringBuilder message = new StringBuilder(issue.message());
        if (issue.path() != null && !issue.path().isBlank()) {
            message.append("；位置: ").append(issue.path());
        }
        if (issue.value() != null) {
            message.append("；当前值: ").append(issue.value());
        }
        return message.toString();
    }

    private void validateSpec(ObjectNode spec, List<FieldMeta> sources, List<FieldMeta> targets,
                              List<MappingIssue> errors, List<MappingIssue> warnings) {
        if (!"1.0".equals(spec.path("version").asText(""))) {
            errors.add(new MappingIssue("version", spec.path("version").asText(null), "字段映射 DSL version 必须是 1.0", "ERROR", "INVALID_VERSION", List.of("1.0")));
        }
        JsonNode mappingsNode = spec.path("mappings");
        if (!mappingsNode.isArray()) {
            errors.add(new MappingIssue("mappings", null, "mappings 必须是数组", "ERROR", "INVALID_MAPPINGS", List.of()));
            return;
        }
        Map<String, FieldMeta> sourceMap = indexFields(sources, spec.path("passthroughCaseSensitive").asBoolean(true));
        Map<String, FieldMeta> targetMap = indexFields(targets, spec.path("passthroughCaseSensitive").asBoolean(true));
        Map<String, Integer> targetCounts = new LinkedHashMap<>();
        Set<String> mappedTargets = new LinkedHashSet<>();
        for (int i = 0; i < mappingsNode.size(); i++) {
            JsonNode mapping = mappingsNode.get(i);
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            String path = "mappings[" + i + "]";
            if (!mapping.isObject()) {
                errors.add(new MappingIssue(path, null, "每条 mapping 必须是对象", "ERROR", "INVALID_MAPPING", List.of()));
                continue;
            }
            if (!mapping.hasNonNull("to")) {
                errors.add(new MappingIssue(path + ".to", null, "每条 mapping 必须声明目标字段 to", "ERROR", "MISSING_TO", List.of()));
                continue;
            }
            String to = normalizePath(mapping.path("to").asText());
            if (to == null || to.isBlank() || "/".equals(to)) {
                errors.add(new MappingIssue(path + ".to", mapping.path("to").asText(), "目标字段不能为空", "ERROR", "MISSING_TO", List.of()));
                continue;
            }
            if (to.substring(1).contains("/") || to.contains("[") || to.contains("]")) {
                errors.add(new MappingIssue(path + ".to", to, "目标字段只支持数据库字段名或一级 RecordPath，例如 create_name", "ERROR", "INVALID_TARGET_FIELD", List.of()));
            }
            targetCounts.merge(to, 1, Integer::sum);
            mappedTargets.add(to);
            if (!targetMap.isEmpty() && !targetMap.containsKey(keyForPath(to, spec.path("passthroughCaseSensitive").asBoolean(true)))) {
                errors.add(new MappingIssue(path + ".to", to, "目标字段 '" + to.substring(1) + "' 不存在于目标 schema", "ERROR", "TARGET_FIELD_NOT_FOUND", suggestions(to, targets)));
            }
            validateSourceRefs(mapping, path, sourceMap, spec.path("passthroughCaseSensitive").asBoolean(true), sources, errors);
            validateMappingInput(mapping, path, errors);
            validateExpressionRefs(mapping, path, sourceMap, mappedTargets, spec.path("passthroughCaseSensitive").asBoolean(true), sources, errors);
            validateTransform(mapping, path, errors);
            warnTypeMismatch(mapping, path, sourceMap, targetMap, spec.path("passthroughCaseSensitive").asBoolean(true), warnings);
        }
        targetCounts.forEach((to, count) -> {
            if (count >= 2) {
                for (int i = 0; i < mappingsNode.size(); i++) {
                    JsonNode mapping = mappingsNode.get(i);
                    if (to.equals(normalizePath(mapping.path("to").asText(""))) && !mapping.has("transform") && !mapping.has("expression")) {
                        errors.add(new MappingIssue("mappings[" + i + "]", to,
                                "同一目标字段出现多条来源时，必须使用 transform 或 expression 明确合并方式", "ERROR", "AMBIGUOUS_TARGET", List.of()));
                    }
                }
            }
        });
        for (FieldMeta target : targets) {
            String path = normalizePath(target.pathOrName());
            if (target.pkRequired() && !mappedTargets.contains(path)) {
                errors.add(new MappingIssue("mappings", path, "目标主键 '" + target.name() + "' 必须有映射", "ERROR", "TARGET_PK_REQUIRED", List.of(path)));
            } else if (!target.nullableEffective() && !target.hasDefaultEffective() && !mappedTargets.contains(path)) {
                warnings.add(new MappingIssue("mappings", path, "目标字段 '" + target.name() + "' 非空且无默认值，建议配置映射或常量", "WARN", "TARGET_REQUIRED_NOT_MAPPED", List.of(path)));
            }
        }
    }

    private void validateSourceRefs(JsonNode mapping, String path, Map<String, FieldMeta> sourceMap, boolean caseSensitive,
                                    List<FieldMeta> sources, List<MappingIssue> errors) {
        if (sourceMap.isEmpty()) return;
        List<String> refs = new ArrayList<>();
        if (mapping.hasNonNull("from")) refs.add(normalizePath(mapping.path("from").asText()));
        if (mapping.has("fromList") && mapping.path("fromList").isArray()) {
            mapping.path("fromList").forEach(v -> refs.add(normalizePath(v.asText())));
        }
        for (String ref : refs) {
            if (!sourceMap.containsKey(keyForPath(ref, caseSensitive))) {
                errors.add(new MappingIssue(path + ".from", ref, "源字段 '" + ref.substring(1) + "' 不存在于源 schema", "ERROR", "SOURCE_FIELD_NOT_FOUND", suggestions(ref, sources)));
            }
        }
    }

    private void validateMappingInput(JsonNode mapping, String path, List<MappingIssue> errors) {
        boolean hasFrom = mapping.hasNonNull("from") && !mapping.path("from").asText().isBlank();
        boolean hasFromList = mapping.has("fromList") && mapping.path("fromList").isArray()
                && mapping.path("fromList").size() > 0
                && anyNonBlank(mapping.path("fromList"));
        boolean hasConstant = mapping.has("constant");
        boolean hasExpression = mapping.hasNonNull("expression") && !mapping.path("expression").asText().isBlank();
        boolean hasLookup = mapping.has("lookup") && mapping.path("lookup").isObject();
        if (!hasFrom && !hasFromList && !hasConstant && !hasExpression && !hasLookup) {
            errors.add(new MappingIssue(path + ".from", null, "请配置源字段、常量、表达式、字典查询或多字段合并", "ERROR", "MISSING_SOURCE", List.of()));
        }
        if (hasFrom) {
            String from = normalizePath(mapping.path("from").asText());
            if (from.substring(1).contains("/") || from.contains("[") || from.contains("]")) {
                errors.add(new MappingIssue(path + ".from", from, "源字段只支持数据库字段名或一级 RecordPath，例如 create_name", "ERROR", "INVALID_SOURCE_FIELD", List.of()));
            }
        }
        if (mapping.has("fromList") && mapping.path("fromList").isArray()) {
            for (JsonNode item : mapping.path("fromList")) {
                String from = normalizePath(item.asText(""));
                if (from == null || from.isBlank() || "/".equals(from)) continue;
                if (from.substring(1).contains("/") || from.contains("[") || from.contains("]")) {
                    errors.add(new MappingIssue(path + ".fromList", from, "源字段列表只支持数据库字段名或一级 RecordPath", "ERROR", "INVALID_SOURCE_FIELD", List.of()));
                }
            }
        }
        if (hasLookup) {
            JsonNode lookup = mapping.path("lookup");
            if (!hasFrom) {
                errors.add(new MappingIssue(path + ".from", null, "字典查询映射必须配置 from", "ERROR", "LOOKUP_SOURCE_REQUIRED", List.of()));
            }
            if (hasFromList || hasConstant || hasExpression) {
                errors.add(new MappingIssue(path + ".lookup", null, "lookup 不能与 fromList、constant、expression 混用", "ERROR", "LOOKUP_CONFLICT", List.of()));
            }
            if (MultiValueTranslation.isRecordLookup(lookup)) {
                try { MultiValueTranslation.rule(lookup); }
                catch (RuntimeException ex) {
                    errors.add(new MappingIssue(path + ".lookup", null, ex.getMessage(), "ERROR", "LOOKUP_INVALID", List.of()));
                }
                return;
            }
            String sql = lookup.path("sql").asText("").trim();
            if (sql.isBlank()) {
                errors.add(new MappingIssue(path + ".lookup.sql", null, "lookup.sql 不能为空", "ERROR", "LOOKUP_SQL_REQUIRED", List.of()));
            } else {
                try {
                    parseLookupSql(sql, lookup.path("resultColumn").asText(""), mapping.path("from").asText(""));
                } catch (RuntimeException ex) {
                    errors.add(new MappingIssue(path + ".lookup.sql", sql, ex.getMessage(), "ERROR", "LOOKUP_SQL_INVALID", List.of()));
                }
            }
        }
    }

    private boolean anyNonBlank(JsonNode array) {
        for (JsonNode item : array) {
            if (!item.asText("").isBlank()) return true;
        }
        return false;
    }

    private void validateExpressionRefs(JsonNode mapping, String path, Map<String, FieldMeta> sourceMap, Set<String> mappedTargets,
                                        boolean caseSensitive, List<FieldMeta> sources, List<MappingIssue> errors) {
        for (String expr : expressionTexts(mapping)) {
            Matcher fieldMatcher = FIELD_REF.matcher(expr);
            while (fieldMatcher.find()) {
                String ref = normalizePath(fieldMatcher.group(1));
                if (!sourceMap.isEmpty() && !sourceMap.containsKey(keyForPath(ref, caseSensitive))) {
                    errors.add(new MappingIssue(path + ".expression", ref, "表达式引用的源字段 '" + ref.substring(1) + "' 不存在", "ERROR", "EXPR_SOURCE_NOT_FOUND", suggestions(ref, sources)));
                }
            }
            Matcher targetMatcher = TARGET_REF.matcher(expr);
            while (targetMatcher.find()) {
                String ref = normalizePath(targetMatcher.group(1));
                if (!mappedTargets.contains(ref)) {
                    errors.add(new MappingIssue(path + ".expression", ref, "表达式只能引用前面已映射的目标字段: " + ref, "ERROR", "TARGET_REFERENCE_ORDER", List.of()));
                }
            }
            if (!balanced(expr)) {
                errors.add(new MappingIssue(path + ".expression", expr, "表达式括号或引号不完整", "ERROR", "EXPR_SYNTAX", List.of()));
            }
        }
    }

    private void validateTransform(JsonNode mapping, String path, List<MappingIssue> errors) {
        if (!mapping.has("transform")) return;
        String fn = mapping.path("transform").path("fn").asText("");
        if (fn.isBlank()) {
            errors.add(new MappingIssue(path + ".transform.fn", null, "transform 必须声明 fn", "ERROR", "TRANSFORM_FN_REQUIRED", List.of()));
            return;
        }
        if (!allTransformNames().contains(fn)) {
            errors.add(new MappingIssue(path + ".transform.fn", fn, "不支持的 transform 函数: " + fn, "ERROR", "TRANSFORM_UNSUPPORTED", new ArrayList<>(allTransformNames())));
        }
    }

    private void warnTypeMismatch(JsonNode mapping, String path, Map<String, FieldMeta> sourceMap, Map<String, FieldMeta> targetMap,
                                  boolean caseSensitive, List<MappingIssue> warnings) {
        if (sourceMap.isEmpty() || targetMap.isEmpty() || !mapping.hasNonNull("from") || !mapping.hasNonNull("to")) return;
        FieldMeta source = sourceMap.get(keyForPath(normalizePath(mapping.path("from").asText()), caseSensitive));
        FieldMeta target = targetMap.get(keyForPath(normalizePath(mapping.path("to").asText()), caseSensitive));
        if (source == null || target == null) return;
        String sourceType = platformType(source.type());
        String targetType = platformType(target.type());
        if (!sourceType.equals(targetType) && !mapping.has("transform")) {
            warnings.add(new MappingIssue(path, mapping.path("to").asText(), "源类型 " + source.type() + " -> 目标类型 " + target.type() + " 可能需要类型转换", "WARN", "TYPE_NEEDS_CAST", List.of(defaultCast(sourceType, targetType))));
        }
    }

    private ObjectNode parseTextSpec(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return emptySpec();
        try {
            STRICT_MAPPER.readTree(trimmed);
        } catch (JsonProcessingException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Duplicate field")) {
                throw new IllegalStateException("旧版字段映射存在重复键，无法可靠迁移。请人工确认每条映射规则后改用新版 DSL。", ex);
            }
        }
        try {
            JsonNode parsed = MAPPER.readTree(trimmed);
            if (parsed.isObject() && parsed.has("mappings")) return normalizeNewSpec((ObjectNode) parsed);
            return migrateLegacy(parsed);
        } catch (Exception ex) {
            throw new IllegalStateException("字段映射不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    private ObjectNode migrateLegacy(JsonNode legacy) {
        ObjectNode spec = emptySpec();
        ArrayNode mappings = spec.putArray("mappings");
        if (legacy == null || legacy.isNull()) return spec;
        if (legacy.isObject()) {
            legacy.fields().forEachRemaining(entry -> {
                ObjectNode mapping = mappings.addObject();
                mapping.put("from", normalizePath(entry.getKey()));
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    mapping.put("to", normalizePath(value.asText()));
                } else if (value.isObject()) {
                    mapping.put("to", normalizePath(value.path("to").asText(value.path("dst").asText())));
                    if (value.hasNonNull("fn")) {
                        ObjectNode transform = mapping.putObject("transform");
                        transform.put("fn", value.path("fn").asText());
                        if (value.has("args")) transform.set("args", value.path("args"));
                    }
                }
            });
        } else if (legacy.isArray()) {
            legacy.forEach(item -> {
                String from = item.path("src").asText(item.path("from").asText(null));
                String to = item.path("dst").asText(item.path("to").asText(null));
                if (from != null && to != null) {
                    ObjectNode mapping = mappings.addObject();
                    mapping.put("from", normalizePath(from));
                    mapping.put("to", normalizePath(to));
                }
            });
        } else {
            throw new IllegalStateException("字段映射必须是新版 DSL、旧版对象或旧版数组");
        }
        removePlaceholderMappings(spec);
        return spec;
    }

    private ObjectNode normalizeNewSpec(ObjectNode input) {
        ObjectNode spec = input.deepCopy();
        if(spec.has("functions")&&(!spec.path("functions").isObject()||spec.path("functions").size()>16))throw new IllegalStateException("自定义函数必须为最多 16 项的表达式定义");
        if (!spec.hasNonNull("version")) spec.put("version", "1.0");
        if (!spec.has("mappings") || !spec.path("mappings").isArray()) spec.putArray("mappings");
        ArrayNode normalized = MAPPER.createArrayNode();
        spec.path("mappings").forEach(item -> {
            if (item.isObject()) {
                ObjectNode mapping = ((ObjectNode) item).deepCopy();
                if (mapping.hasNonNull("from")) mapping.put("from", normalizePath(mapping.path("from").asText()));
                if (mapping.has("fromList") && mapping.path("fromList").isArray()) {
                    ArrayNode fromList = MAPPER.createArrayNode();
                    mapping.path("fromList").forEach(v -> fromList.add(normalizePath(v.asText())));
                    mapping.set("fromList", fromList);
                }
                if (mapping.hasNonNull("to")) mapping.put("to", normalizePath(mapping.path("to").asText()));
                JsonNode function=mapping.path("userFunction");
                String fn=mapping.path("transform").path("fn").asText("");
                if(fn.startsWith("user:")) {
                    ObjectNode definition=MAPPER.createObjectNode();definition.put("name",fn.substring(5));definition.put("from",mapping.path("from").asText());definition.set("args",mapping.path("transform").path("args").deepCopy());mapping.set("userFunction",definition);function=definition;
                }
                if(function.isObject()) {
                    String name=function.path("name").asText("");
                    if(!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))throw new IllegalStateException("自定义函数名称无效");
                    String recipe=spec.path("functions").path(name).path("expression").asText("");
                    if(recipe.isBlank()||recipe.length()>4000||!recipe.contains("${value}"))throw new IllegalStateException("自定义函数必须提供包含 ${value} 的转换表达式");
                    String expression=recipe.replace("${value}","${field:"+columnName(function.path("from").asText(),"函数来源字段")+"}");
                    JsonNode arguments=function.path("args");
                    for(int arg=0;arg<Math.min(8,arguments.size());arg++)expression=expression.replace("${arg:"+arg+"}",literal(arguments.get(arg)));
                    if(expression.contains("${arg:"))throw new IllegalStateException("自定义函数参数未完整配置");
                    mapping.put("expression",expression);mapping.remove(List.of("from","transform","fromList","constant","lookup"));
                }
                normalized.add(mapping);
            }
        });
        spec.set("mappings", normalized);
        removePlaceholderMappings(spec);
        return spec;
    }

    private void removePlaceholderMappings(ObjectNode spec) {
        JsonNode mappingsNode = spec.path("mappings");
        if (!mappingsNode.isArray()) return;
        ArrayNode filtered = MAPPER.createArrayNode();
        mappingsNode.forEach(mapping -> {
            if (!isPlaceholderMapping(mapping)) filtered.add(mapping);
        });
        spec.set("mappings", filtered);
    }

    private boolean isPlaceholderMapping(JsonNode mapping) {
        return mapping != null
                && "/source_field".equals(normalizePath(mapping.path("from").asText("")))
                && "/target_field".equals(normalizePath(mapping.path("to").asText("")));
    }

    private ObjectNode emptySpec() {
        ObjectNode spec = MAPPER.createObjectNode();
        spec.put("version", "1.0");
        spec.put("passthroughUnmapped", false);
        spec.put("passthroughCaseSensitive", true);
        spec.put("onMissingSource", "NULL");
        spec.put("onTypeMismatch", "CAST");
        spec.put("defaultLocale", "zh-CN");
        spec.putArray("mappings");
        return spec;
    }

    private JsonNode toJsonNode(Object raw) {
        if (raw == null) return null;
        if (raw instanceof JsonNode node) return node;
        if (raw instanceof String text) return MAPPER.getNodeFactory().textNode(text);
        return MAPPER.valueToTree(raw);
    }

    private String sqlExpression(JsonNode mapping) {
        return sqlExpression(mapping, null);
    }

    private String sqlExpression(JsonNode mapping, String lookupValueColumn) {
        if (mapping.has("constant")) return literal(mapping.path("constant"));
        if (mapping.hasNonNull("expression")) {
            return expressionToSql(mapping.path("expression").asText());
        }
        if (mapping.has("lookup")) {
            if (!StringUtils.hasText(lookupValueColumn)) {
                throw new IllegalStateException("lookup 映射需要提供 lookupValueColumn");
            }
            return quoteSqlIdentifier(lookupValueColumn);
        }
        if (mapping.has("fromList")) {
            return manyToOneSql(mapping);
        }
        String from = columnName(requiredText(mapping, "from", "映射必须配置 from、fromList、constant 或 expression 之一"), "源字段");
        String expr = quoteSqlIdentifier(from);
        if (mapping.has("transform")) expr = transformSql(expr, mapping.path("transform"), false,
                mapping.path("sourceDataType").asText(""));
        return expr;
    }

    private String manyToOneSql(JsonNode mapping) {
        JsonNode fromList = mapping.path("fromList");
        if (!fromList.isArray() || fromList.size() == 0) throw new IllegalStateException("fromList 必须至少包含一个源字段");
        String fn = mapping.path("transform").path("fn").asText("");
        if (!"concat".equals(fn)) throw new IllegalStateException("当前 NiFi QueryRecord 模式下，多对一暂仅支持 transform.fn=concat");
        String separator = mapping.path("transform").path("args").isArray() && mapping.path("transform").path("args").size() > 0
                ? mapping.path("transform").path("args").get(0).asText("") : "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < fromList.size(); i++) {
            if (i > 0 && !separator.isEmpty()) parts.add(literal(MAPPER.getNodeFactory().textNode(separator)));
            parts.add("COALESCE(" + quoteSqlIdentifier(columnName(fromList.get(i).asText(), "源字段")) + ", '')");
        }
        return String.join(" || ", parts);
    }

    private String transformSql(String expr, JsonNode transform, boolean allowComplex, String sourceDataType) {
        String fn = transform.path("fn").asText("");
        JsonNode args = transform.path("args");
        if (!allowComplex && !SUPPORTED_SQL_TRANSFORMS.contains(fn)) {
            throw new IllegalStateException("当前运行模式暂不支持 transform.fn=" + fn + "，请使用基础函数或等待脚本化字段映射执行器");
        }
        return switch (fn) {
            case "upper" -> "UPPER(" + expr + ")";
            case "lower" -> "LOWER(" + expr + ")";
            case "trim" -> "TRIM(" + expr + ")";
            case "ltrim" -> "LTRIM(" + expr + ")";
            case "rtrim" -> "RTRIM(" + expr + ")";
            case "toInt" -> "CAST(" + expr + " AS INTEGER)";
            case "toLong" -> "CAST(" + expr + " AS BIGINT)";
            case "toDouble" -> "CAST(" + expr + " AS DOUBLE)";
            case "toDecimal" -> "CAST(" + expr + " AS DECIMAL" + decimalSuffix(args) + ")";
            case "cast" -> "CAST(" + expr + " AS " + sqlType(args != null && args.size() > 0 ? args.get(0).asText("STRING") : "STRING") + ")";
            case "coalesce", "nvl" -> "COALESCE(" + expr + ", " + literal(args != null && args.size() > 0 ? args.get(0) : MAPPER.getNodeFactory().nullNode()) + ")";
            case "enumMap" -> enumMapSql(expr, args, sourceDataType);
            case "jsonValue" -> {
                String path=args.path(0).asText("");
                if(!path.matches("\\$(?:\\.[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]{1,6}\\]){1,16}"))throw new IllegalStateException("JSON 路径须为有界的 $.字段 或 [序号] 路径");
                yield "JSON_VALUE("+expr+", 'lax "+path+"' NULL ON EMPTY NULL ON ERROR)";
            }
            default -> expr;
        };
    }

    /**
     * Compiles an enumerated or mandatory-standard mapping into a QueryRecord
     * CASE expression.  This lets a source code keep its original value while
     * the generated {@code _cn} field stores the governed Chinese description.
     */
    private String enumMapSql(String sourceExpression, JsonNode args, String sourceDataType) {
        if (args == null || !args.isArray() || args.isEmpty() || !args.get(0).isObject()) {
            throw new IllegalStateException("transform.fn=enumMap 必须提供代码到描述的映射对象");
        }
        JsonNode values = args.get(0);
        List<String> branches = new ArrayList<>();
        values.fields().forEachRemaining(entry -> branches.add("WHEN " + enumCodeLiteral(entry.getKey(), sourceDataType)
                + " THEN " + literal(entry.getValue())));
        if (branches.isEmpty()) {
            throw new IllegalStateException("transform.fn=enumMap 的映射对象不能为空");
        }
        JsonNode fallback = args.size() > 1 ? args.get(1) : MAPPER.getNodeFactory().nullNode();
        return "CASE " + sourceExpression + " " + String.join(" ", branches)
                + " ELSE " + literal(fallback) + " END";
    }

    // JSON object keys are always strings. Their SQL type comes from the source
    // field, never from how a code looks (varchar codes such as 01 must stay text).
    static boolean isNumericEnumSource(String sourceDataType) {
        if (sourceDataType == null) return false;
        String type = sourceDataType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        int precision = type.indexOf('(');
        if (precision >= 0) type = type.substring(0, precision).trim();
        type = type.replaceFirst("(?: (?:UNSIGNED|ZEROFILL))+$", "");
        return NUMERIC_ENUM_TYPES.contains(type);
    }

    private String enumCodeLiteral(String code, String sourceDataType) {
        return isNumericEnumSource(sourceDataType) ? numericEnumCode(code).toPlainString()
                : literal(MAPPER.getNodeFactory().textNode(code));
    }

    private BigDecimal numericEnumCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.length() > 128 || !NUMERIC_ENUM_CODE.matcher(value).matches()) {
            throw new IllegalStateException("数字类型字段的枚举编码必须是有效数字，请检查关联枚举配置");
        }
        try {
            BigDecimal number = new BigDecimal(value);
            if (Math.abs((long) number.scale()) > 128) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("数字类型字段的枚举编码超出支持范围，请检查关联枚举配置");
        }
    }

    private String expressionToSql(String expression) {
        String sql = expression;
        Matcher fieldMatcher = FIELD_REF.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (fieldMatcher.find()) {
            fieldMatcher.appendReplacement(sb, Matcher.quoteReplacement(quoteSqlIdentifier(columnName(fieldMatcher.group(1), "源字段"))));
        }
        fieldMatcher.appendTail(sb);
        sql = sb.toString().replace("||", "||");
        if (sql.matches(".*\\$\\{[^}]+}.*")) {
            throw new IllegalStateException("当前 QueryRecord 模式暂不支持表达式中的非 field 引用: " + expression);
        }
        if (!sql.matches("[A-Za-z0-9_ \\t\\r\\n'\"().,+\\-*/%<>=!|&?:]+")) {
            throw new IllegalStateException("表达式包含当前运行模式不支持的字符: " + expression);
        }
        return sql.replace("upper(", "UPPER(").replace("lower(", "LOWER(").replace("coalesce(", "COALESCE(");
    }

    private Map<String, Object> applyToRow(ObjectNode spec, Map<String, Object> sourceConfig, Map<String, Object> row, int rowIndex,
                                         Map<String, List<Object>> multiValues) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (spec.path("passthroughUnmapped").asBoolean(false)) output.putAll(row);
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            String to = columnName(requiredText(mapping, "to", "每条映射必须配置 to"), "目标字段");
            if (multiValues.containsKey(to)) {
                Object value = multiValues.get(to).get(rowIndex);
                if (value == null) {
                    String missing = mapping.path("lookup").path("onMissing").asText("NULL");
                    if ("KEEP_SOURCE".equals(missing)) value = row.get(columnName(mapping.path("from").asText(), "源字段"));
                    if ("FAIL".equals(missing)) throw new IllegalStateException("lookup returned no result for field " + to);
                }
                output.put(to, value);
            } else output.put(to, valueForMapping(mapping, sourceConfig, row, output, rowIndex));
        }
        return output;
    }

    private Object valueForMapping(JsonNode mapping, Map<String, Object> sourceConfig, Map<String, Object> row, Map<String, Object> target, int rowIndex) {
        Object value;
        if (mapping.has("constant")) value = MAPPER.convertValue(mapping.path("constant"), Object.class);
        else if (mapping.has("lookup")) {
            LookupPlan lookup = compileLookupPlan(mapping, 0);
            value = executeLookup(sourceConfig, lookup, row);
            if (value == null) {
                String onMissing = lookup.onMissing();
                if ("KEEP_SOURCE".equals(onMissing)) {
                    value = row.get(lookup.parameterFields().get(0));
                } else if ("FAIL".equals(onMissing)) {
                    throw new IllegalStateException("lookup returned no result for field " + lookup.parameterFields().get(0));
                }
            }
        }
        else if (mapping.has("fromList")) {
            List<Object> values = new ArrayList<>();
            mapping.path("fromList").forEach(v -> values.add(row.get(columnName(v.asText(), "源字段"))));
            value = applyTransform(values, mapping.path("transform"), mapping.path("sourceDataType").asText(""));
        } else if (mapping.hasNonNull("expression")) {
            value = evalSimpleExpression(mapping.path("expression").asText(), row, target, rowIndex);
        } else {
            value = row.get(columnName(requiredText(mapping, "from", "映射必须配置 from"), "源字段"));
            if (mapping.has("transform")) value = applyTransform(value, mapping.path("transform"), mapping.path("sourceDataType").asText(""));
        }
        return value;
    }

    private Object applyTransform(Object value, JsonNode transform, String sourceDataType) {
        String fn = transform.path("fn").asText("");
        JsonNode args = transform.path("args");
        try {
            return switch (fn) {
                case "upper" -> value == null ? null : value.toString().toUpperCase(Locale.ROOT);
                case "lower" -> value == null ? null : value.toString().toLowerCase(Locale.ROOT);
                case "trim" -> value == null ? null : value.toString().trim();
                case "ltrim" -> value == null ? null : value.toString().replaceFirst("^\\s+", "");
                case "rtrim" -> value == null ? null : value.toString().replaceFirst("\\s+$", "");
                case "toInt" -> value == null ? null : Integer.parseInt(value.toString());
                case "toLong" -> value == null ? null : Long.parseLong(value.toString());
                case "toDouble" -> value == null ? null : Double.parseDouble(value.toString());
                case "toDecimal" -> value == null ? null : new BigDecimal(value.toString());
                case "coalesce", "nvl" -> value != null ? value : argValue(args, 0, null);
                case "concat" -> concat(value, args != null && args.size() > 0 ? args.get(0).asText("") : "");
                case "enumMap" -> enumMap(value, args, sourceDataType);
                case "jsonValue" -> jsonValue(value, args);
                case "md5", "sha1", "sha256" -> digest(fn, value == null ? "" : value.toString());
                case "urlEncode" -> value == null ? null : URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
                case "urlDecode" -> value == null ? null : URLDecoder.decode(value.toString(), StandardCharsets.UTF_8);
                default -> value;
            };
        } catch (Exception ex) {
            String onError = transform.path("onError").asText("NULL");
            if ("KEEP_ORIGINAL".equals(onError)) return value;
            if ("SKIP_ROW".equals(onError)) throw new IllegalStateException("字段转换失败，按策略跳过行: " + ex.getMessage(), ex);
            return null;
        }
    }

    private Object evalSimpleExpression(String expression, Map<String, Object> row, Map<String, Object> target, int rowIndex) {
        if (expression.length()>4000) throw new IllegalStateException("预览表达式不能超过 4000 个字符");
        return previewExpression(expression.trim(),row,rowIndex,0);
    }

    /** Bounded scalar preview, never evaluates code or substitutes row values into SQL.
     * SQL outside this explicit subset still compiles for QueryRecord but must not
     * be presented as a successfully evaluated preview. */
    private Object previewExpression(String value,Map<String,Object> row,int rowIndex,int depth) {
        if(depth>16)throw new IllegalStateException("预览表达式嵌套不能超过 16 层");
        value=value.trim();
        Matcher field=FIELD_REF.matcher(value);
        if(field.matches())return row.get(columnName(field.group(1),"源字段"));
        if("${row.index}".equals(value))return rowIndex;
        if("NULL".equalsIgnoreCase(value))return null;
        if("TRUE".equalsIgnoreCase(value)||"FALSE".equalsIgnoreCase(value))return Boolean.parseBoolean(value);
        if(value.matches("[-+]?\\d+(?:\\.\\d+)?"))return new BigDecimal(value);
        if(value.matches("'(?:[^']|'')*'"))return value.substring(1,value.length()-1).replace("''","'");
        List<String> concat=previewParts(value,"||");
        if(concat.size()>1){StringBuilder out=new StringBuilder();for(String item:concat){Object part=previewExpression(item,row,rowIndex,depth+1);if(part==null)return null;out.append(part);}return out.toString();}
        Matcher fn=Pattern.compile("(?is)^([a-z][a-z0-9_]*)\\((.*)\\)$").matcher(value);
        if(fn.matches()){
            String name=fn.group(1).toUpperCase(Locale.ROOT);
            if(!List.of("UPPER","LOWER","TRIM","LTRIM","RTRIM","COALESCE","CONCAT","ABS").contains(name))throw new IllegalStateException("样例预览暂不支持函数 "+name+"；请使用共享加工运行验证");
            List<Object> args=new ArrayList<>();for(String item:previewParts(fn.group(2),","))args.add(previewExpression(item,row,rowIndex,depth+1));
            if(args.isEmpty())throw new IllegalStateException("函数参数不能为空");
            if("COALESCE".equals(name))return args.stream().filter(Objects::nonNull).findFirst().orElse(null);
            if("CONCAT".equals(name)){if(args.stream().anyMatch(Objects::isNull))return null;return args.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining());}
            if(args.size()!=1)throw new IllegalStateException(name+" 预览需要一个参数");
            Object arg=args.getFirst();if(arg==null)return null;
            return switch(name){case "UPPER"->arg.toString().toUpperCase(Locale.ROOT);case "LOWER"->arg.toString().toLowerCase(Locale.ROOT);case "TRIM"->arg.toString().trim();case "LTRIM"->arg.toString().replaceFirst("^\\s+","");case "RTRIM"->arg.toString().replaceFirst("\\s+$","");case "ABS"->new BigDecimal(arg.toString()).abs();default->throw new IllegalStateException("不支持的预览函数");};
        }
        throw new IllegalStateException("样例预览暂不支持此 SQL 表达式；编译结果不等于运行结果，请使用共享加工运行验证");
    }

    private List<String> previewParts(String value,String delimiter){
        List<String> parts=new ArrayList<>();int begin=0,level=0;boolean quoted=false;
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch=='\''){if(quoted&&i+1<value.length()&&value.charAt(i+1)=='\''){i++;continue;}quoted=!quoted;continue;}
            if(quoted)continue;
            if(ch=='(')level++;else if(ch==')')level--;
            if(level<0)throw new IllegalStateException("表达式括号不匹配");
            if(level==0&&value.startsWith(delimiter,i)){parts.add(value.substring(begin,i));i+=delimiter.length()-1;begin=i+1;}
        }
        if(quoted||level!=0)throw new IllegalStateException("表达式引号或括号不匹配");
        if(!value.substring(begin).isBlank())parts.add(value.substring(begin));
        return parts;
    }

    private Object concat(Object value, String separator) {
        if (value instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull).map(String::valueOf).reduce((a, b) -> a + separator + b).orElse("");
        }
        return value == null ? null : value.toString();
    }

    private Object enumMap(Object value, JsonNode args, String sourceDataType) {
        if (args == null || args.size() == 0 || !args.get(0).isObject()) return value;
        if (isNumericEnumSource(sourceDataType) && value != null) {
            BigDecimal numericValue = numericEnumCode(String.valueOf(value));
            var entries = args.get(0).fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (numericValue.compareTo(numericEnumCode(entry.getKey())) == 0) {
                    return MAPPER.convertValue(entry.getValue(), Object.class);
                }
            }
            return args.size() > 1 ? MAPPER.convertValue(args.get(1), Object.class) : value;
        }
        String key = value == null ? "" : String.valueOf(value);
        JsonNode mapped = args.get(0).path(key);
        if (!mapped.isMissingNode()) return MAPPER.convertValue(mapped, Object.class);
        return args.size() > 1 ? MAPPER.convertValue(args.get(1), Object.class) : value;
    }

    private String digest(String fn, String value) throws Exception {
        String algorithm = switch (fn) { case "sha1" -> "SHA-1"; case "sha256" -> "SHA-256"; default -> "MD5"; };
        byte[] bytes = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private List<String> expressionTexts(JsonNode mapping) {
        List<String> expressions = new ArrayList<>();
        if (mapping.hasNonNull("expression")) expressions.add(mapping.path("expression").asText());
        if (mapping.hasNonNull("when")) expressions.add(mapping.path("when").asText());
        return expressions;
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalStateException(message);
        return value;
    }

    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim();
        if (s.startsWith("root.")) s = s.substring(5);
        if (!s.startsWith("/")) s = "/" + s.replace('.', '/');
        return s;
    }

    private String columnName(String path, String label) {
        String value = normalizePath(path);
        if (value == null || value.isBlank() || "/".equals(value)) throw new IllegalStateException(label + "不能为空");
        String name = value.startsWith("/") ? value.substring(1) : value;
        if (name.contains("/") || name.contains("[") || name.contains("]")) {
            throw new IllegalStateException(label + "只支持数据库字段名或一级 RecordPath，例如 /create_name；当前值: " + path);
        }
        return name;
    }

    private String quoteSqlIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String literal(JsonNode node) {
        if (node == null || node.isNull()) return "NULL";
        if (node.isNumber() || node.isBoolean()) return node.asText();
        String value = node.asText().replace("'", "''");
        // QueryRecord uses Apache Calcite, whose default SQL literal charset is
        // ISO-8859-1.  A normal literal therefore fails before execution when a
        // mandatory-standard or enum mapping contains Chinese text.  Declare
        // UTF-8 explicitly only when it is required, so ordinary ASCII SQL
        // remains unchanged while governed descriptions are safe to compile.
        return value.chars().allMatch(character -> character <= 0x7F)
                ? "'" + value + "'"
                : "_UTF-8'" + value + "'";
    }

    private Object jsonValue(Object value, JsonNode args) throws Exception {
        if(value==null)return null;
        String path=args.path(0).asText("");
        if(!path.matches("\\$(?:\\.[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]{1,5}\\]){1,16}"))throw new IllegalArgumentException("JSON 路径只支持有界的对象属性和数组下标");
        JsonNode node=value instanceof JsonNode json?json:MAPPER.readTree(value.toString());
        Matcher steps=Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_]*)|\\[([0-9]+)\\]").matcher(path.substring(1));
        while(steps.find())node=steps.group(1)!=null?node.path(steps.group(1)):node.path(Integer.parseInt(steps.group(2)));
        return node.isValueNode()&&!node.isNull()?MAPPER.convertValue(node,Object.class):null;
    }

    private String decimalSuffix(JsonNode args) {
        if (args != null && args.size() > 0 && args.get(0).canConvertToInt()) return "(38," + args.get(0).asInt() + ")";
        return "(38,10)";
    }

    private String sqlType(String targetType) {
        return switch (targetType.trim().toUpperCase(Locale.ROOT)) {
            case "INT", "INTEGER" -> "INTEGER";
            case "LONG", "BIGINT" -> "BIGINT";
            case "DOUBLE" -> "DOUBLE";
            case "DECIMAL" -> "DECIMAL(38,10)";
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            default -> "VARCHAR";
        };
    }

    private Object argValue(JsonNode args, int index, Object defaultValue) {
        if (args == null || !args.isArray() || args.size() <= index) return defaultValue;
        return MAPPER.convertValue(args.get(index), Object.class);
    }

    private String stripQuotes(String value) {
        String s = value.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) return s.substring(1, s.length() - 1);
        return s;
    }

    private boolean balanced(String expression) {
        long open = expression.chars().filter(ch -> ch == '(').count();
        long close = expression.chars().filter(ch -> ch == ')').count();
        return open == close;
    }

    private Map<String, FieldMeta> indexFields(List<FieldMeta> fields, boolean caseSensitive) {
        Map<String, FieldMeta> out = new LinkedHashMap<>();
        for (FieldMeta field : fields) out.put(keyForPath(normalizePath(field.pathOrName()), caseSensitive), field);
        return out;
    }

    private String keyForPath(String path, boolean caseSensitive) {
        String normalized = normalizePath(path);
        return caseSensitive ? normalized : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean containsLookup(ObjectNode spec) {
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            if (mapping.has("lookup")) return true;
        }
        return false;
    }

    private LookupPlan compileLookupPlan(JsonNode mapping, int lookupIndex) {
        return compileLookupPlan(mapping, lookupIndex, null);
    }

    private LookupPlan compileLookupPlan(JsonNode mapping, int lookupIndex, Map<String, Map<String, Object>> registered) {
        String targetField = columnName(requiredText(mapping, "to", "字典查询映射必须配置目标字段 to"), "目标字段");
        String sourceField = columnName(requiredText(mapping, "from", "字典查询映射必须配置源字段 from"), "源字段");
        JsonNode lookup = mapping.path("lookup");
        if (MultiValueTranslation.isRecordLookup(lookup)) {
            var rule = MultiValueTranslation.rule(lookup);
            return new LookupPlan(targetField, targetField, "", List.of(sourceField),
                    List.of("__lookup_" + lookupIndex + "_0"), lookup.path("onMissing").asText("NULL"),
                    rule.inline() ? Map.of() : lookupDataSource(lookup, registered), true, rule);
        }
        ParsedLookupSql parsed = parseLookupSql(lookup.path("sql").asText(""), lookup.path("resultColumn").asText(""), sourceField);
        List<String> parameterRecordFields = new ArrayList<>();
        for (int i = 0; i < parsed.parameterFields().size(); i++) {
            parameterRecordFields.add("__lookup_" + lookupIndex + "_" + i);
        }
        if (parsed.parameterFields().isEmpty()) {
            throw new IllegalStateException("lookup.sql 必须包含 ? 或 ${field:字段名} 占位符");
        }
        if (!parsed.parameterFields().contains(sourceField)) {
            throw new IllegalStateException("lookup.from 必须作为 lookup.sql 的参数字段");
        }
        return new LookupPlan(
                targetField,
                parsed.resultColumn(),
                parsed.parameterizedSql(),
                List.copyOf(parsed.parameterFields()),
                List.copyOf(parameterRecordFields),
                lookup.path("onMissing").asText("NULL"),
                lookupDataSource(lookup, registered),
                lookup.path("multiValue").asBoolean(false));
    }

    /**
     * A dictionary can be registered in a different datasource from the fact
     * table. Keep its connection alongside the lookup rule so deployment uses
     * the real dictionary location instead of assuming the upstream source.
     */
    private Map<String, Map<String, Object>> registeredLookupSources(ObjectNode spec) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode mapping : spec.path("mappings")) {
            if (mapping.path("enabled").isBoolean() && !mapping.path("enabled").asBoolean()) continue;
            JsonNode lookup = mapping.path("lookup");
            if (lookup.path("values").isObject()) continue;
            String id = lookup.path("dataSource").path("datasourceId").asText("");
            if (!id.isBlank()) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();
        if (registeredSources == null) throw new IllegalStateException("已登记字典来源须由服务端解析连接");
        return registeredSources.resolveBatch(TenantContext.requireTenantId(), ids, "MENU:2081000000000000002");
    }

    private Map<String, Object> lookupDataSource(JsonNode lookup, Map<String, Map<String, Object>> registered) {
        JsonNode dataSource = lookup.path("dataSource");
        if (!dataSource.isObject()) {
            return Map.of();
        }
        String id=dataSource.path("datasourceId").asText("");
        if(!id.isBlank()) {
            if(registeredSources==null)throw new IllegalStateException("已登记字典来源须由服务端解析连接");
            Map<String,Object> cfg=new LinkedHashMap<>(registered == null
                    ? registeredSources.resolve(TenantContext.requireTenantId(),id) : registered.getOrDefault(id, Map.of()));
            if(cfg.isEmpty()||"0".equals(String.valueOf(cfg.get("showConnect"))))throw new IllegalStateException("字典数据源不存在或未配置连接");
            cfg.put("datasourceId",id);
            for(String[] aliases:List.of(new String[]{"username","dbUser"},new String[]{"password","dbPassword"},new String[]{"host","dbIp"},new String[]{"port","dbPort"},new String[]{"database","dbMetaDbName"},new String[]{"jdbcUrl","jdbcURL"}))if(!cfg.containsKey(aliases[0])&&cfg.containsKey(aliases[1]))cfg.put(aliases[0],cfg.get(aliases[1]));
            return cfg;
        }
        return MAPPER.convertValue(dataSource, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    protected Object executeLookup(Map<String, Object> sourceConfig, LookupPlan lookup, Map<String, Object> row) {
        // Dictionary tables may be registered in a datasource other than the source table.
        // Prefer the lookup-level datasource so preview and the deployed NiFi flow use the same connection.
        Map<String, Object> lookupConfig = lookup.dataSource() == null || lookup.dataSource().isEmpty()
                ? sourceConfig : lookup.dataSource();
        if (lookupConfig == null || lookupConfig.isEmpty()) {
            throw new IllegalStateException("sourceConfig is required when preview contains lookup rules");
        }
        try (Connection connection = DriverManager.getConnection(buildJdbcUrl(lookupConfig),
                stringValue(lookupConfig.get("username")),
                stringValue(lookupConfig.get("password")));
             PreparedStatement ps = connection.prepareStatement(lookup.parameterizedSql())) {
            List<String> fields = lookup.parameterFields();
            for (int i = 0; i < fields.size(); i++) {
                ps.setObject(i + 1, row.get(fields.get(i)));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getObject(lookup.resultColumn());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("lookup preview failed: " + ex.getMessage(), ex);
        }
    }

    private String buildJdbcUrl(Map<String, Object> sourceConfig) {
        String configured = stringValue(sourceConfig.get("jdbcUrl"));
        if (configured == null || configured.isBlank()) {
            configured = stringValue(sourceConfig.get("jdbcURL"));
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String host = stringValue(sourceConfig.get("host"));
        String port = stringValue(sourceConfig.get("port"));
        String database = stringValue(sourceConfig.get("database"));
        String dbType = stringValue(sourceConfig.get("dbType")).toUpperCase(Locale.ROOT);
        if ("DM".equals(dbType) || "DAMENG".equals(dbType)) {
            return "jdbc:dm://" + host + ":" + port + "/" + database;
        }
        if ("POSTGRESQL".equals(dbType) || "GAUSSDB".equals(dbType) || "OPENGAUSS".equals(dbType)) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
        if ("ORACLE".equals(dbType)) {
            return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
        }
        if ("OCEANBASE_ORACLE".equals(dbType) || "OCEANBASEORACLE".equals(dbType)) {
            return "jdbc:oceanbase:oracle://" + host + ":" + port + "/" + database;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ParsedLookupSql parseLookupSql(String sql, String resultColumn, String defaultParameterField) {
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.isBlank()) throw new IllegalStateException("lookup.sql 不能为空");
        if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
            throw new IllegalStateException("lookup.sql 不能包含分号或 SQL 注释");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select ")) {
            throw new IllegalStateException("lookup.sql 仅支持 SELECT");
        }
        String resolvedResultColumn = StringUtils.hasText(resultColumn)
                ? columnName(resultColumn, "lookup.resultColumn")
                : inferLookupResultColumn(trimmed);
        List<String> parameterFields = new ArrayList<>();
        Matcher matcher = FIELD_REF.matcher(trimmed);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            parameterFields.add(columnName(matcher.group(1), "lookup 参数字段"));
            matcher.appendReplacement(buffer, "?");
        }
        matcher.appendTail(buffer);
        if (parameterFields.isEmpty()) {
            int questionMarkCount = countQuestionMarkPlaceholders(buffer.toString());
            if (questionMarkCount > 0) {
                if (!StringUtils.hasText(defaultParameterField)) {
                    throw new IllegalStateException("lookup.sql 使用 ? 占位符时必须先选择左侧源字段");
                }
                String field = columnName(defaultParameterField, "lookup 参数字段");
                for (int i = 0; i < questionMarkCount; i++) {
                    parameterFields.add(field);
                }
            } else {
                throw new IllegalStateException("lookup.sql 必须包含 ? 占位符，? 会自动绑定左侧源字段");
            }
        }
        return new ParsedLookupSql(buffer.toString(), List.copyOf(parameterFields), resolvedResultColumn);
    }

    private String inferLookupResultColumn(String sql) {
        String selectList = firstSelectList(sql);
        String firstColumn = firstTopLevelItem(selectList);
        if (!StringUtils.hasText(firstColumn)) {
            throw new IllegalStateException("lookup.resultColumn 为空时无法推断 SELECT 第一列，请填写结果列名");
        }
        String alias = trailingAlias(firstColumn);
        if (StringUtils.hasText(alias)) {
            return columnName(alias, "lookup.resultColumn");
        }
        String candidate = firstColumn.trim();
        int dot = candidate.lastIndexOf('.');
        if (dot >= 0) candidate = candidate.substring(dot + 1);
        candidate = unquoteIdentifier(candidate.trim());
        if (!candidate.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
            throw new IllegalStateException("lookup.resultColumn 为空时仅支持从 SELECT 第一列自动推断，请给第一列设置别名或填写结果列名");
        }
        return columnName(candidate, "lookup.resultColumn");
    }

    private String firstSelectList(String sql) {
        int from = findTopLevelFrom(sql, 6);
        if (from < 0) {
            throw new IllegalStateException("lookup.sql 必须包含 FROM");
        }
        return sql.substring(6, from).trim();
    }

    private int findTopLevelFrom(String text, int start) {
        int depth = 0;
        char quote = 0;
        for (int i = start; i <= text.length() - 4; i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') depth++;
            if (ch == ')' && depth > 0) depth--;
            if (depth == 0 && text.regionMatches(true, i, "from", 0, 4)
                    && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))
                    && (i + 4 >= text.length() || Character.isWhitespace(text.charAt(i + 4)))) {
                return i;
            }
        }
        return -1;
    }

    private String firstTopLevelItem(String selectList) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < selectList.length(); i++) {
            char ch = selectList.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') depth++;
            if (ch == ')' && depth > 0) depth--;
            if (depth == 0 && ch == ',') return selectList.substring(0, i).trim();
        }
        return selectList.trim();
    }

    private String trailingAlias(String expression) {
        Matcher asAlias = Pattern.compile("(?i)\\s+as\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*$").matcher(expression);
        if (asAlias.find()) return asAlias.group(1);
        Matcher implicitAlias = Pattern.compile("(?i)^.+\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*$").matcher(expression.trim());
        if (implicitAlias.matches() && !expression.trim().matches("[A-Za-z_][A-Za-z0-9_$.]*")) {
            return implicitAlias.group(1);
        }
        return "";
    }

    private String unquoteIdentifier(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("`") && value.endsWith("`"))
                || (value.startsWith("[") && value.endsWith("]"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private int countQuestionMarkPlaceholders(String sql) {
        int count = 0;
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '?') count++;
        }
        return count;
    }

    private List<String> suggestions(String missing, List<FieldMeta> fields) {
        String name = columnName(missing, "字段");
        return fields.stream().map(FieldMeta::pathOrName)
            .sorted((left, right) -> Double.compare(score(name, right).confidence(), score(name, left).confidence()))
                .limit(5).map(this::normalizePath).toList();
    }

    private MatchScore score(String source, String target) {
        String rawSource = source == null ? "" : source.trim();
        String rawTarget = target == null ? "" : target.trim();
        if (rawSource.equals(rawTarget) && !rawSource.isBlank()) return new MatchScore("英文名完全匹配", 1.0);
        if (rawSource.equalsIgnoreCase(rawTarget) && !rawSource.isBlank()) return new MatchScore("英文名忽略大小写匹配", 1.0);
        String a = normalizeName(source);
        String b = normalizeName(target);
        if (a.isBlank() || b.isBlank()) return new MatchScore("名称相似", 0);
        if (a.equals(b)) return new MatchScore("标准化字段名匹配", 0.98);
        int distance = levenshtein(a, b);
        double confidence = 1.0 - ((double) distance / Math.max(a.length(), b.length()));
        return new MatchScore("名称相似", Math.max(0, confidence));
    }

    private String normalizeName(String raw) {
        String s = raw == null ? "" : raw;
        if (s.startsWith("/")) s = s.substring(1);
        return s.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int previous = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int current = costs[j];
                costs[j] = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1), previous + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                previous = current;
            }
        }
        return costs[b.length()];
    }

    private String platformType(String type) {
        String t = type == null ? "STRING" : type.toUpperCase(Locale.ROOT);
        String base = t.replaceFirst("\\s*\\(.*$", "").trim();
        if (Set.of("RAW", "LONG RAW", "BYTEA", "IMAGE", "LONG VARBINARY").contains(base)) return "BLOB";
        if (Set.of("NUMBER", "DEC", "REAL", "FLOAT4", "FLOAT8").contains(base)) return "DECIMAL";
        if (Set.of("INT2", "INT4", "INT8", "SERIAL", "SMALLSERIAL", "BIGSERIAL").contains(base)) return "INT";
        if (t.contains("INT")) return "INT";
        if (t.contains("DATE") || t.contains("TIME")) return "DATETIME";
        if (t.contains("DECIMAL") || t.contains("NUMERIC") || t.contains("DOUBLE") || t.contains("FLOAT")) return "DECIMAL";
        if (t.contains("BOOL")) return "BOOL";
        if (t.contains("JSON")) return "JSON";
        if (t.contains("BLOB") || t.contains("BINARY")) return "BLOB";
        return "STRING";
    }

    private String defaultCast(String sourceType, String targetType) {
        if ("STRING".equals(sourceType) && "INT".equals(targetType)) return "toInt";
        if ("STRING".equals(sourceType) && "DATETIME".equals(targetType)) return "parseDate";
        if ("DATETIME".equals(sourceType) && "STRING".equals(targetType)) return "formatDate";
        return "cast";
    }

    private SchemaCompare schemaCompare(List<FieldMeta> sourceSchema, List<FieldMeta> targetSchema) {
        return new SchemaCompare(sourceSchema == null ? List.of() : sourceSchema, targetSchema == null ? List.of() : targetSchema);
    }

    public Set<String> allTransformNames() {
        return Set.of("upper", "lower", "trim", "ltrim", "rtrim", "substring", "replace", "replaceRegex", "concat", "split", "pad", "reverse",
                "toInt", "toLong", "toDouble", "toDecimal", "round", "ceil", "floor", "abs", "multiply", "divide", "add", "subtract",
                "parseDate", "formatDate", "toEpochMs", "fromEpochMs", "addDays", "addHours", "addMinutes", "addSeconds", "truncateDate",
                "coalesce", "nvl", "nullIf", "toBoolean", "md5", "sha1", "sha256", "base64Encode", "base64Decode", "urlEncode", "urlDecode",
                "jsonEncode", "jsonDecode", "jsonValue", "codeTableLookup", "enumMap", "cast", "expression");
    }

    public record FieldMeta(String name, String path, String type, Boolean nullable, Boolean isPk, Boolean hasDefault, Boolean sensitive) {
        public String pathOrName() { return path != null && !path.isBlank() ? path : name; }
        public boolean nullableEffective() { return nullable == null || nullable; }
        public boolean pkRequired() { return Boolean.TRUE.equals(isPk); }
        public boolean hasDefaultEffective() { return Boolean.TRUE.equals(hasDefault); }
    }
    public record CompiledMapping(String baseQuery, String finalQuery, List<LookupPlan> lookups,
                                  List<String> baseFields, List<String> outputFields, List<String> runtimeFields) {}
    public record LookupPlan(String targetField, String resultColumn, String parameterizedSql,
                             List<String> parameterFields, List<String> parameterRecordFields, String onMissing,
                             Map<String, Object> dataSource, boolean multiValue, MultiValueTranslation.Rule recordLookup) {
        public LookupPlan(String targetField, String resultColumn, String parameterizedSql,
                          List<String> parameterFields, List<String> parameterRecordFields, String onMissing,
                          Map<String, Object> dataSource, boolean multiValue) {
            this(targetField, resultColumn, parameterizedSql, parameterFields, parameterRecordFields,
                    onMissing, dataSource, multiValue, null);
        }
        public LookupPlan(String targetField, String resultColumn, String parameterizedSql,
                          List<String> parameterFields, List<String> parameterRecordFields, String onMissing,
                          Map<String, Object> dataSource) {
            this(targetField, resultColumn, parameterizedSql, parameterFields, parameterRecordFields,
                    onMissing, dataSource, false);
        }
    }
    public record MappingIssue(String path, Object value, String message, String severity, String code, List<String> suggestions) {}
    public record SchemaCompare(List<FieldMeta> sourceFields, List<FieldMeta> targetFields) {}
    public record ValidationResponse(boolean valid, List<MappingIssue> errors, List<MappingIssue> warnings, SchemaCompare schemaCompare) {}
    public record PreviewResponse(List<Map<String, Object>> resultRows, List<RowResult> rowResults, Map<String, Object> stats) {}
    public record RowResult(int rowIndex, boolean success, List<String> errors) {}
    public record Recommendation(String id, String from, String to, String fromType, String toType, String reason, double confidence) {}
    public record RecommendResponse(JsonNode spec, List<Recommendation> recommendations) {}
    private record ParsedLookupSql(String parameterizedSql, List<String> parameterFields, String resultColumn) {}
    private record MatchScore(String reason, double confidence) {}
}
