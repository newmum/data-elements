package com.linewell.dataelement.metautil.explorer.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MinIO metadata explorer.
 *
 * <p>A bucket is a container, not a dataset.  The explorer therefore exposes
 * each supported object in the selected bucket/prefix as a logical table and
 * delegates file schema inference to the shared FTP/structured-file parser.</p>
 */
public class MinioObjectMetadataExplorer extends AbstractMetadataExplorer {

    private final StructuredSourceProbeService structuredProbeService =
            new StructuredSourceProbeService(new ObjectMapper());

    @Override
    public boolean testConnection(DataSourceConfig config) {
        try {
            structuredProbeService.probe(toSource(config), true);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        List<TableInfo> tables = getTables(config);
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("MinIO");
        info.setVersion("object-file-discovery");
        info.setTableCount(tables.size());
        info.setViewCount(0);
        info.setTotalSizeBytes(0L);
        info.setTotalSizeFormatted("0 B");
        info.setSchemas(List.of(config.getDatabase()));
        info.setDatabases(List.of(config.getDatabase()));
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        return List.of(config.getDatabase());
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        return List.of(config.getDatabase());
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        return structuredProbeService.getTablesPage(toSource(config), 1, 500, null).getRows();
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        for (TableInfo table : getTables(config)) {
            if (table.getTableName().equalsIgnoreCase(tableName)) {
                table.setColumns(getColumns(config, tableName));
                table.setIndexes(Collections.emptyList());
                return table;
            }
        }
        throw new IllegalArgumentException("MinIO file table does not exist: " + tableName);
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        return structuredProbeService.getColumns(toSource(config), tableName);
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        return getTableInfo(config, tableName).getRowCount();
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
        return structuredProbeService.sampleData(
                toSource(request.getDataSource()), request.getTableName(), request.getSampleSize());
    }

    private Map<String, Object> toSource(DataSourceConfig config) {
        if (config == null) throw new IllegalArgumentException("MinIO datasource configuration cannot be empty");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("dbType", "minio");
        source.put("minioEndpoint", endpoint(config));
        source.put("minioAccessKey", config.getUsername());
        source.put("minioSecretKey", config.getPassword());
        source.put("minioBucket", config.getDatabase());
        if (config.getSchema() != null && !config.getSchema().isBlank()) {
            source.put("minioPrefix", config.getSchema());
        }
        source.put("minioUseSSL", Boolean.TRUE.equals(config.getSsl()));
        return source;
    }

    private String endpoint(DataSourceConfig config) {
        String host = config.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("MinIO service endpoint cannot be empty");
        if (host.startsWith("http://") || host.startsWith("https://")) return host;
        int port = config.getPortOrDefault();
        return (Boolean.TRUE.equals(config.getSsl()) ? "https://" : "http://") + host + ":" + port;
    }
}
