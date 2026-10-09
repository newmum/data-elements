package com.linewell.dataelement.dataassets.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final DataScopeAuthorizationService scopes;

    public DataSourceConnectionPropertyResolver(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public DataSourceConnectionPropertyResolver(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                                DataScopeAuthorizationService scopes) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.scopes = scopes;
    }

    /** One tenant-scoped read, one role-scope decision and an explicit check of every ID. */
    public Map<String, Map<String, Object>> resolveBatch(String tenantId, Collection<String> datasourceIds, String resource) {
        if (blank(tenantId)) throw new IllegalStateException("缺少当前租户上下文");
        var ids = new LinkedHashSet<>(datasourceIds == null ? List.<String>of() : datasourceIds);
        ids.removeIf(this::blank);
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 200) throw new IllegalArgumentException("单次最多解析200个字典数据源");
        var parameters = new java.util.ArrayList<Object>(ids);
        parameters.add(tenantId);
        String sql = "SELECT * FROM db_datasource_t WHERE tid IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ") AND tenant_id = ? AND is_del = 0";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters.toArray());
        if (scopes == null) throw new IllegalStateException("字典数据源权限校验服务未配置");
        rows = scopes.visibleDataSources(resource, rows);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) result.put(String.valueOf(row.get("tid")), connectionValues(row));
        for (String id : ids) {
            if (!result.containsKey(id)) throw new IllegalStateException("字典数据源不存在或无访问权限");
        }
        return result;
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
        return connectionValues(datasource);
    }

    private Map<String, Object> connectionValues(Map<String, Object> datasource) {
        Map<String, Object> values = readPoolConfig(datasource.get("pool_cfg"), String.valueOf(datasource.get("tid")));
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
