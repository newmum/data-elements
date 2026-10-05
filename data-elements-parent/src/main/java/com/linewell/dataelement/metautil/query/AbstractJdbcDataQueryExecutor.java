package com.linewell.dataelement.metautil.query;

import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.PageQueryCondition;
import com.linewell.dataelement.metautil.model.dto.PageQueryRequest;
import com.linewell.dataelement.metautil.model.dto.PageQueryResult;
import com.linewell.dataelement.metautil.model.dto.PageQuerySort;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class AbstractJdbcDataQueryExecutor extends AbstractMetadataExplorer implements DataQueryExecutor {

    protected enum PaginationStyle {
        LIMIT_OFFSET,
        OFFSET_FETCH,
        ROWNUM
    }

    private final Set<DatabaseType> supportedTypes;
    private final char identifierQuote;
    private final PaginationStyle paginationStyle;

    protected AbstractJdbcDataQueryExecutor(List<DatabaseType> supportedTypes, char identifierQuote, PaginationStyle paginationStyle) {
        this.supportedTypes = new LinkedHashSet<>(supportedTypes);
        this.identifierQuote = identifierQuote;
        this.paginationStyle = paginationStyle;
    }

    @Override
    public boolean supports(DatabaseType databaseType) {
        return supportedTypes.contains(databaseType);
    }

    @Override
    public PageQueryResult queryPage(PageQueryRequest request) {
        if (request == null || request.getDataSource() == null) {
            throw new BizException(500, "数据源配置不能为空");
        }
        DatabaseType databaseType = request.getDataSource().getDatabaseType();
        if (!supports(databaseType)) {
            throw new BizException(500, "当前数据库类型暂不支持分页查询: " + databaseType);
        }

        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : Math.min(request.getPageSize(), 200);
        TableRef tableRef = resolveTableRef(request);

        try (Connection connection = getConnection(request.getDataSource())) {
            TableRef realTableRef = resolveRealTableRef(connection, tableRef, databaseType);
            List<String> selectableColumns = loadSelectableColumns(connection, request, realTableRef);
            if (selectableColumns.isEmpty()) {
                throw new BizException(500, "未获取到可查询字段: " + realTableRef.tableName());
            }
            Map<String, String> whitelist = buildWhitelist(selectableColumns);
            List<String> selectedColumns = resolveSelectedColumns(request.getColumns(), selectableColumns, whitelist);
            SqlFragment whereClause = buildWhereClause(request.getConditions(), whitelist);
            String orderByClause = buildOrderByClause(request.getSorts(), whitelist);
            String qualifiedTable = qualifyTable(realTableRef);

            String countSql = "SELECT COUNT(1) FROM " + qualifiedTable + whereClause.sql();
            long total = executeCount(connection, countSql, whereClause.params());

            String baseSql = "SELECT " + joinColumns(selectedColumns) + " FROM " + qualifiedTable + whereClause.sql() + orderByClause;
            PageQueryResult result = executeQuery(connection, baseSql, whereClause.params(), selectedColumns, pageNo, pageSize);
            result.setPageNo(pageNo);
            result.setPageSize(pageSize);
            result.setTotal(total);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "分页查询失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> queryGroupedValues(PageQueryRequest request, String groupColumn, String nameColumn) {
        if (request == null || request.getDataSource() == null) {
            throw new BizException(500, "数据源配置不能为空");
        }
        DatabaseType databaseType = request.getDataSource().getDatabaseType();
        if (!supports(databaseType)) {
            throw new BizException(500, "当前数据库类型暂不支持分组查询: " + databaseType);
        }
        TableRef tableRef = resolveTableRef(request);
        try (Connection connection = getConnection(request.getDataSource())) {
            TableRef realTableRef = resolveRealTableRef(connection, tableRef, databaseType);
            List<String> selectableColumns = loadSelectableColumns(connection, request, realTableRef);
            if (selectableColumns.isEmpty()) {
                throw new BizException(500, "未获取到可查询字段: " + realTableRef.tableName());
            }
            Map<String, String> whitelist = buildWhitelist(selectableColumns);
            String resolvedGroupColumn = resolveColumn(groupColumn, whitelist);
            String resolvedNameColumn = nameColumn == null || nameColumn.isBlank()
                ? null
                : resolveColumn(nameColumn, whitelist);
            String quotedGroupColumn = quoteIdentifier(resolvedGroupColumn);
            String qualifiedTable = qualifyTable(realTableRef);
            boolean hasNameColumn = resolvedNameColumn != null
                && !resolvedNameColumn.equalsIgnoreCase(resolvedGroupColumn);
            String sql;
            if (!hasNameColumn) {
                sql = "SELECT " + quotedGroupColumn + ", COUNT(1) FROM " + qualifiedTable
                    + " WHERE " + quotedGroupColumn + " IS NOT NULL GROUP BY " + quotedGroupColumn
                    + " ORDER BY " + quotedGroupColumn;
            } else {
                String quotedNameColumn = quoteIdentifier(resolvedNameColumn);
                sql = "SELECT " + quotedGroupColumn + ", MIN(" + quotedNameColumn + "), COUNT(1) FROM " + qualifiedTable
                    + " WHERE " + quotedGroupColumn + " IS NOT NULL GROUP BY " + quotedGroupColumn
                    + " ORDER BY " + quotedGroupColumn;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> groups = new ArrayList<>();
                while (rs.next()) {
                    Object value = JdbcValueNormalizer.normalize(rs.getObject(1));
                    if (value == null || String.valueOf(value).isBlank()) {
                        continue;
                    }
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("value", value);
                    group.put("name", hasNameColumn
                        ? JdbcValueNormalizer.normalize(rs.getObject(2))
                        : null);
                    group.put("count", JdbcValueNormalizer.normalize(rs.getObject(hasNameColumn ? 3 : 2)));
                    groups.add(group);
                }
                return groups;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "分组查询失败: " + e.getMessage());
        }
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        throw new UnsupportedOperationException("分页查询执行器不支持数据库概览探查");
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        throw new UnsupportedOperationException("分页查询执行器不支持数据库列表探查");
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        throw new UnsupportedOperationException("分页查询执行器不支持 schema 列表探查");
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        throw new UnsupportedOperationException("分页查询执行器不支持表列表探查");
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        throw new UnsupportedOperationException("分页查询执行器不支持表详情探查");
    }

    @Override
    public List<com.linewell.dataelement.metautil.model.dto.ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        throw new UnsupportedOperationException("分页查询执行器不支持字段详情探查");
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        throw new UnsupportedOperationException("分页查询执行器不支持索引探查");
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        throw new UnsupportedOperationException("分页查询执行器不支持表统计探查");
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        throw new UnsupportedOperationException("分页查询执行器不支持表空间探查");
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        throw new UnsupportedOperationException("分页查询执行器不支持数据库空间探查");
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        throw new UnsupportedOperationException("分页查询执行器不支持抽样接口");
    }

    protected List<String> loadSelectableColumns(Connection connection, PageQueryRequest request, TableRef tableRef) throws Exception {
        List<String> columns = loadColumnsFromMetadata(connection, tableRef);
        if (!columns.isEmpty()) {
            return columns;
        }
        return loadColumnsFromZeroRowQuery(connection, tableRef);
    }

    private List<String> loadColumnsFromMetadata(Connection connection, TableRef tableRef) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> columns = new ArrayList<>();
        collectColumns(metaData, tableRef.catalog(), tableRef.schema(), tableRef.tableName(), columns);
        if (!columns.isEmpty()) {
            return deduplicate(columns);
        }
        collectColumns(metaData, tableRef.catalog(), upper(tableRef.schema()), upper(tableRef.tableName()), columns);
        if (!columns.isEmpty()) {
            return deduplicate(columns);
        }
        collectColumns(metaData, null, null, tableRef.tableName(), columns);
        return deduplicate(columns);
    }

    private void collectColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName, List<String> columns) throws Exception {
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null && !columnName.isBlank()) {
                    columns.add(columnName);
                }
            }
        }
    }

    private List<String> loadColumnsFromZeroRowQuery(Connection connection, TableRef tableRef) throws Exception {
        String sql = "SELECT * FROM " + qualifyTable(tableRef) + " WHERE 1 = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String columnLabel = metaData.getColumnLabel(i);
                if (columnLabel == null || columnLabel.isBlank()) {
                    columnLabel = metaData.getColumnName(i);
                }
                if (columnLabel != null && !columnLabel.isBlank()) {
                    columns.add(columnLabel);
                }
            }
            return deduplicate(columns);
        }
    }

    private long executeCount(Connection connection, String countSql, List<Object> params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(countSql)) {
            bindParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private PageQueryResult executeQuery(
        Connection connection,
        String baseSql,
        List<Object> whereParams,
        List<String> selectedColumns,
        int pageNo,
        int pageSize
    ) throws Exception {
        List<Object> params = new ArrayList<>(whereParams);
        String pagedSql = appendPagination(baseSql, params, selectedColumns, pageNo, pageSize);
        try (PreparedStatement statement = connection.prepareStatement(pagedSql)) {
            bindParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                return mapResult(rs, selectedColumns);
            }
        }
    }

    private PageQueryResult mapResult(ResultSet rs, List<String> selectedColumns) throws Exception {
        PageQueryResult result = new PageQueryResult();
        ResultSetMetaData metaData = rs.getMetaData();
        List<String> columns = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnLabel = metaData.getColumnLabel(i);
            if (columnLabel == null || columnLabel.isBlank()) {
                columnLabel = metaData.getColumnName(i);
            }
            columns.add(columnLabel);
            columnTypes.add(metaData.getColumnTypeName(i));
        }
        if (columns.isEmpty()) {
            columns.addAll(selectedColumns);
        }
        result.setColumns(columns);
        result.setColumnTypes(columnTypes);

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(columns.get(i - 1), JdbcValueNormalizer.normalize(rs.getObject(i)));
            }
            rows.add(row);
        }
        result.setRows(rows);
        return result;
    }

    private void bindParams(PreparedStatement statement, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Integer integer) {
                statement.setInt(i + 1, integer);
            } else if (value instanceof Long longValue) {
                statement.setLong(i + 1, longValue);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    protected String appendPagination(
        String baseSql,
        List<Object> params,
        List<String> selectedColumns,
        int pageNo,
        int pageSize
    ) {
        int offset = (pageNo - 1) * pageSize;
        if (paginationStyle == PaginationStyle.OFFSET_FETCH) {
            params.add(offset);
            params.add(pageSize);
            return baseSql + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        }
        if (paginationStyle == PaginationStyle.ROWNUM) {
            params.add(offset + pageSize);
            params.add(offset);
            return "SELECT " + joinColumns(selectedColumns)
                + " FROM (SELECT page_inner.*, ROWNUM \"__page_row_no\" FROM ("
                + baseSql
                + ") page_inner WHERE ROWNUM <= ?) page_outer WHERE \"__page_row_no\" > ?";
        }
        params.add(pageSize);
        params.add(offset);
        return baseSql + " LIMIT ? OFFSET ?";
    }

    private List<String> resolveSelectedColumns(List<String> requestedColumns, List<String> selectableColumns, Map<String, String> whitelist) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return selectableColumns;
        }
        List<String> result = new ArrayList<>();
        for (String requestedColumn : requestedColumns) {
            result.add(resolveColumn(requestedColumn, whitelist));
        }
        return deduplicate(result);
    }

    private SqlFragment buildWhereClause(List<PageQueryCondition> conditions, Map<String, String> whitelist) {
        if (conditions == null || conditions.isEmpty()) {
            return SqlFragment.empty();
        }
        List<String> fragments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (PageQueryCondition condition : conditions) {
            if (condition == null) {
                continue;
            }
            String column = resolveColumn(condition.getField(), whitelist);
            String op = normalizeOp(condition.getOp());
            if ("like".equals(op)) {
                fragments.add(quoteIdentifier(column) + " LIKE ?");
                params.add("%" + String.valueOf(condition.getValue()) + "%");
            } else if ("eq".equals(op)) {
                fragments.add(quoteIdentifier(column) + " = ?");
                params.add(condition.getValue());
            } else {
                throw new BizException(500, "暂不支持的查询操作符: " + condition.getOp());
            }
        }
        if (fragments.isEmpty()) {
            return SqlFragment.empty();
        }
        return new SqlFragment(" WHERE " + String.join(" AND ", fragments), params);
    }

    private String buildOrderByClause(List<PageQuerySort> sorts, Map<String, String> whitelist) {
        if (sorts == null || sorts.isEmpty()) {
            return "";
        }
        List<String> fragments = new ArrayList<>();
        for (PageQuerySort sort : sorts) {
            if (sort == null || sort.getField() == null || sort.getField().isBlank()) {
                continue;
            }
            String column = resolveColumn(sort.getField(), whitelist);
            String direction = normalizeDirection(sort.getDirection());
            fragments.add(quoteIdentifier(column) + " " + direction);
        }
        return fragments.isEmpty() ? "" : " ORDER BY " + String.join(", ", fragments);
    }

    private String normalizeOp(String op) {
        if (op == null || op.isBlank()) {
            throw new BizException(500, "查询操作符不能为空");
        }
        String normalized = op.trim().toLowerCase(Locale.ROOT);
        if ("=".equals(normalized)) {
            return "eq";
        }
        return normalized;
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "ASC";
        }
        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        if (!"ASC".equals(normalized) && !"DESC".equals(normalized)) {
            throw new BizException(500, "排序方向仅支持 ASC 或 DESC");
        }
        return normalized;
    }

    private Map<String, String> buildWhitelist(List<String> columns) {
        Map<String, String> whitelist = new LinkedHashMap<>();
        for (String column : columns) {
            if (column != null && !column.isBlank()) {
                whitelist.putIfAbsent(column.trim().toLowerCase(Locale.ROOT), column.trim());
            }
        }
        return whitelist;
    }

    private String resolveColumn(String requestedColumn, Map<String, String> whitelist) {
        if (requestedColumn == null || requestedColumn.isBlank()) {
            throw new BizException(500, "字段名不能为空");
        }
        String resolved = whitelist.get(requestedColumn.trim().toLowerCase(Locale.ROOT));
        if (resolved == null) {
            throw new BizException(500, "存在非法查询字段: " + requestedColumn);
        }
        return resolved;
    }

    private String joinColumns(List<String> columns) {
        List<String> quotedColumns = new ArrayList<>();
        for (String column : columns) {
            quotedColumns.add(quoteIdentifier(column));
        }
        return String.join(", ", quotedColumns);
    }

    private String qualifyTable(TableRef tableRef) {
        List<String> parts = new ArrayList<>();
        if (tableRef.catalog() != null && !tableRef.catalog().isBlank()) {
            parts.add(quoteSchemaOrCatalog(tableRef.catalog()));
        }
        if (tableRef.schema() != null && !tableRef.schema().isBlank()) {
            parts.add(quoteSchemaOrCatalog(tableRef.schema()));
        }
        parts.add(quoteIdentifier(tableRef.tableName()));
        return String.join(".", parts);
    }

    private String quoteSchemaOrCatalog(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
            || (value.startsWith("`") && value.endsWith("`") && value.length() >= 2)) {
            return value;
        }
        if (usesBareUppercaseIdentifier()) {
            return "\"" + value.toUpperCase(Locale.ROOT).replace("\"", "\"\"") + "\"";
        }
        String escaped = identifierQuote == '`'
            ? value.replace("`", "``")
            : value.replace("\"", "\"\"");
        return identifierQuote + escaped + identifierQuote;
    }

    private String quoteIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
            || (value.startsWith("`") && value.endsWith("`") && value.length() >= 2)) {
            return value;
        }
        if (usesBareUppercaseIdentifier()) {
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        String escaped = identifierQuote == '`'
            ? value.replace("`", "``")
            : value.replace("\"", "\"\"");
        return identifierQuote + escaped + identifierQuote;
    }

    private boolean usesBareUppercaseIdentifier() {
        return supportedTypes.contains(DatabaseType.DAMENG)
            || supportedTypes.contains(DatabaseType.ORACLE)
            || supportedTypes.contains(DatabaseType.OCEANBASE_ORACLE);
    }

    private TableRef resolveTableRef(PageQueryRequest request) {
        DataSourceConfig config = request.getDataSource();
        String rawTableName = request.getTableName() == null ? "" : request.getTableName().trim();
        if (rawTableName.isEmpty()) {
            throw new BizException(500, "tableName不能为空");
        }
        String catalog = usesCatalog(config.getDatabaseType()) ? blankToNull(config.getDatabase()) : null;
        String schema = usesSchema(config.getDatabaseType()) ? blankToNull(config.getSchema()) : null;
        String tableName = rawTableName;

        String[] parts = rawTableName.split("\\.");
        if (parts.length == 2) {
            if (usesCatalog(config.getDatabaseType())) {
                catalog = blankToNull(parts[0]);
            } else {
                schema = blankToNull(parts[0]);
            }
            tableName = parts[1];
        } else if (parts.length >= 3) {
            catalog = blankToNull(parts[parts.length - 3]);
            schema = blankToNull(parts[parts.length - 2]);
            tableName = parts[parts.length - 1];
        }
        return new TableRef(catalog, schema, tableName);
    }

    private boolean usesCatalog(DatabaseType databaseType) {
        return databaseType == DatabaseType.MYSQL
            || databaseType == DatabaseType.OCEANBASE_MYSQL
            || databaseType == DatabaseType.HIVE;
    }

    private boolean usesSchema(DatabaseType databaseType) {
        return !usesCatalog(databaseType);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private List<String> deduplicate(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private TableRef resolveRealTableRef(Connection connection, TableRef tableRef, DatabaseType databaseType) {
        if (databaseType != DatabaseType.DAMENG
            && databaseType != DatabaseType.ORACLE
            && databaseType != DatabaseType.OCEANBASE_ORACLE) {
            return tableRef;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 1. Try with original case
            TableRef found = findTableInMetadata(metaData, tableRef.catalog(), tableRef.schema(), tableRef.tableName());
            if (found != null) {
                return found;
            }
            
            // 2. Try with uppercase schema and table name
            found = findTableInMetadata(metaData, tableRef.catalog(), upper(tableRef.schema()), upper(tableRef.tableName()));
            if (found != null) {
                return found;
            }
            
            // 3. Try with null catalog/schema and uppercase table name
            found = findTableInMetadata(metaData, null, null, upper(tableRef.tableName()));
            if (found != null) {
                return new TableRef(tableRef.catalog(), found.schema(), found.tableName());
            }

            // 4. Try with null catalog/schema and original table name
            found = findTableInMetadata(metaData, null, null, tableRef.tableName());
            if (found != null) {
                return new TableRef(tableRef.catalog(), found.schema(), found.tableName());
            }
        } catch (Exception e) {
            // Ignore metadata errors, fallback to original
        }
        return tableRef;
    }

    private TableRef findTableInMetadata(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws Exception {
        try (ResultSet rs = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE", "VIEW"})) {
            if (rs.next()) {
                String realCatalog = rs.getString("TABLE_CAT");
                String realSchema = rs.getString("TABLE_SCHEM");
                String realTableName = rs.getString("TABLE_NAME");
                if (realSchema == null || realSchema.isBlank()) {
                    realSchema = schema;
                }
                return new TableRef(realCatalog, realSchema, realTableName);
            }
        }
        return null;
    }

    private record TableRef(String catalog, String schema, String tableName) {
    }

    private record SqlFragment(String sql, List<Object> params) {

        private static SqlFragment empty() {
            return new SqlFragment("", Collections.emptyList());
        }
    }
}
