package com.linewell.dataelement.metautil.explorer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.SqlValidationResult;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;

/**
 * 元数据探查器接口
 * 定义了所有数据库元数据探查的通用方法
 * 
 * @author MetaUtil
 */
public interface MetadataExplorer {

    /**
     * 测试连接
     * 
     * @param config 数据源配置
     * @return 连接是否成功
     */
    boolean testConnection(DataSourceConfig config);

    /**
     * 获取数据库信息
     * 
     * @param config 数据源配置
     * @return 数据库信息
     */
    DatabaseInfo getDatabaseInfo(DataSourceConfig config);

    /**
     * 获取所有数据库列表
     * 
     * @param config 数据源配置
     * @return 数据库名称列表
     */
    List<String> getDatabases(DataSourceConfig config);

    /**
     * 获取所有Schema列表
     * 
     * @param config 数据源配置
     * @return Schema名称列表
     */
    List<String> getSchemas(DataSourceConfig config);

    /**
     * Returns schemas that contain at least one object the current connection
     * can explore. Implementations with database-side metadata support should
     * override this instead of requiring callers to probe every schema.
     */
    default List<String> getSchemasWithTables(DataSourceConfig config) {
        return getSchemas(config);
    }

    /**
     * 获取表列表
     * 
     * @param config 数据源配置
     * @return 表信息列表
     */
    List<TableInfo> getTables(DataSourceConfig config);

    /**
     * 分页获取表/视图列表。
     *
     * <p>默认实现用于小型库和暂未做数据库级分页优化的连接器：先复用 getTables，再在内存中过滤分页。
     * Oracle 等大库连接器应覆盖该方法，直接在数据库侧分页，避免一次返回几千张表导致接口和页面超时。</p>
     */
    default TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<TableInfo> allTables = getTables(config);
        List<TableInfo> filtered = new ArrayList<>();
        for (TableInfo table : allTables == null ? new ArrayList<TableInfo>() : allTables) {
            String tableName = table.getTableName() == null ? "" : table.getTableName().toLowerCase(Locale.ROOT);
            String tableComment = table.getTableComment() == null ? "" : table.getTableComment().toLowerCase(Locale.ROOT);
            if (normalizedKeyword.isEmpty() || tableName.contains(normalizedKeyword) || tableComment.contains(normalizedKeyword)) {
                filtered.add(table);
            }
        }

