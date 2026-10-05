package com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc;

import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Bucket;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Difference;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Endpoint;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.FieldRule;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Listener;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Plan;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Row;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Stats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Bounded-memory relational reconciliation engine.
 *
 * <p>Rows are read with keyset pagination and merge-joined. No OFFSET paging
 * and no whole-table materialization are used.</p>
 */
@Service
public class JdbcReconciliationEngine {

    private static final int CHECKPOINT_INTERVAL = 2_000;

    private final RegisteredDataSourceResolver dataSources;

    public JdbcReconciliationEngine(RegisteredDataSourceResolver dataSources) {
        this.dataSources = dataSources;
    }

    public Map<String, Object> precheck(Plan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        try (Connection source = dataSources.open(plan.source());
             Connection target = dataSources.open(plan.target())) {
            validateEndpoint(source, plan.source(), plan.sourceKeys(), plan.rules(), true);
            validateEndpoint(target, plan.target(), plan.targetKeys(), plan.rules(), false);
            boolean sourceUnique = hasUniqueIndex(source, plan.source().tableName(), plan.sourceKeys());
            boolean targetUnique = hasUniqueIndex(target, plan.target().tableName(), plan.targetKeys());
            if (!sourceUnique) {
                warnings.add("来源表未发现覆盖全部对账键的唯一索引，大表对账可能很慢或无法可靠定位重复键");
            }
            if (!targetUnique) {
                warnings.add("目标表未发现覆盖全部对账键的唯一索引，大表对账可能很慢或无法可靠定位重复键");
            }
            result.put("success", true);
            result.put("sourceProduct", source.getMetaData().getDatabaseProductName());
            result.put("targetProduct", target.getMetaData().getDatabaseProductName());
            result.put("sourceUniqueKey", sourceUnique);
            result.put("targetUniqueKey", targetUnique);
            result.put("warnings", warnings);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", rootMessage(e));
            result.put("detail", stackSummary(e));
            result.put("warnings", warnings);
            return result;
        }
    }

    public NumericRange numericRange(Plan plan) {
        if (plan.sourceKeys().size() != 1 || plan.targetKeys().size() != 1) {
            return null;
        }
        try (Connection source = dataSources.open(plan.source());
             Connection target = dataSources.open(plan.target())) {
            BigDecimal[] sourceRange = minMax(
                    source,
                    plan.source(),
                    plan.sourceKeys().get(0),
                    plan.sourceIncrementField(),
                    plan.lowerWatermark(),
                    plan.upperWatermark()
            );
            BigDecimal[] targetRange = minMax(
                    target,
                    plan.target(),
                    plan.targetKeys().get(0),
                    plan.targetIncrementField(),
                    plan.lowerWatermark(),
                    plan.upperWatermark()
            );
            BigDecimal min = minimum(sourceRange[0], targetRange[0]);
            BigDecimal max = maximum(sourceRange[1], targetRange[1]);
            return min == null || max == null ? null : new NumericRange(min, max);
        } catch (Exception ignored) {
            return null;
        }
    }

