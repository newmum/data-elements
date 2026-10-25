package com.linewell.dataelement.metautil.explorer.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import java.util.*;

/** Exposes configured, sampled Kafka topics through the ordinary metadata contract. */
public class KafkaTopicMetadataExplorer extends AbstractMetadataExplorer {
    private final StructuredSourceProbeService probe = new StructuredSourceProbeService(new ObjectMapper());

    private Map<String,Object> source(DataSourceConfig config) {
        var source = new LinkedHashMap<String,Object>();
        if (config.getConnectorProperties() != null) source.putAll(config.getConnectorProperties());
        source.put("dbType", "kafka");
        return source;
    }
    @Override public boolean testConnection(DataSourceConfig config) {
        try { probe.probe(source(config), true); return true; } catch (RuntimeException error) { return false; }
    }
    @Override public List<TableInfo> getTables(DataSourceConfig config) {
        return probe.getTablesPage(source(config), 1, 100, null).getRows();
    }
    @Override public TableInfo getTableInfo(DataSourceConfig config, String name) {
        TableInfo table = getTables(config).stream().filter(item -> item.getTableName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Kafka topic not in configured scope"));
        table.setColumns(getColumns(config, name)); table.setIndexes(List.of()); return table;
    }
    @Override public List<ColumnInfo> getColumns(DataSourceConfig config, String name) { return probe.getColumns(source(config), name); }
    @Override public SampleDataResult sampleData(SampleRequest request) {
        return probe.sampleData(source(request.getDataSource()), request.getTableName(), request.getSampleSize());
    }
    @Override public List<IndexInfo> getIndexes(DataSourceConfig config, String name) { return List.of(); }
    @Override public Long getTableRowCount(DataSourceConfig config, String name) { return getTableInfo(config, name).getRowCount(); }
    @Override public Long getTableSize(DataSourceConfig config, String name) { return 0L; }
    @Override public Long getDatabaseSize(DataSourceConfig config) { return 0L; }
    @Override public List<String> getDatabases(DataSourceConfig config) { return List.of(config.getDatabase()); }
    @Override public List<String> getSchemas(DataSourceConfig config) { return getDatabases(config); }
    @Override public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        var info = new DatabaseInfo(); info.setDatabaseName(config.getDatabase()); info.setDatabaseType("Kafka");
        info.setTableCount(getTables(config).size()); info.setVersion("topic-sampling"); return info;
    }
}
