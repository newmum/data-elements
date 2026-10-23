package com.haitong.registry;

import com.haitong.config.RelayProperties;
import com.haitong.core.Json;
import com.haitong.core.RelayException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Validates table batches in an intentionally standalone deployment.  The
 * platform-owned registry remains authoritative whenever a route is marked
 * registration-managed; this component is never a fallback for an unavailable
 * registry because silently changing the authorization source would be unsafe.
 */
@Component
public final class StandalonePushSchema {
    private final RelayProperties properties;
    private final Json json;
    private final StandaloneRegistryStore registry;

    public StandalonePushSchema(RelayProperties properties, Json json, StandaloneRegistryStore registry) {
        this.properties = properties;
        this.json = json;
        this.registry = registry;
    }

    public Map<String, Object> validate(String callerId, byte[] input) {
        Map<String, Object> batch = json.object(input);
        String datasourceId = text(batch.get("datasourceId"));
        String tableName = text(batch.get("tableName"));
        if (!datasourceId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw RelayException.invalid("独立运行模式下 datasourceId 必须是已配置的数据源标识");
        }
        LocalContract contract = databaseContract(datasourceId, tableName, callerId).orElseGet(() -> yamlContract(datasourceId, tableName, callerId));
        String configuredVersion = contract.schemaVersion();
        Object value = batch.get("records");
        if (!(value instanceof List<?> rows)) {
            throw RelayException.invalid("records 必须是 JSON 数组");
        }
        Set<String> required = contract.requiredFields();
        Set<String> allowed = contract.allowedFields();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) {
                throw RelayException.invalid("records 中的每一条记录必须是 JSON 对象");
            }
            for (String field : required) {
                Object fieldValue = item.get(field);
                if (fieldValue == null || fieldValue instanceof String text && text.isBlank()) {
                    throw new RelayException(422, "STANDALONE_REQUIRED_FIELD_MISSING", "独立表契约缺少必填字段：" + field);
                }
            }
            if (!allowed.isEmpty()) {
                for (Object key : item.keySet()) {
                    if (!(key instanceof String field) || !allowed.contains(field)) {
                        throw new RelayException(422, "STANDALONE_FIELD_NOT_ALLOWED", "独立表契约不允许字段：" + key);
                    }
                }
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "standalone");
        metadata.put("datasourceId", datasourceId);
        metadata.put("tableName", tableName);
        if (!configuredVersion.isBlank()) metadata.put("schemaVersion", configuredVersion);
        metadata.put("recordCount", rows.size());
        return Map.copyOf(metadata);
    }

    private Optional<LocalContract> databaseContract(String datasourceId, String tableName, String callerId) {
        if (registry == null) return Optional.empty();
        Optional<StandaloneRegistryStore.Datasource> source = registry.datasource(datasourceId);
        if (source.isEmpty()) return Optional.empty();
        if (source.get().deleted() || !source.get().enabled())
            throw new RelayException(422, "STANDALONE_DATASOURCE_DISABLED", "独立运行模式的数据源已停用");
        Set<String> clients = registry.clients(datasourceId);
        if (!clients.isEmpty() && !clients.contains(callerId))
            throw new RelayException(403, "STANDALONE_DATASOURCE_FORBIDDEN", "当前调用方无此独立数据源的权限");
        StandaloneRegistryStore.Table table = registry.table(datasourceId, tableName)
            .orElseThrow(() -> new RelayException(422, "STANDALONE_TABLE_NOT_CONFIGURED", "独立运行模式未配置此数据表"));
        if (table.deleted() || !table.enabled())
            throw new RelayException(422, "STANDALONE_TABLE_DISABLED", "独立运行模式的数据表已停用");
        List<StandaloneRegistryStore.Column> columns = registry.columns(datasourceId, tableName);
        if (columns.isEmpty())
            throw new RelayException(422, "STANDALONE_TABLE_NOT_CONFIGURED", "独立运行模式的数据表未配置有效字段");
        Set<String> required = new LinkedHashSet<>(); Set<String> allowed = new LinkedHashSet<>();
        for (StandaloneRegistryStore.Column column : columns) { allowed.add(column.columnName()); if (column.required()) required.add(column.columnName()); }
        return Optional.of(new LocalContract(text(table.schemaVersion()), Set.copyOf(required), Set.copyOf(allowed)));
    }

    private LocalContract yamlContract(String datasourceId, String tableName, String callerId) {
        RelayProperties.StandaloneDatasource source = properties.getStandalone().getDatasources().get(datasourceId);
        if (source == null) throw new RelayException(422, "STANDALONE_DATASOURCE_NOT_CONFIGURED", "独立运行模式未配置此 datasourceId");
        List<String> clients = source.getClients() == null ? List.of() : source.getClients();
        if (!clients.isEmpty() && !clients.contains(callerId))
            throw new RelayException(403, "STANDALONE_DATASOURCE_FORBIDDEN", "当前调用方无此独立数据源的权限");
        RelayProperties.StandaloneTable table = source.getTables().get(tableName);
        if (table == null) throw new RelayException(422, "STANDALONE_TABLE_NOT_CONFIGURED", "独立运行模式未配置此数据表");
        return new LocalContract(text(table.getSchemaVersion()), names(table.getRequiredFields(), "required-fields"), names(table.getAllowedFields(), "allowed-fields"));
    }

    private Set<String> names(List<String> input, String label) {
        if (input == null || input.isEmpty()) return Set.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : input) {
            String name = text(value);
            if (name.isBlank() || name.length() > 200 || name.chars().anyMatch(c -> c < 33 || c == 127)) {
                throw new IllegalStateException("独立表契约 " + label + " 包含无效字段名");
            }
            if (!values.add(name)) throw new IllegalStateException("独立表契约 " + label + " 包含重复字段名");
        }
        return Set.copyOf(values);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
    private record LocalContract(String schemaVersion, Set<String> requiredFields, Set<String> allowedFields) { }
}