    public Stats compare(Plan plan, Bucket bucket, Listener listener) throws Exception {
        try (Connection source = dataSources.open(plan.source());
             Connection target = dataSources.open(plan.target())) {
            source.setReadOnly(true);
            target.setReadOnly(true);
            if ("SUMMARY".equalsIgnoreCase(plan.compareMode())) {
                Stats stats = new Stats();
                long sourceCount = count(source, plan.source(), plan.sourceKeys().get(0),
                        plan.sourceIncrementField(), plan, bucket);
                long targetCount = count(target, plan.target(), plan.targetKeys().get(0),
                        plan.targetIncrementField(), plan, bucket);
                stats.addSource(sourceCount);
                stats.addTarget(targetCount);
                if (sourceCount == targetCount) {
                    stats.addMatched(sourceCount);
                } else if (sourceCount > targetCount) {
                    stats.addMissingTarget(sourceCount - targetCount);
                } else {
                    stats.addExtraTarget(targetCount - sourceCount);
                }
                listener.checkpoint(List.of(), List.of(), stats);
                return stats;
            }

            Stats stats = bucket.initialStats() == null ? new Stats() : bucket.initialStats();
            List<FieldRule> activeRules = "CONTENT".equalsIgnoreCase(plan.compareMode())
                    ? plan.rules().stream().filter(FieldRule::compareEnabled).toList()
                    : List.of();
            List<String> sourceFields = activeRules.stream()
                    .map(FieldRule::sourceField)
                    .toList();
            List<String> targetFields = activeRules.stream()
                    .map(FieldRule::targetField)
                    .toList();

            try (KeysetCursor sourceCursor = new KeysetCursor(
                    source, plan.source(), plan.sourceKeys(), sourceFields,
                    plan.sourceIncrementField(), plan, bucket,
                    bucket.checkpointSourceKey()
            );
                 KeysetCursor targetCursor = new KeysetCursor(
                         target, plan.target(), plan.targetKeys(), targetFields,
                         plan.targetIncrementField(), plan, bucket,
                         bucket.checkpointTargetKey()
                 )) {
                Row sourceRow = sourceCursor.next();
                Row targetRow = targetCursor.next();
                List<Object> lastSourceKey = bucket.checkpointSourceKey();
                List<Object> lastTargetKey = bucket.checkpointTargetKey();
                long sinceCheckpoint = 0;

                while (sourceRow != null || targetRow != null) {
                    if (listener.cancelled()) {
                        throw new ReconciliationCancelledException();
                    }
                    int comparison = sourceRow == null
                            ? 1
                            : targetRow == null
                                    ? -1
                                    : compareKeys(sourceRow.key(), targetRow.key());
                    if (comparison < 0) {
                        stats.source();
                        stats.missingTarget();
                        listener.difference(new Difference(
                                businessKey(sourceRow.key()),
                                "MISSING_TARGET",
                                null,
                                maskKey(sourceRow.key()),
                                null,
                                rowHash(sourceRow.values()),
                                null
                        ));
                        lastSourceKey = sourceRow.key();
                        sourceRow = sourceCursor.next();
                    } else if (comparison > 0) {
                        stats.target();
                        stats.extraTarget();
                        listener.difference(new Difference(
                                businessKey(targetRow.key()),
                                "EXTRA_TARGET",
                                null,
                                null,
                                maskKey(targetRow.key()),
                                null,
                                rowHash(targetRow.values())
                        ));
                        lastTargetKey = targetRow.key();
                        targetRow = targetCursor.next();
                    } else {
                        stats.source();
                        stats.target();
                        boolean equal = compareContent(
                                plan,
                                sourceRow,
                                targetRow,
                                activeRules,
                                stats,
                                listener
                        );
                        if (equal) {
                            stats.matched();
                        }
                        lastSourceKey = sourceRow.key();
                        lastTargetKey = targetRow.key();
                        sourceRow = sourceCursor.next();
                        targetRow = targetCursor.next();
                    }
                    sinceCheckpoint++;
                    if (sinceCheckpoint >= CHECKPOINT_INTERVAL) {
                        listener.checkpoint(lastSourceKey, lastTargetKey, stats);
                        sinceCheckpoint = 0;
                    }
                }
                listener.checkpoint(lastSourceKey, lastTargetKey, stats);
                return stats;
            }
        }
    }

    private boolean compareContent(
            Plan plan,
            Row source,
            Row target,
            List<FieldRule> rules,
            Stats stats,
            Listener listener
    ) {
        boolean equal = true;
        String sourceHash = rowHash(source.values());
        String targetHash = rowHash(target.values());
        for (FieldRule rule : rules) {
            Object sourceValue = source.values().get(rule.sourceField());
            Object targetValue = target.values().get(rule.targetField());
            if (!valuesEqual(plan, rule, sourceValue, targetValue)) {
                equal = false;
                stats.valueMismatch();
                listener.difference(new Difference(
                        businessKey(source.key()),
                        "VALUE_MISMATCH",
                        rule.sourceField() + " -> " + rule.targetField(),
                        mask(sourceValue, rule.maskRule()),
                        mask(targetValue, rule.maskRule()),
                        sourceHash,
                        targetHash
                ));
            }
        }
        return equal;
    }

    private boolean valuesEqual(
            Plan plan,
            FieldRule rule,
            Object source,
            Object target
    ) {
        boolean nullEqualsEmpty = rule.nullEqualsEmpty() == null
                ? plan.nullEqualsEmpty()
                : rule.nullEqualsEmpty();
        Object left = normalize(source, plan, rule, nullEqualsEmpty);
        Object right = normalize(target, plan, rule, nullEqualsEmpty);
        if (left == null || right == null) {
            return left == right;
        }
        BigDecimal tolerance = rule.numericTolerance() == null
                ? plan.numericTolerance()
                : rule.numericTolerance();
        BigDecimal leftNumber = decimal(left);
        BigDecimal rightNumber = decimal(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.subtract(rightNumber).abs().compareTo(tolerance) <= 0;
        }
        return Objects.equals(left, right);
    }

