package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.dialect.Dialect;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.script.annotation.Comment;

/** Magic 单表接口不支持的租户联查与分页。查询形态由结构化运算构成，值统一绑定。 */
@Component
@MagicModule("tableRead")
public class StructuredTableReadMagicModule {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final int MAX_IN_VALUES = 1000;
    private static final int DAMENG_SEARCH_CHUNK_SIZE = 8000;
    private static final DialectAdapter DIALECTS = new DialectAdapter();
    private final JdbcTemplate jdbc;

    public StructuredTableReadMagicModule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Comment("从当前行业租户创建只读查询。源表和关联表均附加租户条件")
    public Query from(String table, String alias, String tenantId) {
        if (!TenantContext.requireTenantId().equals(tenantId) || TenantContext.usesControlDatabase()
                || TenantContext.isIgnored()) {
            throw new IllegalArgumentException("结构化查询租户无效");
        }
        return new Query(jdbc, name(table), name(alias), tenantId);
    }

    public Expression column(String alias, String column, String type) {
        String relation = name(alias);
        String field = name(column);
        return new Expression(context -> context.column(relation, field), Kind.valueOf(type));
    }

    public Expression blankAsNull(Expression term) {
        return new Expression(context -> context.oracle ? term.sql(context) : "NULLIF(" + term.sql(context) + ", '')",
                term.kind);
    }

    public Expression boundedText(Expression term) {
        return expression(context -> context.oracle ? "CAST(" + term.sql(context) + " AS VARCHAR(4000))"
                : term.sql(context));
    }

    public Expression coalesce(List<?> values) {
        Kind resultKind = kind(values);
        Expression portable = call("COALESCE", values, resultKind);
        if (resultKind != Kind.LOB) {
            return portable;
        }
        // Dameng otherwise coerces a mixed CLOB/VARCHAR result to VARCHAR before sorting.
        var clobValues = values.stream().map(operand -> operand instanceof Expression term && term.kind == Kind.LOB
                ? term : new Expression(context -> "TO_CLOB(" + value(context, operand) + ")", Kind.LOB)).toList();
        Expression clob = call("COALESCE", clobValues, Kind.LOB);
        return new Expression(context -> (context.dameng ? clob : portable).sql(context), resultKind);
    }

    public Expression greatest(List<?> values) {
        return call("GREATEST", values, kind(values));
    }

    public Expression lower(Expression term) {
        return call("LOWER", List.of(term), term.kind);
    }

    public Expression trim(Expression term) {
        return call("TRIM", List.of(term), term.kind);
    }

    @Comment("用分隔符拼接文本表达式，跳过数据库 NULL，保留非 NULL 空值和长文本")
    public Expression concatWs(String separator, List<Expression> terms) {
        if (separator == null || terms.isEmpty()) {
            throw new IllegalArgumentException("结构化文本拼接参数无效");
        }
        List<Expression> values = List.copyOf(terms);
        Kind resultKind = values.stream().anyMatch(term -> term.kind == Kind.LOB) ? Kind.LOB : Kind.TEXT;
        var arguments = new ArrayList<Object>();
        arguments.add(separator);
        arguments.addAll(values);
        Expression nativeConcat = call("CONCAT_WS", arguments, resultKind);
        return new Expression(context -> context.oracle
                ? concatOracleText(context, separator, values, resultKind)
                : nativeConcat.sql(context), resultKind, resultKind == Kind.LOB);
    }

    private static String concatOracleText(Render context, String separator, List<Expression> terms, Kind kind) {
        // Use CLOB concatenation from the first operand so wide inputs do not pass through a VARCHAR buffer.
        var parts = new ArrayList<String>();
        if (kind == Kind.LOB) {
            parts.add("EMPTY_CLOB()");
        }
        for (int index = 0; index < terms.size(); index++) {
            Expression term = terms.get(index);
            if (index > 0) {
                String current = term.sql(context);
                String previous = terms.subList(0, index).stream()
                        .map(value -> value.sql(context) + " IS NOT NULL")
                        .collect(Collectors.joining(" OR ", "(", ")"));
                parts.add("CASE WHEN " + current + " IS NOT NULL AND " + previous
                        + " THEN " + context.parameter(separator) + " END");
            }
            parts.add(term.sql(context));
        }
        return parts.stream().collect(Collectors.joining(" || ", "(", ")"));
    }

    public Expression replace(Expression term, String search, String replacement) {
        if (search == null || search.isEmpty() || replacement == null) {
            throw new IllegalArgumentException("结构化文本替换参数无效");
        }
        return call("REPLACE", List.of(term, search, replacement), term.kind);
    }

