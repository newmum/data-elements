package com.linewell.dataelement.feature.dataquality.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Evaluation;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.IssueSample;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.MetricResult;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.NumericRange;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Plan;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Rule;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Shard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class JdbcDataQualityEngine {

    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#]*");

    private final RegisteredJdbcDataSourceResolver dataSources;
    private final DataQualityProperties properties;
    private final ObjectMapper objectMapper;

    public JdbcDataQualityEngine(
            RegisteredJdbcDataSourceResolver dataSources,
            DataQualityProperties properties,
            ObjectMapper objectMapper
    ) {
        this.dataSources = dataSources;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> precheck(Plan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = open(plan)) {
            String table = qualified(connection, plan.endpoint().tableName());
            List<String> columns = new ArrayList<>();
            if (!blank(plan.shardKey())) columns.add(plan.shardKey());
            for (Rule rule : plan.rules()) {
                if (!blank(rule.columnName())) columns.add(rule.columnName());
                if (!blank(rule.relatedColumn())) columns.add(rule.relatedColumn());
                validateRule(rule);
                columns.addAll(uniqueColumns(rule));
                if ("PROFILE".equals(rule.type())) {
                    if (plan.shardCount()!=1) throw new IllegalArgumentException("字段探查使用单分片保证统计预算一致");
                    profileLimit(rule);
                } else if (!"UNIQUE".equals(rule.type())) {
                    SqlContext context = new SqlContext(connection,plan,new Shard("precheck",0,null,null,true));
                    Predicate condition = predicate(context,rule);
                    try (PreparedStatement statement=prepare(connection,"SELECT COUNT(*) FROM "+context.table()+" WHERE 1=0 AND ("+condition.sql()+")",plan.timeoutSeconds())) {
                        bind(statement,condition.arguments());
                        try (ResultSet ignored=statement.executeQuery()) { }
                    }
                }
            }
            List<String> projectionColumns = new ArrayList<>();
            for (String column : columns.stream().distinct().toList()) {
                projectionColumns.add(quote(connection, column));
            }
            String projection = projectionColumns.isEmpty()
                    ? "*"
                    : String.join(",", projectionColumns);
            try (PreparedStatement statement = prepare(connection,
                    "SELECT " + projection + " FROM " + table + " WHERE 1 = 0", plan.timeoutSeconds()
            )) {
                try (ResultSet rows = statement.executeQuery()) {
                    if (plan.shardCount() > 1 && !blank(plan.shardKey())) {
                        ResultSetMetaData metadata = rows.getMetaData();
                        for (int column = 1; column <= metadata.getColumnCount(); column++) {
                            if (plan.shardKey().equalsIgnoreCase(metadata.getColumnName(column))
                                    && !numericType(metadata.getColumnType(column))) {
                                throw new IllegalArgumentException("多分片质检的分片键必须是数值字段");
                            }
                        }
                    }
                }
            }
            if (plan.shardCount() > 1 && blank(plan.shardKey())) {
                throw new IllegalArgumentException("多分片质检必须配置稳定的数值分片键");
            }
            // Empty tables have no min/max, but their numeric schema is still valid.
            // The worker uses a single unbounded shard when no range exists.
            if (plan.shardCount() > 1) numericRange(connection, plan);
            result.put("success", true);
            result.put("message", "数据源、数据表、字段和规则预检通过");
            result.put("database", connection.getMetaData().getDatabaseProductName());
            result.put("table", plan.endpoint().tableName());
            result.put("ruleCount", plan.rules().size());
        } catch (Exception exception) {
            result.put("success", false);
            result.put("message", rootMessage(exception));
            result.put("detail", exceptionChain(exception));
        }
        return result;
    }

    public NumericRange numericRange(Plan plan) {
        if (blank(plan.shardKey())) return null;
        try (Connection connection = open(plan)) {
            return numericRange(connection, plan);
        } catch (Exception exception) {
            throw new IllegalStateException("读取质检分片范围失败: " + rootMessage(exception), exception);
        }
    }

    public Evaluation evaluate(Plan plan, Shard shard) {
        try (Connection connection = open(plan)) {
            SqlContext sql = new SqlContext(connection, plan, shard);
            if (plan.rules().stream().anyMatch(r -> "PROFILE".equals(r.type()))) {
                if(plan.shardCount()!=1 || plan.rules().stream().anyMatch(r -> !"PROFILE".equals(r.type()))) throw new IllegalArgumentException("字段探查不能与评分规则混合或使用多分片");
                return evaluateProfile(sql);
            }
            SqlContext globalSql = new SqlContext(
                    connection,
                    plan,
                    new Shard(shard.id(), shard.number(), null, null, true)
            );
            List<Rule> ordinary = plan.rules().stream()
                    .filter(rule -> !"UNIQUE".equals(rule.type()))
                    .toList();
            List<MetricResult> metrics = new ArrayList<>();
            List<IssueSample> samples = new ArrayList<>();
            long rowCount = evaluateOrdinary(sql, ordinary, metrics);
            if (shard.number() == 0) {
                long globalRowCount = countRows(globalSql);
                for (Rule rule : plan.rules()) {
                    if ("UNIQUE".equals(rule.type())) {
                        metrics.add(evaluateUnique(globalSql, rule, globalRowCount));
                    }
                }
            }
            int remaining = Math.max(0, plan.sampleLimit()/Math.max(1,plan.shardCount()) + (shard.number()<plan.sampleLimit()%Math.max(1,plan.shardCount())?1:0));
            for (MetricResult metric : metrics) {
                if (remaining == 0 || metric.violationCount() == 0) continue;
                int quota = Math.min(remaining, Math.min(50, safeInt(metric.violationCount())));
                SqlContext sampleContext = "UNIQUE".equals(metric.rule().type()) ? globalSql : sql;
                List<IssueSample> found = sample(sampleContext, metric.rule(), quota);
                samples.addAll(found);
                remaining -= found.size();
            }
            long checked = metrics.stream().mapToLong(MetricResult::checkedCount).sum();
            long violations = metrics.stream().mapToLong(MetricResult::violationCount).sum();
            return new Evaluation(rowCount, checked, violations, metrics, samples);
        } catch (Exception exception) {
            throw new IllegalStateException("执行数据质量分片失败: " + rootMessage(exception), exception);
        }
    }

    private long countRows(SqlContext context) throws Exception {
        String sql = "SELECT COUNT(*) AS checked_count FROM " + context.table();
        try (PreparedStatement statement = prepare(context.connection(),sql,context.plan().timeoutSeconds());
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong("checked_count");
        }
    }

    private long evaluateOrdinary(
            SqlContext context,
            List<Rule> rules,
            List<MetricResult> metrics
    ) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS checked_count");
        List<Object> arguments = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            Predicate predicate = predicate(context, rules.get(index));
            sql.append(", COALESCE(SUM(CASE WHEN ")
                    .append(predicate.sql()).append(" THEN 1 ELSE 0 END), 0) AS violation_")
                    .append(index);
            arguments.addAll(predicate.arguments());
        }
        sql.append(" FROM ").append(context.table()).append(context.shardWhere());
        arguments.addAll(context.shardArguments());
        try (PreparedStatement statement = prepare(context.connection(),sql.toString(),context.plan().timeoutSeconds())) {
            bind(statement, arguments);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                long checked = rows.getLong("checked_count");
                for (int index = 0; index < rules.size(); index++) {
                    long violations = rows.getLong("violation_" + index);
                    metrics.add(metric(rules.get(index), checked, violations));
                }
                return checked;
            }
        }
    }

    private MetricResult evaluateUnique(SqlContext context, Rule rule, long checked)
            throws Exception {
        String column = String.join(",",uniqueColumns(rule).stream().map(context::column).toList());
        String sql = "SELECT COALESCE(SUM(group_count - 1), 0) AS violations FROM ("
                + "SELECT COUNT(*) AS group_count FROM " + context.table()
                + context.shardWhere() + " GROUP BY " + column + " HAVING COUNT(*) > 1) dq_group";
        try (PreparedStatement statement = prepare(context.connection(),sql,context.plan().timeoutSeconds())) {
            bind(statement, context.shardArguments());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return metric(rule, checked, rows.getLong("violations"));
            }
        }
    }

    private List<IssueSample> sample(SqlContext context, Rule rule, int limit) throws Exception {
        if (limit <= 0) return List.of();
        if ("UNIQUE".equals(rule.type())) {
            return sampleDuplicates(context, rule, limit);
        }
        Predicate predicate = predicate(context, rule);
        String key = blank(context.plan().shardKey())
                ? context.column(rule.columnName())
                : context.column(context.plan().shardKey());
        String value = context.column(rule.columnName());
        String sql = "SELECT " + key + " AS dq_row_key, " + value + " AS dq_issue_value FROM "
                + context.table() + " WHERE (" + predicate.sql() + ")"
                + context.shardAnd() + " ORDER BY " + key + context.limit(limit);
        List<Object> arguments = new ArrayList<>(predicate.arguments());
        arguments.addAll(context.shardArguments());
        return readSamples(context, rule, sql, arguments);
    }

    private List<IssueSample> sampleDuplicates(SqlContext context, Rule rule, int limit)
            throws Exception {
        String column = String.join(",",uniqueColumns(rule).stream().map(context::column).toList());
        String sql = "SELECT " + context.column(rule.columnName()) + " AS dq_row_key, COUNT(*) AS dq_issue_value FROM "
                + context.table() + context.shardWhere() + " GROUP BY " + column
                + " HAVING COUNT(*) > 1 ORDER BY " + column + context.limit(limit);
        return readSamples(context, rule, sql, context.shardArguments());
    }

    private List<IssueSample> readSamples(
            SqlContext context,
            Rule rule,
            String sql,
            List<Object> arguments
    ) throws Exception {
        List<IssueSample> samples = new ArrayList<>();
        try (PreparedStatement statement = prepare(context.connection(),sql,context.plan().timeoutSeconds())) {
            bind(statement, arguments);
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                while (rows.next()) {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        snapshot.put(metadata.getColumnLabel(index), rows.getObject(index));
                    }
                    samples.add(new IssueSample(
                            rule,
                            text(rows.getObject("dq_row_key")),
                            text(rows.getObject("dq_issue_value")),
                            objectMapper.writeValueAsString(snapshot)
                    ));
                }
            }
        }
        return samples;
    }

    private Predicate predicate(SqlContext context, Rule rule) {
        String column = context.column(rule.columnName());
        Map<String, Object> parameters = rule.parameters();
        return switch (rule.type()) {
            case "NOT_NULL" -> new Predicate(column + " IS NULL", List.of());
            case "NOT_EMPTY" -> new Predicate(
                    column + " IS NULL OR TRIM(" + column + ") " + (List.of("oracle","dm").contains(context.vendor())?"IS NULL":"= ''"), List.of()
            );
            case "RANGE" -> rangePredicate(column, parameters);
            case "ENUM" -> enumPredicate(column, parameters);
            case "LENGTH" -> lengthPredicate(context, column, parameters);
            case "REGEX" -> regexPredicate(context, column, required(parameters, "pattern"));
            case "TIMELINESS" -> timelinessPredicate(column, parameters);
            case "REFERENCE" -> referencePredicate(context, parameters);
            default -> throw new IllegalArgumentException("不支持的质检规则类型: " + rule.type());
        };
    }

    private Predicate rangePredicate(String column, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (!blank(text(parameters.get("min")))) {
            conditions.add(column + " < ?");
            values.add(new BigDecimal(text(parameters.get("min"))));
        }
        if (!blank(text(parameters.get("max")))) {
            conditions.add(column + " > ?");
            values.add(new BigDecimal(text(parameters.get("max"))));
        }
        if (conditions.isEmpty()) throw new IllegalArgumentException("范围规则至少配置最小值或最大值");
        if(!blank(text(parameters.get("min")))&&!blank(text(parameters.get("max")))&&new BigDecimal(text(parameters.get("min"))).compareTo(new BigDecimal(text(parameters.get("max"))))>0) throw new IllegalArgumentException("范围最小值不能大于最大值");
        return new Predicate(column + " IS NULL OR " + String.join(" OR ", conditions), values);
    }

    private Predicate enumPredicate(String column, Map<String, Object> parameters) {
        Object raw = parameters.get("values");
        List<?> values = raw instanceof List<?> list ? list : List.of();
        if (values.isEmpty()) throw new IllegalArgumentException("枚举规则必须配置允许值");
        return new Predicate(
                column + " IS NULL OR " + column + " NOT IN ("
                        + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")",
                new ArrayList<>(values)
        );
    }

    private Predicate lengthPredicate(
            SqlContext context,
            String column,
            Map<String, Object> parameters
    ) {
        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        String function = context.lengthFunction();
        if (!blank(text(parameters.get("minLength")))) {
            conditions.add(function + "(" + column + ") < ?");
            values.add(Integer.parseInt(text(parameters.get("minLength"))));
        }
        if (!blank(text(parameters.get("maxLength")))) {
            conditions.add(function + "(" + column + ") > ?");
            values.add(Integer.parseInt(text(parameters.get("maxLength"))));
        }
        if (conditions.isEmpty()) throw new IllegalArgumentException("长度规则至少配置最小或最大长度");
        for(Object value:values) if(((Number)value).intValue()<0) throw new IllegalArgumentException("长度不能小于零");
        if(!blank(text(parameters.get("minLength")))&&!blank(text(parameters.get("maxLength")))&&Integer.parseInt(text(parameters.get("minLength")))>Integer.parseInt(text(parameters.get("maxLength")))) throw new IllegalArgumentException("最小长度不能大于最大长度");
        return new Predicate(column + " IS NULL OR " + String.join(" OR ", conditions), values);
    }

    private Predicate regexPredicate(SqlContext context, String column, String pattern) {
        Pattern.compile(pattern);
        return switch (context.vendor()) {
            case "sqlserver" -> throw new IllegalArgumentException("此 SQL Server 不支持共享正则 SQL；不能把未执行的检查视为通过");
            case "postgresql", "kingbase" -> new Predicate(
                    column + " IS NULL OR NOT (" + column + " ~ ?)", List.of(pattern)
            );
            case "oracle", "dm" -> new Predicate(
                    column + " IS NULL OR NOT REGEXP_LIKE(" + column + ", ?)", List.of(pattern)
            );
            default -> new Predicate(
                    column + " IS NULL OR NOT (" + column + " REGEXP ?)", List.of(pattern)
            );
        };
    }

    private Predicate timelinessPredicate(String column, Map<String,Object> p) {
        int age=boundedInteger(p,"maxAgeMinutes",1,5256000), future=boundedInteger(p,"futureToleranceMinutes",0,5256000);
        java.time.LocalDateTime anchor=java.time.LocalDateTime.parse(required(p,"evaluationTime"));
        return new Predicate(column+" IS NULL OR "+column+" < ? OR "+column+" > ?",List.of(java.sql.Timestamp.valueOf(anchor.minusMinutes(age)),java.sql.Timestamp.valueOf(anchor.plusMinutes(future))));
    }

    private Predicate referencePredicate(SqlContext context,Map<String,Object> p) {
        List<String> sources=stringList(p.get("sourceColumns")), targets=stringList(p.get("targetColumns"));
        if(sources.isEmpty()||sources.size()!=targets.size()||sources.size()>16) throw new IllegalArgumentException("引用规则须配置 1–16 对字段");
        List<String> conditions=new ArrayList<>(),nulls=new ArrayList<>();
        for(int i=0;i<sources.size();i++) {
            String source=context.column(sources.get(i));nulls.add(source+" IS NULL");
            conditions.add("dq_ref."+context.identifier(targets.get(i))+" = "+source);
        }
        String target;
        try {target=qualified(context.connection(),required(p,"resolvedTargetTable"));}catch(Exception e){throw new IllegalArgumentException("目标表配置无效",e);}
        String missing="NOT EXISTS (SELECT 1 FROM "+target+" dq_ref WHERE "+String.join(" AND ",conditions)+")";
        String anyNull=String.join(" OR ",nulls);
        return new Predicate(Boolean.TRUE.equals(p.get("allowNull"))?"NOT ("+anyNull+") AND "+missing:"("+anyNull+") OR "+missing,List.of());
    }

    private List<String> uniqueColumns(Rule rule) {
        if(!"UNIQUE".equals(rule.type())) return List.of();
        List<String> values=stringList(rule.parameters().get("columns"));
        if(values.isEmpty()) values=List.of(rule.columnName());
        if(values.size()>16 || values.stream().distinct().count()!=values.size()) throw new IllegalArgumentException("联合唯一性字段须为 1–16 个不重复字段");
        return values;
    }

    private List<String> stringList(Object value) {
        if(!(value instanceof List<?> list)) return List.of();
        return list.stream().map(v->{if(!(v instanceof String s)||s.isBlank())throw new IllegalArgumentException("字段列表无效");return s;}).toList();
    }

    private int boundedInteger(Map<String,Object> p,String key,int minimum,int maximum) {
        try {int v=Integer.parseInt(String.valueOf(p.get(key)));if(v<minimum||v>maximum)throw new IllegalArgumentException();return v;}catch(Exception e){throw new IllegalArgumentException(key+" 必须为 "+minimum+"–"+maximum+" 的整数");}
    }

    private int profileLimit(Rule rule) {
        String mode=required(rule.parameters(),"scanMode");
        if("FULL".equals(mode)) return 0;
        if(!"SAMPLE".equals(mode))throw new IllegalArgumentException("探查模式必须为 SAMPLE 或 FULL");
        return boundedInteger(rule.parameters(),"scanLimit",1,10000);
    }

    /** Aggregate-only results; a bounded preview is not advertised as full-table statistics. */
    private Evaluation evaluateProfile(SqlContext context) throws Exception {
        if(context.plan().rules().size()>64)throw new IllegalArgumentException("一次最多探查 64 个字段");
        List<MetricResult> metrics=new ArrayList<>();
        int budget=profileLimit(context.plan().rules().getFirst());
        String table=qualified(context.connection(),context.plan().endpoint().tableName());
        String input=budget==0?table:"("+("sqlserver".equals(context.vendor())?"SELECT TOP "+budget+" * FROM "+table:"SELECT * FROM "+table+context.limit(budget))+ ") dq_profile";
        List<Boolean> textTypes=new ArrayList<>(),distinctTypes=new ArrayList<>();
        String columns=context.plan().rules().stream().map(r->context.identifier(r.columnName())).collect(java.util.stream.Collectors.joining(","));
        try(PreparedStatement schema=prepare(context.connection(),"SELECT "+columns+" FROM "+table+" WHERE 1=0",context.plan().timeoutSeconds());ResultSet rows=schema.executeQuery()) {
            for(int i=1;i<=context.plan().rules().size();i++) {
                int jdbcType=rows.getMetaData().getColumnType(i);
                textTypes.add(List.of(java.sql.Types.CHAR,java.sql.Types.VARCHAR,java.sql.Types.NCHAR,java.sql.Types.NVARCHAR,java.sql.Types.LONGVARCHAR,java.sql.Types.LONGNVARCHAR).contains(jdbcType));
                distinctTypes.add(!List.of(java.sql.Types.CLOB,java.sql.Types.NCLOB,java.sql.Types.BLOB,java.sql.Types.LONGVARBINARY,java.sql.Types.OTHER,java.sql.Types.ARRAY,java.sql.Types.SQLXML).contains(jdbcType));
            }
        }
        StringBuilder query=new StringBuilder("SELECT COUNT(*) AS scanned");
        for(int i=0;i<context.plan().rules().size();i++) {
            Rule rule=context.plan().rules().get(i);
            if(profileLimit(rule)!=budget)throw new IllegalArgumentException("同一探查的字段必须使用相同扫描预算");
            String column=context.identifier(rule.columnName());boolean textual=textTypes.get(i),distinctSupported=distinctTypes.get(i);
            String blank=textual?(List.of("oracle","dm").contains(context.vendor())?"TRIM("+column+") IS NULL":"TRIM("+column+") = ''"):"1=0";
            query.append(",COUNT(").append(column).append(") AS n_").append(i)
                    .append(",COALESCE(SUM(CASE WHEN ").append(column).append(" IS NOT NULL AND (").append(blank).append(") THEN 1 ELSE 0 END),0) AS b_").append(i);
            if(distinctSupported)query.append(",COUNT(DISTINCT ").append(column).append(") AS d_").append(i);
        }
        // One aggregate statement makes every field observe the same bounded input,
        // rather than choosing a potentially different sample for each field.
        long scanned;
        try(PreparedStatement statement=prepare(context.connection(),query+" FROM "+input,context.plan().timeoutSeconds());ResultSet rows=statement.executeQuery()) {
            rows.next();scanned=rows.getLong("scanned");
            for(int i=0;i<context.plan().rules().size();i++) {
            Rule rule=context.plan().rules().get(i);boolean textual=textTypes.get(i),distinctSupported=distinctTypes.get(i);
            long nonNull=rows.getLong("n_"+i),empty=rows.getLong("b_"+i);
            Map<String,Object> stats=new LinkedHashMap<>();
            stats.put("scanMode",budget==0?"FULL":"SAMPLE");stats.put("scanLimit",budget);stats.put("scannedRows",scanned);stats.put("nullCount",scanned-nonNull);stats.put("blankCount",textual?empty:null);stats.put("nullRate",percent(scanned-nonNull,scanned));stats.put("blankRate",textual?percent(empty,scanned):null);stats.put("distinctCount",distinctSupported?rows.getLong("d_"+i):null);stats.put("distinctSupported",distinctSupported);stats.put("textual",textual);
            metrics.add(new MetricResult(rule,0,0,null,null,stats));
            }
        }
        return new Evaluation(scanned,0,0,metrics,List.of());
    }

    private BigDecimal percent(long count,long total){return total==0?null:BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),4,RoundingMode.HALF_UP);}

    private MetricResult metric(Rule rule, long checked, long violations) {
        BigDecimal rate = checked == 0
                ? null
                : BigDecimal.valueOf(checked - Math.min(checked, violations))
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(checked), 4, RoundingMode.HALF_UP);
        return new MetricResult(rule, checked, violations, rate, rate);
    }

    private NumericRange numericRange(Connection connection, Plan plan) throws Exception {
        String key = quote(connection, plan.shardKey());
        String sql = "SELECT MIN(" + key + ") AS min_key, MAX(" + key + ") AS max_key FROM "
                + qualified(connection, plan.endpoint().tableName());
        try (PreparedStatement statement = prepare(connection,sql,plan.timeoutSeconds());
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            BigDecimal min = rows.getBigDecimal("min_key");
            BigDecimal max = rows.getBigDecimal("max_key");
            return min == null || max == null ? null : new NumericRange(min, max);
        }
    }

    private boolean numericType(int type) {
        return switch (type) {
            case java.sql.Types.TINYINT, java.sql.Types.SMALLINT, java.sql.Types.INTEGER,
                 java.sql.Types.BIGINT, java.sql.Types.FLOAT, java.sql.Types.REAL,
                 java.sql.Types.DOUBLE, java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> true;
            default -> false;
        };
    }

    private void validateRule(Rule rule) {
        if (blank(rule.columnName())) throw new IllegalArgumentException(rule.name() + " 未配置校验字段");
        if (!List.of("NOT_NULL", "NOT_EMPTY", "UNIQUE", "RANGE", "ENUM", "LENGTH", "REGEX", "TIMELINESS", "REFERENCE", "PROFILE")
                .contains(rule.type())) {
            throw new IllegalArgumentException("不支持的质检规则类型: " + rule.type());
        }
    }

    private Connection open(Plan plan) throws Exception {
        return dataSources.open(plan.endpoint(), properties.getConnectionTimeoutSeconds());
    }
    private PreparedStatement prepare(Connection connection,String sql,int timeout) throws Exception {
        PreparedStatement statement=connection.prepareStatement(sql);
        try{statement.setQueryTimeout(Math.max(1,timeout));return statement;}catch(Exception error){statement.close();throw error;}
    }

    private String qualified(Connection connection, String name) throws Exception {
        String[] parts = name.split("\\.");
        List<String> quoted = new ArrayList<>();
        for (String part : parts) quoted.add(quote(connection, part));
        return String.join(".", quoted);
    }

    private String quote(Connection connection, String identifier) throws Exception {
        if (blank(identifier) || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("非法数据库标识符: " + identifier);
        }
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) return identifier;
        return quote + identifier + ("[".equals(quote)?"]":quote);
    }

    private void bind(PreparedStatement statement, List<?> values) throws Exception {
        for (int index = 0; index < values.size(); index++) {
            statement.setObject(index + 1, values.get(index));
        }
    }

    private String required(Map<String, Object> map, String key) {
        String value = text(map.get(key));
        if (blank(value)) throw new IllegalArgumentException("规则参数 " + key + " 不能为空");
        return value;
    }

    private int safeInt(long value) { return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value; }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private boolean blank(String value) { return value == null || value.isBlank() || "null".equalsIgnoreCase(value); }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String exceptionChain(Throwable throwable) {
        StringBuilder value = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (!value.isEmpty()) value.append("\nCaused by: ");
            value.append(current.getClass().getName()).append(": ").append(current.getMessage());
        }
        return value.toString();
    }

    private record Predicate(String sql, List<Object> arguments) { }

    private final class SqlContext {
        private final Connection connection;
        private final Plan plan;
        private final Shard shard;
        private final String vendor;

        private SqlContext(Connection connection, Plan plan, Shard shard) throws Exception {
            this.connection = connection;
            this.plan = plan;
            this.shard = shard;
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            this.vendor = product.contains("postgres") ? "postgresql"
                    : product.contains("kingbase") ? "kingbase"
                    : product.contains("oracle") ? "oracle"
                    : product.contains("dm") ? "dm" : product.contains("sql server")?"sqlserver":"mysql";
        }

        Connection connection() { return connection; }
        Plan plan() { return plan; }
        String vendor() { return vendor; }
        String table() throws Exception { return qualified(connection, plan.endpoint().tableName())+" dq_src"; }
        String identifier(String value) { try { return quote(connection, value); } catch (Exception e) { throw new IllegalArgumentException(e.getMessage(), e); } }
        String column(String value) { return "dq_src."+identifier(value); }
        String lengthFunction() { return List.of("oracle","dm").contains(vendor)?"LENGTH":"sqlserver".equals(vendor)?"LEN":"CHAR_LENGTH"; }
        String limit(int value) { return List.of("oracle", "dm").contains(vendor)
                ? " FETCH FIRST " + value + " ROWS ONLY" : "sqlserver".equals(vendor)?" OFFSET 0 ROWS FETCH NEXT "+value+" ROWS ONLY":" LIMIT " + value; }

        String shardWhere() {
            String predicate = shardPredicate();
            return predicate.isEmpty() ? "" : " WHERE " + predicate;
        }

        String shardAnd() {
            String predicate = shardPredicate();
            return predicate.isEmpty() ? "" : " AND " + predicate;
        }

        String shardPredicate() {
            if (blank(plan.shardKey()) || shard.keyLower() == null) return "";
            String key = column(plan.shardKey());
            return key + " >= ? AND " + key + (shard.upperInclusive() ? " <= ?" : " < ?");
        }

        List<Object> shardArguments() {
            if (blank(plan.shardKey()) || shard.keyLower() == null) return List.of();
            return List.of(new BigDecimal(shard.keyLower()), new BigDecimal(shard.keyUpper()));
        }
    }
}
