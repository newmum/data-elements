package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Virtual metadata explorer for non-tabular datasource types.
 *
 * <p>The registration workflow is table/field oriented. Object stores,
 * message queues and APIs are therefore represented as stable "resource
 * tables" so the same five-step UI can classify, review and finish them.</p>
 */
@Slf4j
public class VirtualResourceMetadataExplorer extends AbstractMetadataExplorer {

    @Override
    public boolean testConnection(DataSourceConfig config) {
        return config != null && config.getDatabaseType() != null;
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(resourceName(config));
        info.setDatabaseType(config.getDatabaseType().getDisplayName());
        info.setVersion("virtual-resource");
        info.setTableCount(getTables(config).size());
        info.setViewCount(0);
        info.setTotalSizeBytes(0L);
        info.setTotalSizeFormatted(formatBytes(0L));
        info.setSchemas(List.of(resourceName(config)));
        info.setDatabases(List.of(resourceName(config)));
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        return List.of(resourceName(config));
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        return List.of(resourceName(config));
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        TableInfo table = new TableInfo();
        table.setTableName(defaultTableName(config));
        table.setTableComment(defaultTableComment(config));
        table.setSchemaName(resourceName(config));
        table.setTableType("TABLE");
        table.setColumnCount(getColumns(config, table.getTableName()).size());
        table.setRowCount(0L);
        table.setDataSizeBytes(0L);
        table.setIndexSizeBytes(0L);
        table.setTotalSizeBytes(0L);
        table.setDataSizeFormatted(formatBytes(0L));
        table.setIndexSizeFormatted(formatBytes(0L));
        table.setTotalSizeFormatted(formatBytes(0L));
        return List.of(table);
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        TableInfo table = getTables(config).get(0);
        table.setColumns(getColumns(config, tableName));
        table.setIndexes(Collections.emptyList());
        return table;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        int position = 1;
        for (Map.Entry<String, String> entry : defaultColumns(config.getDatabaseType()).entrySet()) {
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(entry.getKey());
            column.setColumnComment(entry.getValue());
            column.setDataType("VARCHAR");
            column.setColumnType("VARCHAR(1024)");
            column.setLength(1024L);
            column.setNullable(true);
            column.setPrimaryKey(false);
            column.setAutoIncrement(false);
            column.setUnique(false);
            column.setIndexed(false);
            column.setOrdinalPosition(position++);
            columns.add(column);
        }
        return columns;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        return 0L;
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        return 0L;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        SampleDataResult result = new SampleDataResult();
        result.setTableName(defaultTableName(request.getDataSource()));
        result.setSampleSize(request.getSampleSize());
        result.setSampleMethod(request.getSampleMethod().name());
        List<ColumnInfo> columns = getColumns(request.getDataSource(), result.getTableName());
        List<String> names = columns.stream().map(ColumnInfo::getColumnName).toList();
        result.setColumnNames(names);
        result.setColumnTypes(columns.stream().map(ColumnInfo::getColumnType).toList());
        Map<String, Object> row = new LinkedHashMap<>();
        for (String name : names) {
            row.put(name, sampleValue(request.getDataSource().getDatabaseType(), name));
        }
        result.setData(List.of(row));
        result.setActualSize(1);
        result.setTotalCount(0L);
        return result;
    }

    private String resourceName(DataSourceConfig config) {
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            return config.getDatabase();
        }
        if (config.getSchema() != null && !config.getSchema().isBlank()) {
            return config.getSchema();
        }
        return config.getDatabaseType().getCode() + "_resource";
    }

    private String defaultTableName(DataSourceConfig config) {
        return switch (config.getDatabaseType()) {
            case MINIO -> "minio_object_resource";
            case FTP -> "ftp_file_resource";
            case API -> "api_endpoint_resource";
            case KAFKA -> "kafka_topic_message";
            case ELASTICSEARCH -> "elasticsearch_index_document";
            default -> config.getDatabaseType().getCode().toLowerCase(Locale.ROOT) + "_resource";
        };
    }

    private String defaultTableComment(DataSourceConfig config) {
        return switch (config.getDatabaseType()) {
            case MINIO -> "MinIO对象资源";
            case FTP -> "FTP文件资源";
            case API -> "API接口资源";
            case KAFKA -> "Kafka主题消息";
            case ELASTICSEARCH -> "Elasticsearch索引文档";
            default -> config.getDatabaseType().getDisplayName() + "资源";
        };
    }

    private Map<String, String> defaultColumns(DatabaseType type) {
        Map<String, String> columns = new LinkedHashMap<>();
        switch (type) {
            case MINIO -> {
                columns.put("bucket_name", "存储桶名称");
                columns.put("object_key", "对象路径");
                columns.put("object_size", "对象大小");
                columns.put("content_type", "内容类型");
                columns.put("last_modified_time", "最后修改时间");
                columns.put("etag", "对象ETag");
            }
            case FTP -> {
                columns.put("remote_path", "远程路径");
                columns.put("file_name", "文件名称");
                columns.put("file_type", "文件类型");
                columns.put("file_size", "文件大小");
                columns.put("modified_time", "修改时间");
                columns.put("checksum", "校验值");
            }
            case API -> {
                columns.put("endpoint_url", "接口地址");
                columns.put("http_method", "请求方法");
                columns.put("request_body", "请求体");
                columns.put("response_body", "响应体");
                columns.put("status_code", "响应状态码");
                columns.put("request_time", "请求时间");
            }
            case KAFKA -> {
                columns.put("topic_name", "主题名称");
                columns.put("message_key", "消息键");
                columns.put("message_value", "消息内容");
                columns.put("partition_no", "分区号");
                columns.put("offset_no", "偏移量");
                columns.put("event_time", "事件时间");
            }
            case ELASTICSEARCH -> {
                columns.put("index_name", "索引名称");
                columns.put("document_id", "文档ID");
                columns.put("document_body", "文档内容");
                columns.put("routing_key", "路由键");
                columns.put("score_value", "评分");
                columns.put("updated_time", "更新时间");
            }
            default -> columns.put("resource_payload", "资源内容");
        }
        return columns;
    }

    private String sampleValue(DatabaseType type, String columnName) {
        return switch (columnName) {
            case "http_method" -> "GET";
            case "status_code" -> "200";
            case "file_type" -> "FILE";
            default -> type.getCode() + "_" + columnName;
        };
    }
}