    @Comment("将 Unix 毫秒数转换为当前数据库的时间值，保留空值")
    public Expression epochMillis(Expression term) {
        if (term.kind != Kind.NUMBER) {
            throw new IllegalArgumentException("毫秒时间转换必须使用数值表达式");
        }
        return new Expression(context -> {
            var epoch = new Timestamp(0);
            if (context.postgres) {
                return "(CAST(" + context.parameter(epoch) + " AS TIMESTAMP) + "
                        + term.sql(context) + " * INTERVAL '1 millisecond')";
            }
            if (context.oracle) {
                return "(CAST(" + context.parameter(epoch) + " AS TIMESTAMP) + NUMTODSINTERVAL("
                        + term.sql(context) + " / 1000.0, 'SECOND'))";
            }
            if (context.mysql) {
                return "TIMESTAMPADD(MICROSECOND, " + term.sql(context) + " * 1000, CAST("
                        + context.parameter(epoch) + " AS DATETIME(3)))";
            }
            throw new IllegalStateException("当前租户数据库缺少毫秒时间转换方言");
        }, Kind.TIME);
    }

    @Comment("读取 JSON 根对象中的单个标量属性。文档为空或无效时返回空值")
    public Expression jsonText(Expression term, String key) {
        // Some dialects require a literal JSON path; only a validated single property name may enter it.
        String path = "'$." + name(key) + "'";
        return expression(context -> {
            String column = term.sql(context);
            if (context.oracle) {
                return "JSON_VALUE(" + column + ", " + path + " NULL ON EMPTY NULL ON ERROR)";
            }
            if (context.postgres && !context.kingbase) {
                return "CASE WHEN (" + column + ") IS JSON OBJECT THEN (CAST(" + term.sql(context)
                        + " AS JSONB) ->> '" + key + "') END";
            }
            return "JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(" + column + ") = 1 THEN "
                    + term.sql(context) + " END, " + path + "))";
        });
    }

    public Expression count() {
        return new Expression(context -> "COUNT(*)", Kind.NUMBER);
    }

    public Expression countDistinct(Expression term) {
        return new Expression(context -> "COUNT(DISTINCT " + term.sql(context) + ")", Kind.NUMBER);
    }

    public Expression countIf(Condition condition) {
        return new Expression(context -> "COUNT(CASE WHEN " + condition.sql(context) + " THEN 1 END)", Kind.NUMBER);
    }

    public Expression sum(Expression term) {
        return call("SUM", List.of(term), Kind.NUMBER);
    }

    public Expression max(Expression term) {
        return call("MAX", List.of(term), term.kind);
    }

    public Expression when(Condition condition, Object trueValue, Object falseValue) {
        Kind type = trueValue instanceof Expression term ? term.kind
                : falseValue instanceof Expression term ? term.kind
                : trueValue instanceof Number || falseValue instanceof Number ? Kind.NUMBER : Kind.TEXT;
        return new Expression(context -> "CASE WHEN " + condition.sql(context)
                + " THEN " + caseValue(context, trueValue) + " ELSE " + caseValue(context, falseValue) + " END", type);
    }

    public Condition equal(Expression term, Object value) {
        return value == null ? isNull(term) : new Condition(context -> equality(context, term, value));
    }

    public Condition greaterThan(Expression term, Object value) {
        return new Condition(context -> term.sql(context) + " > " + value(context, value));
    }

    public Condition isNull(Expression term) {
        return new Condition(context -> term.sql(context) + " IS NULL");
    }

    public Condition in(Expression term, List<?> values) {
        return new Condition(context -> {
            if (values.isEmpty()) return "1 = 0";
            List<String> batches = new ArrayList<>();
            for (int start = 0; start < values.size(); start += MAX_IN_VALUES) {
                List<?> batch = values.subList(start, Math.min(start + MAX_IN_VALUES, values.size()));
                if (context.oracle && term.kind == Kind.LOB) {
                    batches.add(batch.stream().map(value -> equality(context, term, value))
                            .collect(Collectors.joining(" OR ", "(", ")")));
                } else {
                    String column = term.sql(context);
                    String bound = batch.stream().map(context::parameter).collect(Collectors.joining(", "));
                    batches.add(column + " IN (" + bound + ")");
                }
            }
            return "(" + String.join(" OR ", batches) + ")";
        });
    }

    public Condition contains(Expression term, String value) {
        return new Condition(context -> {
            if (context.dameng && term.joinedClob && (value == null || value.length() <= DAMENG_SEARCH_CHUNK_SIZE)) {
                return containsDamengJoinedClob(context, term, value);
            }
            String column = searchableText(context, term);
            String function = context.postgres ? "STRPOS" : "INSTR";
            return function + "(LOWER(" + column + "), LOWER(" + context.parameter(value) + ")) > 0";
        });
    }

