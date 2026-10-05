package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.exception.DataSourceConnectionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.MetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

/**
 * MongoDB 元数据探查器
 * 
 * @author MetaUtil
 */
@Slf4j
public class MongoDbMetadataExplorer implements MetadataExplorer {

    /**
     * 创建 MongoDB 客户端
     */
    private MongoClient createClient(DataSourceConfig config) {
        String connectionString;
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            connectionString = String.format(
                "mongodb://%s:%s@%s:%d/%s?authSource=admin",
                config.getUsername(),
                config.getPassword(),
                config.getHost(),
                config.getPortOrDefault(),
                config.getDatabase() != null ? config.getDatabase() : "admin"
            );
        } else {
            connectionString = String.format(
                "mongodb://%s:%d",
                config.getHost(),
                config.getPortOrDefault()
            );
        }
        
        return MongoClients.create(connectionString);
    }

    @Override
    public boolean testConnection(DataSourceConfig config) {
        try (MongoClient client = createClient(config)) {
            // 尝试列出数据库来测试连接
            client.listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            log.error("MongoDB连接测试失败: {}", e.getMessage(), e);
            throw new DataSourceConnectionException("MongoDB 连接测试失败", e);
        }
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("MongoDB");
        
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            
            // 获取数据库统计信息
            Document stats = database.runCommand(new Document("dbStats", 1));

            Long dataSize = readLong(stats, "dataSize");
            Long indexSize = readLong(stats, "indexSize");
            Long totalSize = readLong(stats, "totalSize");
            if (totalSize == null || totalSize <= 0) {
                Long storageSize = readLong(stats, "storageSize");
                if (storageSize != null && storageSize > 0) {
                    totalSize = storageSize;
                } else if (dataSize != null && indexSize != null) {
                    totalSize = dataSize + indexSize;
                }
            }

            info.setDataSizeBytes(dataSize);
            info.setDataSizeFormatted(formatBytes(dataSize));
            info.setIndexSizeBytes(indexSize);
            info.setIndexSizeFormatted(formatBytes(indexSize));
            info.setTotalSizeBytes(totalSize);
            info.setTotalSizeFormatted(formatBytes(totalSize));
            info.setTableCount(readInteger(stats, "collections"));
            info.setViewCount(readInteger(stats, "views"));
            info.setProcedureCount(0);

            // 获取服务器版本
            Document buildInfo = database.runCommand(new Document("buildInfo", 1));
            info.setVersion(buildInfo.getString("version"));

            // 获取用户数量（权限不足时兜底 0）
            try {
                Document usersInfo = database.runCommand(new Document("usersInfo", 1));
                Object usersObj = usersInfo.get("users");
                if (usersObj instanceof List<?> users) {
                    info.setUserCount(users.size());
                }
            } catch (Exception e) {
                log.warn("获取MongoDB用户数量失败: {}", e.getMessage());
                info.setUserCount(0);
            }
            
            // 获取数据库列表
            info.setDatabases(getDatabases(config));
            
        } catch (Exception e) {
            log.error("获取MongoDB数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        
        return info;
    }

    private Long readLong(Document doc, String key) {
        if (doc == null || key == null) {
            return null;
        }
        Object value = doc.get(key);
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Double d) {
            return d.longValue();
        }
        if (value instanceof org.bson.types.Decimal128 d128) {
            try {
                return d128.bigDecimalValue().longValue();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Integer readInteger(Document doc, String key) {
        if (doc == null || key == null) {
            return 0;
        }
        Object value = doc.get(key);
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            return l.intValue();
        }
        if (value instanceof Double d) {
            return d.intValue();
        }
        return 0;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> databases = new ArrayList<>();
        
        try (MongoClient client = createClient(config)) {
            for (String dbName : client.listDatabaseNames()) {
                // 过滤系统数据库
                if (!"admin".equals(dbName) && !"local".equals(dbName) && !"config".equals(dbName)) {
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
        // MongoDB 没有 Schema 概念，返回空列表
        return new ArrayList<>();
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            
            for (String collectionName : database.listCollectionNames()) {
                TableInfo table = new TableInfo();
                table.setTableName(collectionName);
                table.setTableType("COLLECTION");
                
                // 获取集合统计信息
                try {
                    Document stats = database.runCommand(new Document("collStats", collectionName));
                    table.setRowCount(stats.getLong("count"));
                    
                    // MongoDB 返回的大小可能是 Integer 或 Long
                    Object sizeObj = stats.get("size");
                    if (sizeObj instanceof Integer) {
                        table.setDataSizeBytes(((Integer) sizeObj).longValue());
                    } else if (sizeObj instanceof Long) {
                        table.setDataSizeBytes((Long) sizeObj);
                    }
                    
                    Object indexSizeObj = stats.get("totalIndexSize");
                    if (indexSizeObj instanceof Integer) {
                        table.setIndexSizeBytes(((Integer) indexSizeObj).longValue());
                    } else if (indexSizeObj instanceof Long) {
                        table.setIndexSizeBytes((Long) indexSizeObj);
                    }
                    
                    if (table.getDataSizeBytes() != null && table.getIndexSizeBytes() != null) {
                        table.setTotalSizeBytes(table.getDataSizeBytes() + table.getIndexSizeBytes());
                    }
                    
                    table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
                    table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
                    table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
                    
                } catch (Exception e) {
                    log.warn("获取集合 {} 统计信息失败: {}", collectionName, e.getMessage());
                }
                
                tables.add(table);
            }
            
        } catch (Exception e) {
            log.error("获取集合列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取集合列表失败: " + e.getMessage(), e);
        }

        // 统计每个集合的字段数量（基于采样推断的字段列表）
        for (TableInfo table : tables) {
            try {
                int columnCount = getColumns(config, table.getTableName()).size();
                table.setColumnCount(columnCount);
            } catch (Exception e) {
                log.warn("获取集合 {} 字段数失败: {}", table.getTableName(), e.getMessage());
            }
        }
        
        return tables;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        List<TableInfo> tables = getTables(config);
        TableInfo tableInfo = tables.stream()
                .filter(t -> t.getTableName().equals(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("集合不存在: " + tableName));
        
        tableInfo.setColumns(getColumns(config, tableName));
        tableInfo.setIndexes(getIndexes(config, tableName));
        
        return tableInfo;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            MongoCollection<Document> collection = database.getCollection(tableName);
            
            // 通过采样文档推断字段结构
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            int sampleCount = 0;
            int maxSample = 100;
            
            for (Document doc : collection.find().limit(maxSample)) {
                analyzeDocument(doc, "", fieldTypes);
                sampleCount++;
            }
            
            int position = 1;
            for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(entry.getKey());
                column.setDataType(entry.getValue());
                column.setColumnType(entry.getValue());
                column.setOrdinalPosition(position++);
                column.setNullable(true); // MongoDB 字段都是可选的
                column.setPrimaryKey("_id".equals(entry.getKey()));
                
                columns.add(column);
            }
            
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        
        return columns;
    }

    /**
     * 分析文档结构，推断字段类型
     */
    private void analyzeDocument(Document doc, String prefix, Map<String, String> fieldTypes) {
        for (String key : doc.keySet()) {
            String fieldName = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);
            
            String type = getMongoType(value);
            
            // 如果字段已存在且类型不同，标记为 mixed
            if (fieldTypes.containsKey(fieldName)) {
                String existingType = fieldTypes.get(fieldName);
                if (!existingType.equals(type) && !existingType.equals("mixed")) {
                    fieldTypes.put(fieldName, "mixed");
                }
            } else {
                fieldTypes.put(fieldName, type);
            }
            
            // 递归处理嵌套文档
            if (value instanceof Document) {
                analyzeDocument((Document) value, fieldName, fieldTypes);
            }
        }
    }

    /**
     * 获取 MongoDB 值的类型
     */
    private String getMongoType(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer) {
            return "int32";
        } else if (value instanceof Long) {
            return "int64";
        } else if (value instanceof Double) {
            return "double";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Date) {
            return "date";
        } else if (value instanceof Document) {
            return "object";
        } else if (value instanceof List) {
            return "array";
        } else if (value instanceof org.bson.types.ObjectId) {
            return "objectId";
        } else if (value instanceof org.bson.types.Binary) {
            return "binary";
        } else {
            return value.getClass().getSimpleName();
        }
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            MongoCollection<Document> collection = database.getCollection(tableName);
            
            for (Document indexDoc : collection.listIndexes()) {
                IndexInfo index = new IndexInfo();
                index.setIndexName(indexDoc.getString("name"));
                
                // 判断是否唯一索引
                Boolean unique = indexDoc.getBoolean("unique", false);
                index.setUnique(unique);
                
                // 判断是否主键索引
                index.setPrimaryKey("_id_".equals(index.getIndexName()));
                
                // 获取索引键
                Document keyDoc = (Document) indexDoc.get("key");
                if (keyDoc != null) {
                    index.setColumns(new ArrayList<>(keyDoc.keySet()));
                }
                
                indexes.add(index);
            }
            
        } catch (Exception e) {
            log.error("获取索引列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取索引列表失败: " + e.getMessage(), e);
        }
        
        return indexes;
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            MongoCollection<Document> collection = database.getCollection(tableName);
            return collection.countDocuments();
        } catch (Exception e) {
            log.error("获取集合记录数失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取集合记录数失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            Document stats = database.runCommand(new Document("collStats", tableName));
            
            Object sizeObj = stats.get("size");
            if (sizeObj instanceof Integer) {
                return ((Integer) sizeObj).longValue();
            } else if (sizeObj instanceof Long) {
                return (Long) sizeObj;
            }
            return 0L;
            
        } catch (Exception e) {
            log.error("获取集合大小失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取集合大小失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        try (MongoClient client = createClient(config)) {
            MongoDatabase database = client.getDatabase(config.getDatabase());
            Document stats = database.runCommand(new Document("dbStats", 1));
            return stats.getLong("dataSize");
        } catch (Exception e) {
            log.error("获取数据库大小失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库大小失败: " + e.getMessage(), e);
        }
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        SampleDataResult result = new SampleDataResult();
        result.setTableName(request.getTableName());
        result.setSampleSize(request.getSampleSize());
        result.setSampleMethod(request.getSampleMethod().name());
        
        List<Map<String, Object>> data = new ArrayList<>();
        Set<String> columnNameSet = new LinkedHashSet<>();
        
        try (MongoClient client = createClient(request.getDataSource())) {
            MongoDatabase database = client.getDatabase(request.getDataSource().getDatabase());
            MongoCollection<Document> collection = database.getCollection(request.getTableName());
            
            // 获取总记录数
            result.setTotalCount(collection.countDocuments());
            
            // 构建查询
            FindIterable<Document> query = collection.find();
            
            switch (request.getSampleMethod()) {
                case RANDOM:
                    // MongoDB 随机采样使用 $sample 聚合管道
                    for (Document doc : collection.aggregate(
                            Collections.singletonList(new Document("$sample", new Document("size", request.getSampleSize()))))) {
                        Map<String, Object> row = flattenDocument(doc, "");
                        columnNameSet.addAll(row.keySet());
                        data.add(row);
                    }
                    break;
                    
                case LAST:
                    // 按 _id 倒序取最后 N 条
                    List<Document> lastDocs = new ArrayList<>();
                    for (Document doc : collection.find()
                            .sort(new Document("_id", -1))
                            .limit(request.getSampleSize())) {
                        lastDocs.add(doc);
                    }
                    // 反转顺序
                    Collections.reverse(lastDocs);
                    for (Document doc : lastDocs) {
                        Map<String, Object> row = flattenDocument(doc, "");
                        columnNameSet.addAll(row.keySet());
                        data.add(row);
                    }
                    break;
                    
                case FIRST:
                default:
                    for (Document doc : collection.find().limit(request.getSampleSize())) {
                        Map<String, Object> row = flattenDocument(doc, "");
                        columnNameSet.addAll(row.keySet());
                        data.add(row);
                    }
                    break;
            }
            
        } catch (Exception e) {
            log.error("数据抽样失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据抽样失败: " + e.getMessage(), e);
        }
        
        result.setColumnNames(new ArrayList<>(columnNameSet));
        result.setData(data);
        result.setActualSize(data.size());
        
        return result;
    }

    /**
     * 扁平化 MongoDB 文档
     */
    private Map<String, Object> flattenDocument(Document doc, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (String key : doc.keySet()) {
            String fieldName = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = doc.get(key);
            
            if (value instanceof Document) {
                result.putAll(flattenDocument((Document) value, fieldName));
            } else if (value instanceof org.bson.types.ObjectId) {
                result.put(fieldName, value.toString());
            } else {
                result.put(fieldName, value);
            }
        }
        
        return result;
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        
        return String.format("%.2f %s", 
                bytes / Math.pow(1024, digitGroups), 
                units[digitGroups]);
    }
}
