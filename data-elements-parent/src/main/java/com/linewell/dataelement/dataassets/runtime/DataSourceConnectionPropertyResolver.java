package com.linewell.dataelement.dataassets.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads a datasource connection from its authoritative {@code db_datasource_t} record.
 *
 * <p>Basic connection columns remain denormalized on the master record and the complete
 * extension set is stored as one JSON object in {@code pool_cfg}. Connection settings must
 * never be reconstructed from {@code da_prop_t}; that table is for ordinary asset properties,
 * not datasource credentials or protocol configuration.</p>
 */
@Component
public class DataSourceConnectionPropertyResolver {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataSourceConnectionPropertyResolver(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Returns a normalized connection map for one datasource in the active tenant database. */
    public Map<String, Object> resolve(String tenantId, String datasourceId) {
        if (blank(datasourceId)) {
            return new LinkedHashMap<>();
        }
        String sql = blank(tenantId)
                ? "SELECT * FROM db_datasource_t WHERE tid = ? AND is_del = 0"
                : "SELECT * FROM db_datasource_t WHERE tid = ? AND tenant_id = ? AND is_del = 0";
        List<Map<String, Object>> rows = blank(tenantId)
                ? jdbcTemplate.queryForList(sql, datasourceId)
                : jdbcTemplate.queryForList(sql, datasourceId, tenantId);
        if (rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> datasource = rows.getFirst();
        Map<String, Object> values = readPoolConfig(datasource.get("pool_cfg"), datasourceId);
        copyMasterValue(values, "dbType", datasource.get("db_type"));
        copyMasterValue(values, "jdbcURL", datasource.get("jdbc_url"));
        copyMasterValue(values, "username", datasource.get("username"));
        copyMasterValue(values, "password", datasource.get("password"));
        copyMasterValue(values, "dbVersion", datasource.get("db_version"));
        copyMasterValue(values, "nodeId", datasource.get("node_id"));
        copyMasterValue(values, "storageDomain", datasource.get("storage_domain"));
        copyMasterValue(values, "showConnect", datasource.get("show_connect"));
        copyMasterValue(values, "connectionStatus", datasource.get("connection_status"));
        return values;
    }

    private Map<String, Object> readPoolConfig(Object source, String datasourceId) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (source == null || source.toString().isBlank()) {
            return values;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    source.toString(), new TypeReference<LinkedHashMap<String, Object>>() { });
            if (parsed != null) {
                values.putAll(parsed);
            }
            return values;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "数据源 " + datasourceId + " 的连接配置 pool_cfg 不是有效 JSON 对象", exception);
        }
    }

    private void copyMasterValue(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