    private static String containsDamengJoinedClob(Render context, Expression term, String value) {
        // Keep the joined CLOB in the same query level as CONNECT BY. ROWNUM bounds its derived row.
        String source = "(SELECT " + term.sql(context) + " AS search_text FROM DUAL WHERE ROWNUM <= 1) joined_text";
        String text = "joined_text.search_text";
        // Overlap the chunks so a keyword split at the boundary can still be found.
        int overlap = value == null || value.isEmpty() ? 0 : value.length() - 1;
        int stride = DAMENG_SEARCH_CHUNK_SIZE - overlap;
        String chunk = "DBMS_LOB.SUBSTR(" + text + ", " + DAMENG_SEARCH_CHUNK_SIZE
                + ", (LEVEL - 1) * " + context.parameter(stride) + " + 1)";
        String chunkMatch = "INSTR(LOWER(" + chunk + "), LOWER(" + context.parameter(value) + ")) > 0";
        String chunkCount = "CEIL(LENGTH(" + text + ") / (" + context.parameter(stride) + " * 1.0))";
        return "EXISTS (SELECT 1 FROM " + source + " WHERE " + chunkMatch + " CONNECT BY LEVEL <= " + chunkCount + ")";
    }

    public Condition like(Expression term, String pattern) {
        return new Condition(context -> searchableText(context, term) + " LIKE " + context.parameter(pattern));
    }

    public Condition not(Condition condition) {
        return new Condition(context -> "NOT (" + condition.sql(context) + ")");
    }

    public Condition all(List<Condition> conditions) {
        return combine(conditions, " AND ", "1 = 1");
    }

    public Condition any(List<Condition> conditions) {
        return combine(conditions, " OR ", "1 = 0");
    }

    private static Condition combine(List<Condition> conditions, String operator, String empty) {
        List<Condition> operands = List.copyOf(conditions);
        return new Condition(context -> operands.isEmpty() ? empty : operands.stream()
                .map(condition -> condition.sql(context)).collect(Collectors.joining(operator, "(", ")")));
    }

    private static Expression call(String function, List<?> arguments, Kind kind) {
        List<?> operands = new ArrayList<>(arguments);
        return new Expression(context -> operands.stream().map(operand -> value(context, operand))
                .collect(Collectors.joining(", ", function + "(", ")")), kind);
    }

    private static Kind kind(List<?> values) {
        return values.stream().filter(value -> value != null).findFirst()
                .map(value -> value instanceof Expression term ? term.kind
                        : value instanceof Number ? Kind.NUMBER : Kind.TEXT).orElse(Kind.TEXT);
    }

    private static String searchableText(Render context, Expression term) {
        String column = term.sql(context);
        return term.kind == Kind.NUMBER || term.kind == Kind.TIME ? context.asText(column) : column;
    }

    private static String caseValue(Render context, Object value) {
        return value == null ? "NULL" : value(context, value);
    }

    private static String equality(Render context, Expression term, Object value) {
        String left = term.sql(context);
        String right = value(context, value);
        return context.oracle && term.kind == Kind.LOB ? "DBMS_LOB.COMPARE(" + left + ", TO_CLOB(" + right + ")) = 0"
                : left + " = " + right;
    }

    private static String value(Render context, Object value) {
        return value instanceof Expression term ? term.sql(context) : context.parameter(value);
    }

