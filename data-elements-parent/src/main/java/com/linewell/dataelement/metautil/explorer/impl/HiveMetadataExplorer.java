package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Hive 元数据探查器
 * 
 * @author MetaUtil
 */
@Slf4j
public class HiveMetadataExplorer extends AbstractMetadataExplorer {

    @Override
    public String generateCreateTableDdl(DataSourceConfig config, String tableName, List<ColumnInfo> columns, String tableComment) {
        return com.linewell.dataelement.metautil.ddl.HiveTableDdl.generate(tableName, columns, tableComment);
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("Hive");
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 获取 Hive 版本
            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            } catch (Exception e) {
                // 某些 Hive 版本可能不支持 VERSION()
                log.warn("获取Hive版本失败: {}", e.getMessage());
            }
            
            // 切换数据库后统计对象数量
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }

            List<String> objectNames = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank()) {
                        objectNames.add(name.trim());
                    }
                }
            }

            int viewCount = resolveHiveViewCount(stmt, objectNames);
            int tableCount = Math.max(objectNames.size() - viewCount, 0);
            info.setViewCount(viewCount);
            info.setTableCount(tableCount);
            info.setProcedureCount(0);
            info.setUserCount(0);

            Long totalSizeBytes = sumHiveObjectSize(stmt, objectNames);
            if (totalSizeBytes != null && totalSizeBytes > 0) {
                info.setTotalSizeBytes(totalSizeBytes);
                info.setTotalSizeFormatted(formatBytes(totalSizeBytes));
                info.setDataSizeBytes(totalSizeBytes);
                info.setDataSizeFormatted(info.getTotalSizeFormatted());
            }
            
            // 获取数据库列表
            info.setDatabases(getDatabases(config));
            
        } catch (Exception e) {
            log.error("获取Hive数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        
        return info;
    }

    private int resolveHiveViewCount(Statement stmt, List<String> tableNames) {
        int viewCount = 0;
        boolean resolvedByShowViews = false;

        // 优先用 SHOW VIEWS，性能更高；旧版本 Hive 可能不支持
        try (ResultSet rs = stmt.executeQuery("SHOW VIEWS")) {
            resolvedByShowViews = true;
            while (rs.next()) {
                viewCount++;
            }
        } catch (Exception e) {
            log.warn("SHOW VIEWS 不可用，回退 DESCRIBE FORMATTED 统计视图: {}", e.getMessage());
        }

        if (resolvedByShowViews) {
            return viewCount;
        }

        for (String tableName : tableNames) {
            if (isHiveView(stmt, tableName)) {
                viewCount++;
            }
        }
        return viewCount;
    }

    private boolean isHiveView(Statement stmt, String tableName) {
        String sql = "DESCRIBE FORMATTED " + tableName;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String colName = rs.getString(1);
                String value = rs.getString(2);
                if (colName == null) {
                    continue;
                }
                String key = colName.trim();
                if (!"Table Type:".equalsIgnoreCase(key)) {
                    continue;
                }
                String type = value == null ? "" : value.trim().toUpperCase();
                return type.contains("VIEW");
            }
        } catch (Exception e) {
            log.warn("统计视图类型失败: table={}, err={}", tableName, e.getMessage());
        }
        return false;
    }

    private Long sumHiveObjectSize(Statement stmt, List<String> tableNames) {
        long totalSize = 0L;
        boolean found = false;
        for (String tableName : tableNames) {
            String sql = "SHOW TBLPROPERTIES " + tableName + "('totalSize')";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Long value = parseLongSafe(readValue(rs, 2));
                    if (value == null) {
                        value = parseLongSafe(readValue(rs, 1));
                    }
                    if (value != null && value > 0) {
                        totalSize += value;
                        found = true;
                    }
                }
            } catch (Exception e) {
                log.debug("获取对象大小失败: table={}, err={}", tableName, e.getMessage());
            }
        }
        return found ? totalSize : 0L;
    }

    private String readValue(ResultSet rs, int index) {
        try {
            return rs.getString(index);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> databases = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            
            while (rs.next()) {
                String dbName = rs.getString(1);
                // 过滤默认数据库
                if (!"default".equals(dbName)) {
                    databases.add(dbName);
                }
            }
            
        } catch (Exception e) {
            log.error("获取数据库列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库列表失败: " + e.getMessage(), e);
        }
        
        return databases;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        // Hive 没有 Schema 概念，返回数据库列表
        return getDatabases(config);
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 切换数据库
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }
            
            // 获取表列表
            List<String> tableNames = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tableNames.add(rs.getString(1));
                }
            }
            
            // 获取每个表的详细信息
            for (String tableName : tableNames) {
                TableInfo table = new TableInfo();
                table.setTableName(tableName);
                table.setSchemaName(config.getDatabase());
                
                // 获取表类型和注释
                try {
                    String descSql = "DESCRIBE FORMATTED " + tableName;
                    try (ResultSet rs = stmt.executeQuery(descSql)) {
                        while (rs.next()) {
                            String colName = rs.getString(1);
                            String dataType = rs.getString(2);
                            
                            if (colName != null) {
                                colName = colName.trim();
                                if ("Table Type:".equals(colName) && dataType != null) {
                                    table.setTableType(dataType.trim());
                                } else if ("comment".equalsIgnoreCase(colName) && dataType != null) {
                                    table.setTableComment(dataType.trim());
                                } else if ("transient_lastDdlTime".equalsIgnoreCase(colName) && dataType != null) {
                                    try {
                                        long timestamp = Long.parseLong(dataType.trim());
                                        java.util.Date ddlTime = new java.util.Date(timestamp * 1000);
                                        table.setUpdateTime(ddlTime);
                                        table.setDataUpdateTime(ddlTime);
                                    } catch (NumberFormatException e) {
                                        log.warn("Failed to parse transient_lastDdlTime: {}", dataType);
                                    }
                                } else if ("Location:".equals(colName) && dataType != null) {
                                    // 可以从 Location 解析存储位置
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取表 {} 详细信息失败: {}", tableName, e.getMessage());
                }
                
                // 尝试获取表大小（某些 Hive 配置可能不支持）
                try {
                    String sizeSql = "SHOW TBLPROPERTIES " + tableName + "('totalSize')";
                    try (ResultSet rs = stmt.executeQuery(sizeSql)) {
                        if (rs.next()) {
                            String sizeStr = rs.getString(2);
                            if (sizeStr != null && !sizeStr.isEmpty()) {
                                table.setTotalSizeBytes(Long.parseLong(sizeStr.trim()));
                                table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略，某些配置不支持
                }
                
                // 尝试获取行数（从表属性）
                try {
                    String countSql = "SHOW TBLPROPERTIES " + tableName + "('numRows')";
                    try (ResultSet rs = stmt.executeQuery(countSql)) {
                        if (rs.next()) {
                            String countStr = rs.getString(2);
                            if (countStr != null && !countStr.isEmpty() && !"-1".equals(countStr.trim())) {
                                table.setRowCount(Long.parseLong(countStr.trim()));
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
                
                tables.add(table);
            }

            for (TableInfo table : tables) {
                try {
                    Integer columnCount = null;
                    String descSql = "DESCRIBE " + table.getTableName();
                    try (ResultSet rs = stmt.executeQuery(descSql)) {
                        int cnt = 0;
                        while (rs.next()) {
                            String colName = rs.getString(1);
                            if (colName == null) {
                                continue;
                            }
                            String trimmed = colName.trim();
                            if (trimmed.isEmpty()) {
                                continue;
                            }
                            if (trimmed.startsWith("#")) {
                                break;
                            }
                            cnt++;
                        }
                        columnCount = cnt;
                    }
                    if (columnCount != null) {
                        table.setColumnCount(columnCount);
                    }
                } catch (Exception e) {
                    log.warn("获取表 {} 字段数失败: {}", table.getTableName(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        
        return tables;
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        TablePageResult page = new TablePageResult();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();

        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }

            List<String> matchedNames = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    String tableName = name.trim();
                    if (!normalizedKeyword.isEmpty() && !tableName.toLowerCase().contains(normalizedKeyword)) {
                        continue;
                    }
                    matchedNames.add(tableName);
                }
            }
            page.setTotal((long) matchedNames.size());

            int end = Math.min(offset + safePageSize, matchedNames.size());
            if (offset < matchedNames.size()) {
                for (String tableName : matchedNames.subList(offset, end)) {
                    rows.add(readHiveTableSummary(stmt, config, tableName));
                }
            }
            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.error("Hive分页获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("Hive分页获取表列表失败: " + e.getMessage(), e);
        }
    }

    private TableInfo readHiveTableSummary(Statement stmt, DataSourceConfig config, String tableName) {
        TableInfo table = new TableInfo();
        table.setTableName(tableName);
        table.setSchemaName(config.getDatabase());
        table.setTableType("TABLE");
        table.setRowCount(0L);
        table.setTotalSizeBytes(0L);
        table.setTotalSizeFormatted(formatBytes(0L));

        try (ResultSet rs = stmt.executeQuery("DESCRIBE FORMATTED " + tableName)) {
            while (rs.next()) {
                String colName = readValue(rs, 1);
                String dataType = readValue(rs, 2);
                if (colName == null) {
                    continue;
                }
                String key = colName.trim();
                if ("Table Type:".equalsIgnoreCase(key) && dataType != null) {
                    table.setTableType(dataType.trim().toUpperCase().contains("VIEW") ? "VIEW" : "TABLE");
                } else if ("comment".equalsIgnoreCase(key) && dataType != null) {
                    table.setTableComment(dataType.trim());
                } else if ("transient_lastDdlTime".equalsIgnoreCase(key) && dataType != null) {
                    Long timestamp = parseLongSafe(dataType);
                    if (timestamp != null && timestamp > 0) {
                        java.util.Date ddlTime = new java.util.Date(timestamp * 1000);
                        table.setUpdateTime(ddlTime);
                        table.setDataUpdateTime(ddlTime);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取Hive表摘要失败: table={}, err={}", tableName, e.getMessage());
        }

        try (ResultSet rs = stmt.executeQuery("DESCRIBE " + tableName)) {
            int count = 0;
            while (rs.next()) {
                String colName = readValue(rs, 1);
                if (colName == null) {
                    continue;
                }
                String trimmed = colName.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    break;
                }
                count++;
            }
            table.setColumnCount(count);
        } catch (Exception e) {
            log.warn("读取Hive字段数失败: table={}, err={}", tableName, e.getMessage());
            table.setColumnCount(0);
        }
        return table;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        List<TableInfo> tables = getTables(config);
        TableInfo tableInfo = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("表不存在: " + tableName));
        
        tableInfo.setColumns(getColumns(config, tableName));
        
        return tableInfo;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 切换数据库
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }
            
            // 使用 DESCRIBE 获取列信息
            String sql = "DESCRIBE " + tableName;
            int position = 1;
            boolean isPartitionSection = false;
            
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String colName = rs.getString(1);
                    String dataType = rs.getString(2);
                    String comment = null;
                    
                    try {
                        comment = rs.getString(3);
                    } catch (Exception e) {
                        // 某些版本可能没有第三列
                    }
                    
                    if (colName == null || colName.trim().isEmpty()) {
                        continue;
                    }
                    
                    colName = colName.trim();
                    
                    // 检查是否进入分区列部分
                    if (colName.startsWith("#") || colName.contains("Partition")) {
                        isPartitionSection = true;
                        continue;
                    }
                    
                    // 跳过空行和分隔符
                    if (colName.isEmpty() || colName.equals("") || 
                        colName.startsWith("# col_name") || colName.startsWith("----")) {
                        continue;
                    }
                    
                    ColumnInfo column = new ColumnInfo();
                    column.setColumnName(colName);
                    column.setDataType(dataType != null ? dataType.trim() : "");
                    column.setColumnType(column.getDataType());
                    column.setColumnComment(comment != null ? comment.trim() : "");
                    column.setOrdinalPosition(position++);
                    column.setNullable(true);
                    
                    // 解析类型中的长度信息
                    parseHiveType(column);
                    
                    columns.add(column);
                }
            }
            
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        
        return columns;
    }

    /**
     * 解析 Hive 类型，提取长度等信息
     */
    private void parseHiveType(ColumnInfo column) {
        String type = column.getDataType();
        if (type == null) {
            return;
        }
        
        // 匹配 varchar(100)、char(50)、decimal(10,2) 等格式
        Pattern pattern = Pattern.compile("(\\w+)\\((\\d+)(?:,(\\d+))?\\)");
        Matcher matcher = pattern.matcher(type);
        
        if (matcher.matches()) {
            column.setDataType(matcher.group(1));
            column.setLength(Long.parseLong(matcher.group(2)));
            if (matcher.group(3) != null) {
                column.setScale(Integer.parseInt(matcher.group(3)));
                column.setPrecision(column.getLength().intValue());
            }
        }
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        // Hive 3.0+ 移除了索引支持，返回空列表
        return new ArrayList<>();
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 切换数据库
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }
            
            String sql = "SELECT COUNT(*) FROM " + tableName;
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            
        } catch (Exception e) {
            log.error("获取表记录数失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表记录数失败: " + e.getMessage(), e);
        }
        
        return 0L;
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }
            
            // 尝试从表属性获取大小
            String sql = "SHOW TBLPROPERTIES " + tableName + "('totalSize')";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    String sizeStr = rs.getString(2);
                    if (sizeStr != null && !sizeStr.isEmpty()) {
                        return Long.parseLong(sizeStr.trim());
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("获取表大小失败: {}", e.getMessage());
        }
        
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        // Hive 没有直接获取数据库大小的方法
        // 需要累加所有表的大小
        Long totalSize = 0L;
        
        try {
            List<TableInfo> tables = getTables(config);
            for (TableInfo table : tables) {
                if (table.getTotalSizeBytes() != null) {
                    totalSize += table.getTotalSizeBytes();
                }
            }
        } catch (Exception e) {
            log.warn("获取数据库大小失败: {}", e.getMessage());
        }
        
        return totalSize;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        String columns = buildColumnSelection(request.getColumns());
        String where = buildWhereClause(request.getWhereClause());
        String tableName = request.getTableName();
        int sampleSize = request.getSampleSize();
        
        String sql;
        switch (request.getSampleMethod()) {
            case RANDOM:
                // Hive 使用 TABLESAMPLE 进行随机采样
                sql = String.format(
                    "SELECT %s FROM %s TABLESAMPLE(BUCKET 1 OUT OF 10 ON rand()) %s LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
            case LAST:
                // Hive 不支持 ORDER BY 后取 LAST，使用子查询
                sql = String.format(
                    "SELECT %s FROM %s %s ORDER BY 1 DESC LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
            case FIRST:
            default:
                sql = String.format(
                    "SELECT %s FROM %s %s LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
        }
        
        // 先切换数据库
        DataSourceConfig config = request.getDataSource();
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
                stmt.execute("USE " + config.getDatabase());
            }
        } catch (Exception e) {
            log.warn("切换数据库失败: {}", e.getMessage());
        }
        
        return executeSampleQuery(request.getDataSource(), request, sql);
    }
}
