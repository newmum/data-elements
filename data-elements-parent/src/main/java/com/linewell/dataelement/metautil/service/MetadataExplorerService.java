package com.linewell.dataelement.metautil.service;

import java.util.List;
import java.util.Map;
import com.linewell.dataelement.metautil.explorer.MetadataExplorer;
import com.linewell.dataelement.metautil.explorer.MetadataExplorerFactory;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 元数据探查服务
 * 
 * @author MetaUtil
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataExplorerService {

    private final MetadataExplorerFactory explorerFactory;

    /**
     * 获取对应的探查器
     */
    private MetadataExplorer getExplorer(DataSourceConfig config) {
        return explorerFactory.getExplorer(config.getDatabaseType());
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection(DataSourceConfig config) {
        log.info("测试连接: {} - {}:{}", config.getDatabaseType(), config.getHost(), config.getPortOrDefault());
        return getExplorer(config).testConnection(config);
    }

    /**
     * 获取数据库信息
     */
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        log.info("获取数据库信息: {} - {}", config.getDatabaseType(), config.getDatabase());
        return getExplorer(config).getDatabaseInfo(config);
    }

    /**
     * 获取数据库列表
     */
    public List<String> getDatabases(DataSourceConfig config) {
        log.info("获取数据库列表: {} - {}:{}", config.getDatabaseType(), config.getHost(), config.getPortOrDefault());
        return getExplorer(config).getDatabases(config);
    }

    /**
     * 获取Schema列表
     */
    public List<String> getSchemas(DataSourceConfig config) {
        log.info("获取Schema列表: {} - {}", config.getDatabaseType(), config.getDatabase());
        return getExplorer(config).getSchemas(config);
    }

    /**
     * 获取当前连接实际可探查、且至少包含一个表或视图的 Schema 列表。
     */
    public List<String> getSchemasWithTables(DataSourceConfig config) {
        log.info("获取可探查Schema列表: {} - {}", config.getDatabaseType(), config.getDatabase());
        return getExplorer(config).getSchemasWithTables(config);
    }

    /**
     * 获取表列表
     */
    public List<TableInfo> getTables(DataSourceConfig config) {
        log.info("获取表列表: {} - {}", config.getDatabaseType(), config.getDatabase());
        return getExplorer(config).getTables(config);
    }

    /**
     * 分页获取表/视图列表，供登记流程第二步“加载更多”使用。
     *
     * <p>大库场景（如 Oracle 几千张表/视图）必须优先由具体 Explorer 在数据库侧分页，
     * 防止一次返回全部表导致 Magic 接口超时、前端渲染空白或卡死。</p>
     */
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        log.info("分页获取表列表: {} - {}, pageNo={}, pageSize={}, keyword={}",
            config.getDatabaseType(), config.getDatabase(), pageNo, pageSize, keyword);
        return getExplorer(config).getTablesPage(config, pageNo, pageSize, keyword);
    }

    public TablePageResult getTablesPageExcluding(
            DataSourceConfig config,
            Integer pageNo,
            Integer pageSize,
            String keyword,
            List<String> excludedTableNames
    ) {
        log.info("Paged table lookup excluding managed tables: {} - {}, pageNo={}, pageSize={}, excluded={}",
                config.getDatabaseType(), config.getDatabase(), pageNo, pageSize,
                excludedTableNames == null ? 0 : excludedTableNames.size());
        return getExplorer(config).getTablesPageExcluding(
                config, pageNo, pageSize, keyword, excludedTableNames);
    }

    /**
     * 获取表详细信息
     */
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        log.info("获取表详情: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).getTableInfo(config, tableName);
    }

    /**
     * 获取字段列表
     */
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        log.info("获取字段列表: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).getColumns(config, tableName);
    }

    /**
     * Batch column lookup used by database structure comparison.
     */
    public Map<String, List<ColumnInfo>> getColumnsByTables(
            DataSourceConfig config, List<String> tableNames) {
        log.info("Batch column lookup: {} - {}, tables={}",
                config.getDatabaseType(), config.getDatabase(),
                tableNames == null ? 0 : tableNames.size());
        return getExplorer(config).getColumnsByTables(config, tableNames);
    }

    /**
     * 获取索引列表
     */
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        log.info("获取索引列表: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).getIndexes(config, tableName);
    }

    /**
     * 获取表记录数
     */
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        log.info("获取表记录数: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).getTableRowCount(config, tableName);
    }

    /**
     * 获取表空间大小
     */
    public Long getTableSize(DataSourceConfig config, String tableName) {
        log.info("获取表大小: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).getTableSize(config, tableName);
    }

    /**
     * 获取数据库空间大小
     */
    public Long getDatabaseSize(DataSourceConfig config) {
        log.info("获取数据库大小: {} - {}", config.getDatabaseType(), config.getDatabase());
        return getExplorer(config).getDatabaseSize(config);
    }

    /**
     * 数据抽样
     */
    public SampleDataResult sampleData(SampleRequest request) {
        log.info("数据抽样: {} - {}.{}, 数量: {}", 
                request.getDataSource().getDatabaseType(),
                request.getDataSource().getDatabase(),
                request.getTableName(),
                request.getSampleSize());
        return getExplorer(request.getDataSource()).sampleData(request);
    }


    /**
     * 生成建表 DDL
     */
    public String generateCreateTableDdl(DataSourceConfig config, String tableName, List<ColumnInfo> columns, String tableComment) {
        log.info("生成建表DDL: {} - {}.{}", config.getDatabaseType(), config.getDatabase(), tableName);
        return getExplorer(config).generateCreateTableDdl(config, tableName, columns, tableComment);
    }

    /**
     * 执行建表 DDL
     */
    public void createTable(DataSourceConfig config, String ddl) {
        log.info("执行建表DDL: {} - {}:{}", config.getDatabaseType(), config.getHost(), config.getPortOrDefault());
        getExplorer(config).createTable(config, ddl);
    }

    /**
     * 校验 SQL 语句语法是否正确
     * <p>
     * 支持不同数据库类型的语法校验，根据数据库类型自动选择最优校验策略：
     * <ul>
     *   <li>MySQL / OceanBase(MySQL) / Hive：使用 EXPLAIN 语法校验</li>
     *   <li>PostgreSQL / KingBase / GaussDB：使用 PREPARE 语法校验</li>
     *   <li>Oracle / OceanBase(Oracle) / 达梦：使用 EXPLAIN PLAN FOR 语法校验</li>
     *   <li>DDL 语句：使用 JDBC prepareStatement 进行语法校验</li>
     * </ul>
     * </p>
     *
     * @param config 数据源配置
     * @param sql    待校验的 SQL 语句
     * @return 校验结果，包含是否通过、错误信息、SQL 类型等
     */
    public SqlValidationResult validateSql(DataSourceConfig config, String sql) {
        log.info("校验SQL语法: {} - {}:{}", config.getDatabaseType(), config.getHost(), config.getPortOrDefault());
        return getExplorer(config).validateSql(config, sql);
    }
}