    private static String name(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("结构化查询标识符无效");
        }
        return value;
    }

    private static Expression expression(Function<Render, String> sql) {
        return new Expression(sql, Kind.TEXT);
    }

    private enum Kind { TEXT, NUMBER, TIME, LOB }

    public static final class Expression {
        private final Function<Render, String> renderer;
        private final Kind kind;
        private final boolean joinedClob;

        private Expression(Function<Render, String> renderer, Kind kind) {
            this(renderer, kind, false);
        }

        private Expression(Function<Render, String> renderer, Kind kind, boolean joinedClob) {
            this.renderer = renderer;
            this.kind = kind;
            this.joinedClob = joinedClob;
        }

        private String sql(Render context) {
            return renderer.apply(context);
        }
    }

    public static final class Condition {
        private final Function<Render, String> renderer;

        private Condition(Function<Render, String> renderer) {
            this.renderer = renderer;
        }

        private String sql(Render context) {
            return renderer.apply(context);
        }
    }

    public static final class Query {
        private static final String RANK_COLUMN = "structured_rank_no";
        private static final String NODE_COLUMN = "node_id";
        private static final String PARENT_COLUMN = "structured_parent_id";
        private static final String DEPTH_COLUMN = "structured_depth";
        private static final String PRIOR_ALIAS = "structured_previous";
        private static final Set<String> WALK_COLUMNS = Set.of("tenant_id", NODE_COLUMN, PARENT_COLUMN, DEPTH_COLUMN);
        private static final int MAX_WALK_DEPTH = 16;
        private static final int MAX_PAGE_SIZE = 100;
        private static final int MAX_READ_ROWS = 1000;
        private final JdbcTemplate jdbc;
        private final String table;
        private final String alias;
        private final String tenantId;
        private final Ranked ranked;
        private final Ancestors ancestors;
        private final List<Join> joins = new ArrayList<>();
        private final List<Condition> filters = new ArrayList<>();

        private Query(JdbcTemplate jdbc, String table, String alias, String tenantId) {
            this(jdbc, table, alias, tenantId, null, null);
        }

        private Query(JdbcTemplate jdbc, String table, String alias, String tenantId, Ranked ranked, Ancestors ancestors) {
            this.jdbc = jdbc;
            this.table = table;
            this.alias = alias;
            this.tenantId = tenantId;
            this.ranked = ranked;
            this.ancestors = ancestors;
        }

        public void leftJoin(String table, String joinAlias, String key, String rootKey, List<Condition> conditions) {
            joins.add(new Join(name(table), name(joinAlias), name(key), name(rootKey), List.copyOf(conditions), null, false, null));
        }

        @Comment("关联每组最近有效记录，以时间、唯一键稳定选择，空时间按旧值处理")
        public void leftJoinLatest(String table, String joinAlias, String key, String rootKey,
                Map<String, ?> fixedValues, String deletionColumn, String timestampColumn, String uniqueColumn,
                boolean projectionOnly) {
            fixedValues.keySet().forEach(StructuredTableReadMagicModule::name);
            Latest latest = new Latest(Map.copyOf(fixedValues), name(deletionColumn), name(timestampColumn), name(uniqueColumn));
            joins.add(new Join(name(table), name(joinAlias), name(key), name(rootKey), List.of(), latest, projectionOnly, null));
        }

        @Comment("关联同一租户内按唯一关联键分组的派生结果")
        public void leftJoinGrouped(Query child, String joinAlias, String projectedKey, String rootKey,
                Map<String, Expression> projection, List<Expression> groups) {
            leftJoinGrouped(child, joinAlias, projectedKey, rootKey, projection, groups, false);
        }

        public void leftJoinGrouped(Query child, String joinAlias, String projectedKey, String rootKey,
                Map<String, Expression> projection, List<Expression> groups, boolean compareKeysAsText) {
            joinGrouped(child, joinAlias, projectedKey, rootKey, projection, groups, compareKeysAsText, false);
        }

        @Comment("按唯一键关联仅供投影和排序使用的聚合结果，分页总数不执行该关联")
        public void leftJoinGroupedProjection(Query child, String joinAlias, String projectedKey, String rootKey,
                Map<String, Expression> projection, List<Expression> groups) {
            joinGrouped(child, joinAlias, projectedKey, rootKey, projection, groups, false, true);
        }

        private void joinGrouped(Query child, String joinAlias, String projectedKey, String rootKey,
                Map<String, Expression> projection, List<Expression> groups, boolean compareKeysAsText,
                boolean projectionOnly) {
            requireTenant();
            if (child == this || jdbc != child.jdbc || !tenantId.equals(child.tenantId)) {
                throw new IllegalArgumentException("结构化查询关联租户无效");
            }
            if (groups.size() != 1 || groups.getFirst() == null || groups.getFirst() != projection.get(projectedKey)) {
                throw new IllegalArgumentException("聚合关联必须按已投影的唯一关联键分组");
            }
            Grouped group = new Grouped(child.snapshot(), new LinkedHashMap<>(projection), List.copyOf(groups),
                    compareKeysAsText);
            joins.add(new Join(null, name(joinAlias), name(projectedKey), name(rootKey), List.of(), null, projectionOnly, group));
        }

        @Comment("先按分组和明确顺序选择第一行，再以新别名施加外层筛选和分页")
        public Query ranked(String rankedAlias, Map<String, Expression> projection, List<Expression> partitions,
                List<Expression> orders, List<String> directions) {
            requireTenant();
            if (partitions.isEmpty() || orders.isEmpty() || orders.size() != directions.size()
                    || directions.stream().anyMatch(direction -> !validDirection(direction))) {
                throw new IllegalArgumentException("结构化查询分组排序参数无效");
            }
            var columns = new LinkedHashMap<>(projection);
            if (columns.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(RANK_COLUMN)
                    || key.equalsIgnoreCase("tenant_id") && !key.equals("tenant_id"))) {
                throw new IllegalArgumentException("结构化查询使用了保留列名");
            }
            columns.put("tenant_id", expression(context -> context.column(alias, "tenant_id")));
            var rank = new Ranked(snapshot(), columns, List.copyOf(partitions), List.copyOf(orders),
                    List.copyOf(directions));
            return new Query(jdbc, null, name(rankedAlias), tenantId, rank, null);
        }

        @Comment("从种子节点向当前租户的父节点逐层查询，返回 node_id、tenant_id 与原始负载；父级条件只引用父表别名")
        public Query ancestors(String walkAlias, String nodeTable, String parentAlias, String nodeKey, String parentKey,
                Expression seedNode, Expression seedParent, Map<String, Expression> payload,
                List<Condition> parentConditions, int maxDepth) {
            requireTenant();
            if (maxDepth < 0 || maxDepth > MAX_WALK_DEPTH || PRIOR_ALIAS.equalsIgnoreCase(parentAlias)
                    || seedNode.kind != seedParent.kind || (seedNode.kind != Kind.TEXT && seedNode.kind != Kind.NUMBER)) {
                throw new IllegalArgumentException("结构化祖先查询参数无效");
            }
            var projectedNames = new HashSet<>(WALK_COLUMNS);
            for (var entry : payload.entrySet()) {
                if (!projectedNames.add(name(entry.getKey()).toLowerCase(Locale.ROOT))
                        || entry.getValue() == null || entry.getValue().kind == Kind.LOB) {
                    throw new IllegalArgumentException("结构化祖先查询负载列无效");
                }
            }
            var walk = new Ancestors(snapshot(), name(nodeTable), name(parentAlias), name(nodeKey), name(parentKey),
                    seedNode, seedParent, new LinkedHashMap<>(payload), List.copyOf(parentConditions), maxDepth);
            return new Query(jdbc, null, name(walkAlias), tenantId, null, walk);
        }

        public void where(Condition condition) {
            filters.add(condition);
        }

        public List<Map<String, Object>> grouped(Map<String, Expression> projection, List<Expression> groups) {
            return execute((connection, reader) -> {
                Render context = new Render(connection);
                String sql = groupedSql(context, projection, groups);
                return JdbcValueNormalizer.maps(reader.queryForList(context.statement(sql), context.boundParameters().toArray()));
            });
        }

        @Comment("以确定的排序读取一个受限窗口，不额外统计总数")
        public List<Map<String, Object>> rows(Map<String, Expression> projection, Expression sort, String direction,
                int limit) {
            if (limit < 1 || limit > MAX_READ_ROWS || !validDirection(direction)) {
                throw new IllegalArgumentException("结构化查询读取窗口无效");
            }
            return execute((connection, reader) -> {
                Render context = new Render(connection);
                String sql = select(context, projection) + from(context, true)
                        + " ORDER BY " + sort.sql(context) + " " + direction;
                return queryWindow(connection, reader, context, sql, 0, limit);
            });
        }

        public Map<String, Object> page(Map<String, Expression> projection, Expression sort, String direction,
                String tieBreaker, int pageNum, int pageSize) {
            return page(projection, sort, direction, tieBreaker, "asc", pageNum, pageSize);
        }

        public Map<String, Object> page(Map<String, Expression> projection, Expression sort, String direction,
                String tieBreaker, String tieDirection, int pageNum, int pageSize) {
            return readPage(projection, sort, direction, tieBreaker, tieDirection, pageNum, pageSize, false, false);
        }

        @Comment("分页保留主表原始列，并增加显式投影的计算列")
        public Map<String, Object> pageRootRows(Map<String, Expression> extra, Expression sort, String direction,
                String tieBreaker, int pageNum, int pageSize) {
            if (table == null) {
                throw new IllegalArgumentException("原始列分页必须使用物理主表");
            }
            return readPage(extra, sort, direction, tieBreaker, "asc", pageNum, pageSize, false, true);
        }

        @Comment("请求页超过最后一页时读取最后一页，返回实际页码")
        public Map<String, Object> pageClamped(Map<String, Expression> projection, Expression sort, String direction,
                String tieBreaker, String tieDirection, int pageNum, int pageSize) {
            return readPage(projection, sort, direction, tieBreaker, tieDirection, pageNum, pageSize, true, false);
        }

        private Map<String, Object> readPage(Map<String, Expression> projection, Expression sort, String direction,
                String tieBreaker, String tieDirection, int pageNum, int pageSize, boolean clamp, boolean includeRoot) {
            if (pageNum < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE || !validDirection(direction)
                    || !validDirection(tieDirection)) {
                throw new IllegalArgumentException("结构化查询分页参数无效");
            }
            name(tieBreaker);
            return execute((connection, reader) -> {
                Render countContext = new Render(connection);
                String countSql = "SELECT COUNT(*)" + from(countContext, false);
                long total = reader.queryForObject(countContext.statement(countSql),
                        Long.class, countContext.boundParameters().toArray());
                int actualPage = clamp ? (int) Math.min(pageNum, total == 0 ? 1 : (total - 1) / pageSize + 1) : pageNum;
                long offset = (long) (actualPage - 1) * pageSize;
                var result = new LinkedHashMap<String, Object>();
                result.put("total", total);
                if (clamp) result.put("pageNum", actualPage);
                if (offset >= total) {
                    result.put("rows", List.of());
                    return result;
                }
                Render context = new Render(connection);
                String ordered = select(context, projection, includeRoot) + from(context, true)
                        + " ORDER BY CASE WHEN " + sort.sql(context) + " IS NULL THEN 1 ELSE 0 END, "
                        + sort.sql(context) + " " + direction + ", " + context.column(alias, tieBreaker)
                        + " " + tieDirection;
                result.put("rows", queryWindow(connection, reader, context, ordered, offset, pageSize));
                return result;
            });
        }

        private static List<Map<String, Object>> queryWindow(Connection connection, JdbcTemplate reader,
                Render context, String ordered, long offset, int limit) throws SQLException {
            Dialect dialect = DIALECTS.getDialectFromConnection(connection);
            if (dialect == null) throw new IllegalStateException("当前租户数据库缺少分页方言");
            var args = new BoundSql(null, ordered, context.boundParameters(), null);
            String paged = dialect.getPageSql(ordered, args, offset, limit);
            return JdbcValueNormalizer.maps(reader.queryForList(
                    context.statement(paged), args.getParameters()));
        }

        private static boolean validDirection(String direction) {
            return "asc".equals(direction) || "desc".equals(direction);
        }

        private void requireTenant() {
            if (!tenantId.equals(TenantContext.requireTenantId()) || TenantContext.usesControlDatabase()
                    || TenantContext.isIgnored()) {
                throw new IllegalArgumentException("结构化查询租户已变化");
            }
        }

        private <T> T execute(ReadOperation<T> operation) {
            requireTenant();
            return jdbc.execute((ConnectionCallback<T>) connection -> {
                var reader = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                reader.setQueryTimeout(jdbc.getQueryTimeout());
                return operation.run(connection, reader);
            });
        }

        private Query snapshot() {
            Query copy = new Query(jdbc, table, alias, tenantId, ranked, ancestors);
            copy.joins.addAll(joins);
            copy.filters.addAll(filters);
            return copy;
        }

        private String groupedSql(Render context, Map<String, Expression> projection, List<Expression> groups) {
            String sql = select(context, projection) + from(context, true);
            if (groups.isEmpty()) return sql;
            List<Expression> projected = new ArrayList<>(projection.values());
            return sql + " GROUP BY " + groups.stream().map(group -> {
                // PostgreSQL distinguishes separately bound markers in the same textual expression.
                int index = context.postgres ? projected.indexOf(group) : -1;
                return index < 0 ? group.sql(context) : Integer.toString(index + 1);
            }).collect(Collectors.joining(", "));
        }

        private String select(Render context, Map<String, Expression> projection) {
            return select(context, projection, false);
        }

        private String select(Render context, Map<String, Expression> projection, boolean includeRoot) {
            String columns = projection.entrySet().stream().map(entry -> entry.getValue().sql(context) + " AS "
                    + context.quote(name(entry.getKey()))).collect(Collectors.joining(", "));
            if (includeRoot) {
                columns = alias + ".*" + (columns.isEmpty() ? "" : ", " + columns);
            }
            return "SELECT " + columns;
        }

        private String from(Render context, boolean projectionJoins) {
            StringBuilder sql = new StringBuilder(" FROM ").append(source(context));
            for (Join join : joins) {
                if (!projectionJoins && join.projectionOnly()) continue;
                if (join.grouped() != null) {
                    Grouped group = join.grouped();
                    sql.append(" LEFT JOIN (").append(group.child().groupedSql(context, group.projection(), group.groups()))
                            .append(") ").append(join.alias()).append(" ON ");
                    String left = context.column(join.alias(), join.key());
                    String right = context.column(alias, join.rootKey());
                    sql.append(group.compareKeysAsText() ? context.asText(left) + " = " + context.asText(right)
                            : left + " = " + right);
                    continue;
                }
                sql.append(" LEFT JOIN ").append(context.table(join.table(), join.alias())).append(" ON ")
                        .append(context.column(join.alias(), "tenant_id")).append(" = ").append(context.column(alias, "tenant_id"))
                        .append(" AND ").append(context.column(join.alias(), join.key())).append(" = ")
                        .append(context.column(alias, join.rootKey()));
                for (Condition condition : join.conditions()) sql.append(" AND ").append(condition.sql(context));
                if (join.latest() != null) sql.append(latest(context, join));
            }
            sql.append(" WHERE ").append(context.column(alias, "tenant_id")).append(" = ").append(context.parameter(tenantId));
            if (ranked != null) sql.append(" AND ").append(context.column(alias, RANK_COLUMN)).append(" = 1");
            for (Condition condition : filters) sql.append(" AND ").append(condition.sql(context));
            return sql.toString();
        }

        private String source(Render context) {
            if (ancestors != null) return context.table(context.registerWalk(ancestors), alias);
            if (ranked == null) return context.table(table, alias);
            Query child = ranked.child();
            String projection = child.select(context, ranked.projection());
            String partitions = ranked.partitions().stream().map(term -> term.sql(context))
                    .collect(Collectors.joining(", "));
            List<String> orders = new ArrayList<>();
            for (int i = 0; i < ranked.orders().size(); i++) {
                Expression order = ranked.orders().get(i);
                orders.add("CASE WHEN " + order.sql(context) + " IS NULL THEN 1 ELSE 0 END");
                orders.add(order.sql(context) + " " + ranked.directions().get(i));
            }
            return "(" + projection + ", ROW_NUMBER() OVER (PARTITION BY " + partitions + " ORDER BY "
                    + String.join(", ", orders) + ") AS " + context.quote(RANK_COLUMN)
                    + child.from(context, true) + ") " + alias;
        }

        private static String walkDefinition(Render context, String cte, Ancestors walk) {
            Query seed = walk.seed();
            var projection = new LinkedHashMap<String, Expression>();
            projection.put("tenant_id", expression(render -> render.column(seed.alias, "tenant_id")));
            projection.put(NODE_COLUMN, walk.seedNode());
            projection.put(PARENT_COLUMN, walk.seedParent());
            projection.put(DEPTH_COLUMN, new Expression(render -> "0", Kind.NUMBER));
            projection.putAll(walk.payload());
            String anchor = seed.select(context, projection) + seed.from(context, true)
                    + " AND " + walk.seedNode().sql(context) + " IS NOT NULL";

            String parent = walk.parentAlias();
            String node = context.column(parent, walk.nodeKey());
            String depth = context.column(PRIOR_ALIAS, DEPTH_COLUMN);
            var columns = new ArrayList<>(List.of(context.column(PRIOR_ALIAS, "tenant_id"), node,
                    context.column(parent, walk.parentKey()), depth + " + 1"));
            walk.payload().keySet().forEach(key -> columns.add(context.column(PRIOR_ALIAS, key)));
            StringBuilder recursive = new StringBuilder("SELECT ").append(String.join(", ", columns))
                    .append(" FROM ").append(context.table(cte, PRIOR_ALIAS))
                    .append(" JOIN ").append(context.table(walk.nodeTable(), parent)).append(" ON ")
                    .append(context.column(parent, "tenant_id")).append(" = ").append(context.column(PRIOR_ALIAS, "tenant_id"))
                    .append(" AND ").append(node).append(" = ").append(context.column(PRIOR_ALIAS, PARENT_COLUMN))
                    .append(" WHERE ").append(depth).append(" < ").append(context.parameter(walk.maxDepth()))
                    .append(" AND ").append(node).append(" <> ").append(context.column(PRIOR_ALIAS, NODE_COLUMN));
            Render parentContext = context.restrictTo(parent);
            for (Condition condition : walk.parentConditions()) {
                recursive.append(" AND ").append(condition.sql(parentContext));
            }
            String names = projection.keySet().stream().map(context::quote).collect(Collectors.joining(", "));
            return context.quote(cte) + " (" + names + ") AS (" + anchor + " UNION ALL " + recursive + ")";
        }

        private static String latest(Render context, Join join) {
            Latest rule = join.latest();
            String current = join.alias();
            String other = name("_latest_" + current);
            String time = context.column(current, rule.timestampColumn());
            String next = context.column(other, rule.timestampColumn());
            return " AND " + fixed(context, current, rule) + " AND NOT EXISTS (SELECT 1 FROM "
                    + context.table(join.table(), other) + " WHERE "
                    + context.column(other, "tenant_id") + " = " + context.column(current, "tenant_id")
                    + " AND " + context.column(other, join.key()) + " = " + context.column(current, join.key())
                    + " AND " + fixed(context, other, rule)
                    + " AND (" + next + " > " + time + " OR (" + time + " IS NULL AND " + next + " IS NOT NULL)"
                    + " OR ((" + next + " = " + time + " OR (" + next + " IS NULL AND " + time + " IS NULL)) AND "
                    + context.column(other, rule.uniqueColumn()) + " > " + context.column(current, rule.uniqueColumn()) + ")))";
        }

        private static String fixed(Render context, String alias, Latest rule) {
            StringBuilder sql = new StringBuilder("COALESCE(").append(context.column(alias, rule.deletionColumn())).append(", 0) = 0");
            rule.fixedValues().forEach((key, value) -> sql.append(" AND ").append(context.column(alias, key))
                    .append(" = ").append(context.parameter(value)));
            return sql.toString();
        }
    }

    private record Join(String table, String alias, String key, String rootKey, List<Condition> conditions,
                        Latest latest, boolean projectionOnly, Grouped grouped) { }
    private record Grouped(Query child, Map<String, Expression> projection, List<Expression> groups,
                           boolean compareKeysAsText) { }
    private record Ranked(Query child, Map<String, Expression> projection, List<Expression> partitions,
                          List<Expression> orders, List<String> directions) { }
    private record Ancestors(Query seed, String nodeTable, String parentAlias, String nodeKey, String parentKey,
                             Expression seedNode, Expression seedParent, Map<String, Expression> payload,
                             List<Condition> parentConditions, int maxDepth) { }
    private record Latest(Map<String, ?> fixedValues, String deletionColumn, String timestampColumn,
                          String uniqueColumn) { }
    private record CommonTable(String sql, List<Object> parameters) { }

    private static final class CommonTables {
        private final List<CommonTable> definitions = new ArrayList<>();
        private int nextId;
    }

    @FunctionalInterface
    private interface ReadOperation<T> {
        T run(Connection connection, JdbcTemplate reader) throws SQLException;
    }

    private static final class Render {
        private final String quote;
        private final boolean postgres;
        private final boolean kingbase;
        private final boolean oracle;
        private final boolean dameng;
        private final boolean mysql;
        private final CommonTables commonTables;
        private final List<Object> parameters;
        private final String restrictedAlias;

        private Render(Connection connection) throws SQLException {
            var metadata = connection.getMetaData();
            String url = metadata.getURL().toLowerCase(Locale.ROOT);
            kingbase = url.startsWith("jdbc:kingbase8:");
            postgres = url.startsWith("jdbc:postgresql:") || kingbase;
            dameng = url.startsWith("jdbc:dm:");
            oracle = dameng || url.startsWith("jdbc:oracle:");
            mysql = url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:") || url.startsWith("jdbc:h2:");
            quote = oracle ? "\"" : metadata.getIdentifierQuoteString().trim();
            commonTables = new CommonTables();
            parameters = new ArrayList<>();
            restrictedAlias = null;
        }

        private Render(Render source, List<Object> parameters, String restrictedAlias) {
            quote = source.quote;
            postgres = source.postgres;
            kingbase = source.kingbase;
            oracle = source.oracle;
            dameng = source.dameng;
            mysql = source.mysql;
            commonTables = source.commonTables;
            this.parameters = parameters;
            this.restrictedAlias = restrictedAlias;
        }

        private Render restrictTo(String alias) {
            return new Render(this, parameters, alias);
        }

        private String registerWalk(Ancestors walk) {
            if (!mysql && !postgres && !oracle) {
                throw new IllegalStateException("当前租户数据库缺少祖先查询方言");
            }
            String cte = "structured_walk_" + commonTables.nextId++;
            // CTE references are discovered while rendering the SELECT. Their bindings still precede SELECT bindings.
            Render definition = new Render(this, new ArrayList<>(), null);
            String sql = Query.walkDefinition(definition, cte, walk);
            commonTables.definitions.add(new CommonTable(sql, definition.parameters));
            return cte;
        }

        private String statement(String sql) {
            if (commonTables.definitions.isEmpty()) return sql;
            String prefix = oracle ? "WITH " : "WITH RECURSIVE ";
            return commonTables.definitions.stream().map(CommonTable::sql)
                    .collect(Collectors.joining(", ", prefix, " " + sql));
        }

        private List<Object> boundParameters() {
            if (commonTables.definitions.isEmpty()) return parameters;
            var bindings = new ArrayList<>();
            commonTables.definitions.forEach(table -> bindings.addAll(table.parameters()));
            bindings.addAll(parameters);
            return bindings;
        }

        private String parameter(Object value) {
            parameters.add(value);
            return "?";
        }

        private String asText(String value) {
            String type = postgres ? "TEXT" : oracle ? "VARCHAR(128)" : "CHAR";
            return "CAST(" + value + " AS " + type + ")";
        }

        private String quote(String identifier) {
            return quote + identifier + quote;
        }

        private String table(String name, String alias) {
            return quote(name) + " " + alias;
        }

        private String column(String alias, String name) {
            if (restrictedAlias != null && !restrictedAlias.equals(alias)) {
                throw new IllegalArgumentException("祖先条件只能引用父表别名");
            }
            return alias + "." + quote(name);
        }
    }
}