        int fromIndex = Math.min((safePageNo - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        result.setTotal((long) filtered.size());
        result.setRows(new ArrayList<>(filtered.subList(fromIndex, toIndex)));
        return result;
    }

    /**
     * Reads a physical table page while excluding table names already managed by the platform.
     * Database explorers with large schemas should override this method and push the exclusion
     * into their metadata SQL. The compatibility implementation keeps existing connectors working.
     */
    default TablePageResult getTablesPageExcluding(
            DataSourceConfig config,
            Integer pageNo,
            Integer pageSize,
            String keyword,
            List<String> excludedTableNames
    ) {
        if (excludedTableNames == null || excludedTableNames.isEmpty()) {
            return getTablesPage(config, pageNo, pageSize, keyword);
        }
        java.util.Set<String> excluded = new java.util.HashSet<>();
        for (String tableName : excludedTableNames) {
            if (tableName != null && !tableName.isBlank()) {
                excluded.add(tableName.trim().toLowerCase(Locale.ROOT));
            }
        }
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<TableInfo> filtered = new ArrayList<>();
        for (TableInfo table : getTables(config)) {
            String tableName = table.getTableName() == null ? "" : table.getTableName();
            String tableComment = table.getTableComment() == null ? "" : table.getTableComment();
            if (!excluded.contains(tableName.toLowerCase(Locale.ROOT))
                    && (normalizedKeyword.isEmpty()
                    || tableName.toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                    || tableComment.toLowerCase(Locale.ROOT).contains(normalizedKeyword))) {
                filtered.add(table);
            }
        }
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        result.setTotal((long) filtered.size());
        result.setRows(new ArrayList<>(filtered.subList(fromIndex, toIndex)));
        return result;
    }

    /**
     * 获取表详细信息
     * 
     * @param config    数据源配置
     * @param tableName 表名
     * @return 表详细信息
     */
    TableInfo getTableInfo(DataSourceConfig config, String tableName);

    /**
     * 获取表的字段列表
     * 
     * @param config    数据源配置
     * @param tableName 表名
     * @return 字段信息列表
     */
    List<ColumnInfo> getColumns(DataSourceConfig config, String tableName);

    /**
     * Reads the columns needed for structural comparison in batches.
     *
     * <p>Database-specific explorers should override this method with one metadata query. The
     * default keeps compatibility for uncommon connectors while avoiding a breaking contract.
     */
    default Map<String, List<ColumnInfo>> getColumnsByTables(
            DataSourceConfig config, List<String> tableNames) {
        Map<String, List<ColumnInfo>> result = new LinkedHashMap<>();
        if (tableNames == null) {
            return result;
        }
        for (String tableName : tableNames) {
            if (tableName != null && !tableName.isBlank()) {
                result.put(tableName.toLowerCase(Locale.ROOT), getColumns(config, tableName));
            }
        }
        return result;
    }

    /**
     * 获取表的索引列表
     * 
     * @param config    数据源配置
     * @param tableName 表名
     * @return 索引信息列表
     */
    List<IndexInfo> getIndexes(DataSourceConfig config, String tableName);

    /**
     * 获取表记录数
     * 
     * @param config    数据源配置
     * @param tableName 表名
     * @return 记录数
     */
    Long getTableRowCount(DataSourceConfig config, String tableName);

    /**
     * 获取表空间大小
     * 
     * @param config    数据源配置
     * @param tableName 表名
     * @return 表大小（字节）
     */
    Long getTableSize(DataSourceConfig config, String tableName);

    /**
     * 获取数据库空间大小
     * 
     * @param config 数据源配置
     * @return 数据库大小（字节）
     */
    Long getDatabaseSize(DataSourceConfig config);

    /**
     * 数据抽样
     * 
     * @param request 抽样请求
     * @return 抽样结果
     */
    SampleDataResult sampleData(SampleRequest request);

    /**
     * 根据字段信息生成建表 DDL（各数据库可按需覆盖实现）
     *
     * @param config       数据源配置（用于识别数据库类型及 schema 等）
     * @param tableName    表名
     * @param columns      字段列表（需包含字段名、类型、是否主键等）
     * @param tableComment 表注释（可为空）
     * @return 生成的建表 DDL 语句
     */
    default String generateCreateTableDdl(DataSourceConfig config, String tableName, java.util.List<ColumnInfo> columns, String tableComment) {
        throw new UnsupportedOperationException("当前数据库暂不支持建表 DDL 生成");
    }

    /**
     * 根据字段信息生成建表 DDL（各数据库可按需覆盖实现）
     *
     * @param tableName    表名
     * @param columns      字段列表（需包含字段名、类型、是否主键等）
     * @param tableComment 表注释（可为空）
     * @return 生成的建表 DDL 语句
     */
    default String generateCreateTableDdl(String tableName, java.util.List<ColumnInfo> columns, String tableComment) {
        throw new UnsupportedOperationException("当前数据库暂不支持建表 DDL 生成");
    }

    /**
     * 根据传入的 DDL 语句执行建表。
     *
     * @param config 数据源配置
     * @param ddl    建表 DDL 语句
     */
    default void createTable(DataSourceConfig config, String ddl) {
        throw new UnsupportedOperationException("当前数据库暂不支持建表执行");
    }

    /**
     * 校验 SQL 语句语法是否正确（通过实际数据源执行 EXPLAIN / PREPARE 等方式验证）。
     * 各数据库实现类可按需覆盖，默认实现使用 JDBC 的 prepareStatement 进行语法校验。
     *
     * @param config 数据源配置
     * @param sql    待校验的 SQL 语句
     * @return 校验结果
     */
    default SqlValidationResult validateSql(DataSourceConfig config, String sql) {
        throw new UnsupportedOperationException("当前数据库暂不支持 SQL 校验");
    }
}