    private Object normalize(
            Object value,
            Plan plan,
            FieldRule rule,
            boolean nullEqualsEmpty
    ) {
        if (value == null) {
            return nullEqualsEmpty ? "" : null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Timestamp timestamp) {
            Instant instant = timestamp.toInstant();
            return truncate(instant, rule.datePrecision());
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return truncate(date.toInstant(), rule.datePrecision());
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros();
        }
        String normalized = String.valueOf(value);
        if (plan.trimStrings()
                || "TRIM".equalsIgnoreCase(rule.normalization())
                || "DEFAULT".equalsIgnoreCase(rule.normalization())) {
            normalized = normalized.trim();
        }
        if (nullEqualsEmpty && normalized.isEmpty()) {
            return "";
        }
        if (plan.ignoreCase() || "LOWER".equalsIgnoreCase(rule.normalization())) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        } else if ("UPPER".equalsIgnoreCase(rule.normalization())) {
            normalized = normalized.toUpperCase(Locale.ROOT);
        }
        return normalized;
    }

    private Instant truncate(Instant value, String precision) {
        if (precision == null || precision.isBlank() || "MILLIS".equalsIgnoreCase(precision)) {
            return value.truncatedTo(ChronoUnit.MILLIS);
        }
        return switch (precision.toUpperCase(Locale.ROOT)) {
            case "SECONDS" -> value.truncatedTo(ChronoUnit.SECONDS);
            case "MINUTES" -> value.truncatedTo(ChronoUnit.MINUTES);
            case "HOURS" -> value.truncatedTo(ChronoUnit.HOURS);
            case "DAYS" -> value.truncatedTo(ChronoUnit.DAYS);
            default -> value.truncatedTo(ChronoUnit.MILLIS);
        };
    }

    private int compareKeys(List<Object> left, List<Object> right) {
        for (int i = 0; i < left.size(); i++) {
            int value = compareKeyValue(left.get(i), right.get(i));
            if (value != 0) {
                return value;
            }
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareKeyValue(Object left, Object right) {
        if (left == null || right == null) {
            if (left == right) {
                return 0;
            }
            return left == null ? -1 : 1;
        }
        BigDecimal leftNumber = decimal(left);
        BigDecimal rightNumber = decimal(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        Instant leftInstant = instant(left);
        Instant rightInstant = instant(right);
        if (leftInstant != null && rightInstant != null) {
            return leftInstant.compareTo(rightInstant);
        }
        if (left.getClass().equals(right.getClass()) && left instanceof Comparable comparable) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number || value instanceof BigDecimal) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof LocalDate local) {
            return local.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private void validateEndpoint(
            Connection connection,
            Endpoint endpoint,
            List<String> keys,
            List<FieldRule> rules,
            boolean source
    ) throws SQLException {
        Set<String> columns = columns(connection, endpoint.tableName());
        if (columns.isEmpty()) {
            throw new SQLException("数据表不存在或当前账号无权读取: " + endpoint.tableName());
        }
        for (String key : keys) {
            requireColumn(columns, key, endpoint.tableName());
        }
        for (FieldRule rule : rules) {
            if (rule.compareEnabled()) {
                requireColumn(
                        columns,
                        source ? rule.sourceField() : rule.targetField(),
                        endpoint.tableName()
                );
            }
        }
    }

    private Set<String> columns(Connection connection, String tableName) throws SQLException {
        QualifiedName name = QualifiedName.parse(tableName);
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(
                connection.getCatalog(),
                name.schema(),
                name.table(),
                null
        )) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (columns.isEmpty()) {
            try (ResultSet result = metadata.getColumns(
                    connection.getCatalog(),
                    name.schema() == null ? null : name.schema().toUpperCase(Locale.ROOT),
                    name.table().toUpperCase(Locale.ROOT),
                    null
            )) {
                while (result.next()) {
                    columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private void requireColumn(Set<String> columns, String field, String table) throws SQLException {
        if (field == null || !columns.contains(field.toLowerCase(Locale.ROOT))) {
            throw new SQLException("数据表 " + table + " 不存在字段 " + field);
        }
    }

    private boolean hasUniqueIndex(
            Connection connection,
            String tableName,
            List<String> keys
    ) throws SQLException {
        QualifiedName name = QualifiedName.parse(tableName);
        Map<String, List<String>> unique = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(
                connection.getCatalog(),
                name.schema(),
                name.table(),
                true,
                false
        )) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    unique.computeIfAbsent(index, ignored -> new ArrayList<>())
                            .add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> normalized = keys.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        return unique.values().stream().anyMatch(columns -> columns.containsAll(normalized));
    }

    private BigDecimal[] minMax(
            Connection connection,
            Endpoint endpoint,
            String key,
            String incrementField,
            String lower,
            String upper
    ) throws SQLException {
        SqlBuilder sql = new SqlBuilder(connection);
        StringBuilder text = new StringBuilder("select min(")
                .append(sql.quote(key)).append("), max(")
                .append(sql.quote(key)).append(") from ")
                .append(sql.qualified(endpoint.tableName()));
        List<Object> parameters = new ArrayList<>();
        appendWindow(text, parameters, sql, incrementField, lower, upper);
        try (PreparedStatement statement = connection.prepareStatement(text.toString())) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new BigDecimal[]{null, null};
                }
                return new BigDecimal[]{
                        decimal(result.getObject(1)),
                        decimal(result.getObject(2))
                };
            }
        }
    }

    private long count(
            Connection connection,
            Endpoint endpoint,
            String firstKey,
            String incrementField,
            Plan plan,
            Bucket bucket
    ) throws SQLException {
        SqlBuilder sql = new SqlBuilder(connection);
        StringBuilder text = new StringBuilder("select count(1) from ")
                .append(sql.qualified(endpoint.tableName()));
        List<Object> parameters = new ArrayList<>();
        appendFilters(text, parameters, sql, firstKey, incrementField, plan, bucket, null);
        try (PreparedStatement statement = connection.prepareStatement(text.toString())) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> parameters,
            SqlBuilder identifiers,
            String firstKey,
            String incrementField,
            Plan plan,
            Bucket bucket,
            List<Object> lastKey
    ) {
        List<String> predicates = new ArrayList<>();
        if (bucket.keyLower() != null) {
            predicates.add(identifiers.quote(firstKey) + " >= ?");
            parameters.add(new BigDecimal(bucket.keyLower()));
        }
        if (bucket.keyUpper() != null) {
            predicates.add(
                    identifiers.quote(firstKey)
                            + (bucket.upperInclusive() ? " <= ?" : " < ?")
            );
            parameters.add(new BigDecimal(bucket.keyUpper()));
        }
        if (incrementField != null && !incrementField.isBlank()) {
            if (plan.lowerWatermark() != null && !plan.lowerWatermark().isBlank()) {
                predicates.add(identifiers.quote(incrementField) + " > ?");
                parameters.add(plan.lowerWatermark());
            }
            if (plan.upperWatermark() != null && !plan.upperWatermark().isBlank()) {
                predicates.add(identifiers.quote(incrementField) + " <= ?");
                parameters.add(plan.upperWatermark());
            }
        }
        if (!predicates.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", predicates));
        }
    }

    private void appendWindow(
            StringBuilder sql,
            List<Object> parameters,
            SqlBuilder identifiers,
            String incrementField,
            String lower,
            String upper
    ) {
        if (incrementField == null || incrementField.isBlank()) {
            return;
        }
        List<String> predicates = new ArrayList<>();
        if (lower != null && !lower.isBlank()) {
            predicates.add(identifiers.quote(incrementField) + " > ?");
            parameters.add(lower);
        }
        if (upper != null && !upper.isBlank()) {
            predicates.add(identifiers.quote(incrementField) + " <= ?");
            parameters.add(upper);
        }
        if (!predicates.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", predicates));
        }
    }

    private String businessKey(List<Object> key) {
        return key.stream().map(this::canonical).reduce((a, b) -> a + "|" + b).orElse("");
    }

    private String maskKey(List<Object> key) {
        return key.stream().map(value -> mask(value, "PARTIAL"))
                .reduce((a, b) -> a + "|" + b).orElse("");
    }

    private String rowHash(Map<String, Object> values) {
        StringBuilder canonical = new StringBuilder();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey())
                        .append('=')
                        .append(canonical(entry.getValue()))
                        .append('\u001f'));
        return sha256(canonical.toString());
    }

    private String canonical(Object value) {
        if (value == null) {
            return "<NULL>";
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        }
        Instant instant = instant(value);
        if (instant != null) {
            return instant.toString();
        }
        return String.valueOf(value);
    }

    private String mask(Object value, String rule) {
        if (value == null) {
            return null;
        }
        if ("HASH".equalsIgnoreCase(rule)) {
            return sha256(canonical(value));
        }
        if ("NONE".equalsIgnoreCase(rule)) {
            return truncateText(canonical(value), 2000);
        }
        String text = canonical(value);
        if (text.length() <= 4) {
            return "*".repeat(text.length());
        }
        int visible = Math.min(3, text.length() / 4);
        return truncateText(
                text.substring(0, visible)
                        + "*".repeat(Math.min(12, text.length() - visible * 2))
                        + text.substring(text.length() - visible),
                2000
        );
    }

    private String truncateText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String stackSummary(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                result.append("\nCaused by: ");
            }
            result.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage());
            current = current.getCause();
            depth++;
        }
        return result.toString();
    }

    private BigDecimal minimum(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.min(right);
    }

    private BigDecimal maximum(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.max(right);
    }

    private void bind(PreparedStatement statement, Collection<?> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            statement.setObject(index++, parameter);
        }
    }

    public record NumericRange(BigDecimal min, BigDecimal max) {

        public List<RangeBucket> split(int requestedCount) {
            int count = Math.max(1, requestedCount);
            BigDecimal width = max.subtract(min).add(BigDecimal.ONE)
                    .divide(BigDecimal.valueOf(count), 0, RoundingMode.CEILING);
            if (width.signum() <= 0) {
                return List.of(new RangeBucket(min, max, true));
            }
            List<RangeBucket> result = new ArrayList<>();
            BigDecimal lower = min;
            for (int i = 0; i < count && lower.compareTo(max) <= 0; i++) {
                BigDecimal upper = lower.add(width);
                boolean last = i == count - 1 || upper.compareTo(max) > 0;
                result.add(new RangeBucket(lower, last ? max : upper, last));
                lower = upper;
            }
            return result;
        }
    }

    public record RangeBucket(BigDecimal lower, BigDecimal upper, boolean upperInclusive) {
    }

    public static class ReconciliationCancelledException extends RuntimeException {
        public ReconciliationCancelledException() {
            super("对账任务已取消");
        }
    }

    private final class KeysetCursor implements AutoCloseable {

        private final Connection connection;
        private final Endpoint endpoint;
        private final List<String> keys;
        private final List<String> fields;
        private final String incrementField;
        private final Plan plan;
        private final Bucket bucket;
        private final SqlBuilder identifiers;
        private List<Object> lastKey;
        private List<Row> page = List.of();
        private int index;
        private boolean exhausted;

        private KeysetCursor(
                Connection connection,
                Endpoint endpoint,
                List<String> keys,
                List<String> fields,
                String incrementField,
                Plan plan,
                Bucket bucket,
                List<Object> checkpoint
        ) throws SQLException {
            this.connection = connection;
            this.endpoint = endpoint;
            this.keys = keys;
            this.fields = fields;
            this.incrementField = incrementField;
            this.plan = plan;
            this.bucket = bucket;
            this.identifiers = new SqlBuilder(connection);
            this.lastKey = checkpoint == null || checkpoint.isEmpty() ? null : checkpoint;
        }

        private Row next() throws SQLException {
            if (index >= page.size() && !exhausted) {
                loadPage();
            }
            if (index >= page.size()) {
                return null;
            }
            Row row = page.get(index++);
            lastKey = row.key();
            return row;
        }

        private void loadPage() throws SQLException {
            List<String> selectFields = new ArrayList<>(keys);
            for (String field : fields) {
                if (selectFields.stream().noneMatch(value -> value.equalsIgnoreCase(field))) {
                    selectFields.add(field);
                }
            }
            StringBuilder sql = new StringBuilder("select ")
                    .append(selectFields.stream().map(identifiers::quote)
                            .reduce((a, b) -> a + ", " + b).orElseThrow())
                    .append(" from ")
                    .append(identifiers.qualified(endpoint.tableName()));
            List<Object> parameters = new ArrayList<>();
            List<String> predicates = new ArrayList<>();
            if (bucket.keyLower() != null) {
                predicates.add(identifiers.quote(keys.get(0)) + " >= ?");
                parameters.add(new BigDecimal(bucket.keyLower()));
            }
            if (bucket.keyUpper() != null) {
                predicates.add(identifiers.quote(keys.get(0))
                        + (bucket.upperInclusive() ? " <= ?" : " < ?"));
                parameters.add(new BigDecimal(bucket.keyUpper()));
            }
            if (incrementField != null && !incrementField.isBlank()) {
                if (plan.lowerWatermark() != null && !plan.lowerWatermark().isBlank()) {
                    predicates.add(identifiers.quote(incrementField) + " > ?");
                    parameters.add(plan.lowerWatermark());
                }
                if (plan.upperWatermark() != null && !plan.upperWatermark().isBlank()) {
                    predicates.add(identifiers.quote(incrementField) + " <= ?");
                    parameters.add(plan.upperWatermark());
                }
            }
            if (lastKey != null && !lastKey.isEmpty()) {
                predicates.add(keysetPredicate(keys, parameters, lastKey));
            }
            if (!predicates.isEmpty()) {
                sql.append(" where ").append(String.join(" and ", predicates));
            }
            sql.append(" order by ")
                    .append(keys.stream().map(identifiers::quote)
                            .reduce((a, b) -> a + ", " + b).orElseThrow());
            appendLimit(sql, connection, plan.fetchSize());

            List<Row> loaded = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    sql.toString(),
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            )) {
                statement.setFetchSize(plan.fetchSize());
                statement.setMaxRows(plan.fetchSize());
                bind(statement, parameters);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        List<Object> keyValues = new ArrayList<>(keys.size());
                        for (int i = 0; i < keys.size(); i++) {
                            keyValues.add(result.getObject(i + 1));
                        }
                        Map<String, Object> values = new LinkedHashMap<>();
                        for (int i = 0; i < selectFields.size(); i++) {
                            values.put(selectFields.get(i), result.getObject(i + 1));
                        }
                        loaded.add(new Row(List.copyOf(keyValues), values));
                    }
                }
            }
            page = loaded;
            index = 0;
            exhausted = loaded.size() < plan.fetchSize();
        }

        private String keysetPredicate(
                List<String> keyFields,
                List<Object> parameters,
                List<Object> values
        ) {
            List<String> branches = new ArrayList<>();
            for (int greaterIndex = 0; greaterIndex < keyFields.size(); greaterIndex++) {
                List<String> parts = new ArrayList<>();
                for (int equalIndex = 0; equalIndex < greaterIndex; equalIndex++) {
                    parts.add(identifiers.quote(keyFields.get(equalIndex)) + " = ?");
                    parameters.add(values.get(equalIndex));
                }
                parts.add(identifiers.quote(keyFields.get(greaterIndex)) + " > ?");
                parameters.add(values.get(greaterIndex));
                branches.add("(" + String.join(" and ", parts) + ")");
            }
            return "(" + String.join(" or ", branches) + ")";
        }

        @Override
        public void close() {
            page = List.of();
        }
    }

    private void appendLimit(StringBuilder sql, Connection connection, int limit) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        if (product.contains("sql server")) {
            sql.append(" offset 0 rows fetch next ").append(limit).append(" rows only");
        } else if (product.contains("oracle") || product.contains("dm dbms")) {
            sql.append(" fetch first ").append(limit).append(" rows only");
        } else {
            sql.append(" limit ").append(limit);
        }
    }

    private static final class SqlBuilder {

        private final String quote;

        private SqlBuilder(Connection connection) throws SQLException {
            String value = connection.getMetaData().getIdentifierQuoteString();
            this.quote = value == null ? "" : value.trim();
        }

        private String quote(String identifier) {
            String value = validate(identifier);
            return quote.isEmpty() ? value : quote + value.replace(quote, quote + quote) + quote;
        }

        private String qualified(String identifier) {
            return QualifiedName.parse(identifier).parts().stream()
                    .map(this::quote)
                    .reduce((a, b) -> a + "." + b)
                    .orElseThrow();
        }

        private String validate(String identifier) {
            if (identifier == null
                    || identifier.isBlank()
                    || !identifier.matches("[\\p{L}\\p{N}_$#]+")) {
                throw new IllegalArgumentException("非法数据库标识符: " + identifier);
            }
            return identifier;
        }
    }

    private record QualifiedName(String schema, String table, List<String> parts) {

        private static QualifiedName parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("数据表名不能为空");
            }
            String[] values = value.trim().split("\\.");
            if (values.length == 1) {
                return new QualifiedName(null, values[0], List.of(values[0]));
            }
            if (values.length == 2) {
                return new QualifiedName(values[0], values[1], List.of(values[0], values[1]));
            }
            throw new IllegalArgumentException("不支持的数据表限定名: " + value);
        }
    }
}
