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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FTP/SFTP metadata explorer backed by the same structured-file probe used by
 * registration step 1.
 *
 * <p>Each matched remote file is a logical table.  Returning a synthetic FTP
 * resource table here would make re-exploration compare a different table set
 * from the set that was initially registered, and would falsely mark existing
 * files as deleted.</p>
 */
public class FtpFileMetadataExplorer extends AbstractMetadataExplorer {

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
        info.setDatabaseType("FTP/SFTP");
        info.setVersion("structured-file-discovery");
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
        throw new IllegalArgumentException("FTP file table does not exist: " + tableName);
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
        if (config == null) throw new IllegalArgumentException("FTP datasource configuration cannot be empty");

        Map<String, Object> source = new LinkedHashMap<>();
        if (config.getConnectorProperties() != null) {
            source.putAll(config.getConnectorProperties());
        }
        source.put("dbType", "ftp");
        putIfPresent(source, "ftpHost", config.getHost());
        if (config.getPort() != null) source.put("ftpPort", config.getPort());
        putIfPresent(source, "ftpUsername", config.getUsername());
        putIfPresent(source, "ftpPassword", config.getPassword());
        return source;
    }

    private void putIfPresent(Map<String, Object> source, String key, String value) {
        if (value != null && !value.isBlank()) source.put(key, value);
    }
}
