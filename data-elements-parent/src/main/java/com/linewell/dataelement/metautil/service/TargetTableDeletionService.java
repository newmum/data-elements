package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * Deletes a registered target table with a database-specific, verifiable contract.
 *
 * <p>The table-reset Magic API previously issued a string-concatenated {@code DROP TABLE}
 * after a page-limited table lookup.  That is particularly unsafe for OceanBase Oracle:
 * a raw {@code oceanbase} datasource can otherwise fall back to the MySQL explorer,
 * while an owner-qualified Oracle name is not comparable with a table-only result.
 * This service resolves the physical object from the selected datasource scope, executes
 * a dialect-correct statement, and verifies that the object is no longer discoverable.
 * Callers must perform lifecycle metadata cleanup only after {@link #drop} returns.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetTableDeletionService {

    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_$#]*";
    private static final String HIVE_IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";

    private final MetadataExplorerService metadataExplorerService;
    private final HiveModule huaweiMrsHive;

    /**
     * Resolves the exact physical target before a destructive operation.  This is read-only
     * and is intended to run before any task, pipeline or metadata record is changed.
     */
    public Map<String, Object> preflight(DataSourceConfig source, String requestedTableName) {
        ResolvedTarget target = resolve(source, requestedTableName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("physicalExists", target.exists());
        result.put("targetTableName", target.tableName());
        result.put("targetScope", target.scope());
        result.put("databaseType", target.config().getDatabaseType().getCode());
        return result;
    }

    /**
     * Checks a bounded set of tables in one registered datasource. The physical
     * table directory is fetched once, then every requested name is matched in
     * memory. Historical materialization rows may carry an obsolete qualifier;
     * these are searched only inside the datasource's configured scope.
     */
    public List<Map<String, Object>> preflightBatch(DataSourceConfig source, List<String> requestedNames) {
        if (requestedNames == null || requestedNames.isEmpty() || requestedNames.size() > 20) {
            throw new IllegalArgumentException("一次最多预检20个目标表");
        }
        DataSourceConfig config = scopedCopy(source);
        String scope = configuredScope(config);
        List<Name> parsed = new ArrayList<>(requestedNames.size());
        List<Boolean> normalized = new ArrayList<>(requestedNames.size());
        for (String requestedName : requestedNames) {
            Name name = parseName(requestedName);
            boolean historical = name.scope() != null && !name.scope().equalsIgnoreCase(scope);
            parsed.add(name);
            normalized.add(historical);
        }

        Map<String, List<TableInfo>> physicalByName = new LinkedHashMap<>();
        for (TableInfo candidate : physicalTables(config)) {
            if (candidate == null || blank(candidate.getTableName())) continue;
            if (!blank(candidate.getSchemaName()) && !candidate.getSchemaName().equalsIgnoreCase(scope)) continue;
            physicalByName.computeIfAbsent(candidate.getTableName().toUpperCase(Locale.ROOT),
                    ignored -> new ArrayList<>()).add(candidate);
        }

        List<Map<String, Object>> results = new ArrayList<>(parsed.size());
        for (int index = 0; index < parsed.size(); index++) {
            Name name = parsed.get(index);
            List<TableInfo> matches = physicalByName.getOrDefault(
                    name.table().toUpperCase(Locale.ROOT), List.of());
            if (matches.size() > 1) {
                throw new IllegalStateException("目标数据源中存在同名表，无法确认唯一物理表：" + name.table());
            }
            String physicalName = matches.isEmpty() ? name.table() : matches.getFirst().getTableName();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("physicalExists", !matches.isEmpty());
            result.put("targetTableName", physicalName);
            result.put("physicalTargetTableName", physicalName);
            result.put("targetScope", scope);
            result.put("databaseType", config.getDatabaseType().getCode());
            result.put("historicalScopeNormalized", normalized.get(index));
            results.add(result);
        }
        return results;
    }

    /**
     * Drops the resolved table and confirms that metadata discovery no longer finds it.
     * A missing physical table is a successful no-op: its stale platform metadata can then
     * be cleaned up safely.  Any failed execution or failed post-check throws, leaving the
     * caller's task/pipeline metadata untouched.
     */
    public Map<String, Object> drop(DataSourceConfig source, String requestedTableName) {
        ResolvedTarget before = resolve(source, requestedTableName);
        if (!before.exists()) {
            return result(before, false, false);
        }

        DatabaseType type = before.config().getDatabaseType();
        String ddl = type == DatabaseType.HIVE
                ? "DROP TABLE IF EXISTS " + quoteIdentifier(type, before.scope()) + "."
                        + quoteIdentifier(type, before.tableName())
                : "DROP TABLE " + quoteIdentifier(type, before.tableName());
        try {
            if (serverManagedHive(before.config())) {
                huaweiMrsHive.execDDLSqlInDatabase(before.scope(), ddl);
            } else {
                metadataExplorerService.createTable(before.config(), ddl);
            }
        } catch (Exception error) {
            if (isMissingPhysicalObject(error)) {
                log.info("Target table disappeared before deletion: type={}, scope={}, table={}",
                        before.config().getDatabaseType(), before.scope(), before.tableName());
                return result(before, false, false);
            }
            throw new IllegalStateException("删除目标物理表失败，已保留接入流程和元数据："
                    + safeMessage(error), error);
        }

        ResolvedTarget after = resolve(before.config(), requestedTableName);
        if (after.exists()) {
            throw new IllegalStateException("目标物理表删除后校验仍存在，已保留接入流程和元数据："
                    + before.scope() + "." + before.tableName());
        }
        log.info("Target table dropped and verified: type={}, scope={}, table={}",
                before.config().getDatabaseType(), before.scope(), before.tableName());
        return result(before, true, true);
    }

    private Map<String, Object> result(ResolvedTarget target, boolean existed, boolean deleted) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("physicalExists", existed);
        result.put("physicalDeleted", deleted);
        result.put("targetTableName", target.tableName());
        result.put("targetScope", target.scope());
        result.put("databaseType", target.config().getDatabaseType().getCode());
        return result;
    }

    private ResolvedTarget resolve(DataSourceConfig original, String requestedTableName) {
        DataSourceConfig config = scopedCopy(original);
        Name requested = parseName(requestedTableName);
        String scope = configuredScope(config);
        if (requested.scope() != null && !requested.scope().equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("目标表所属数据库或 Schema 与所选目标数据源不一致：" + scope);
        }

        List<TableInfo> matches = new ArrayList<>();
        for (TableInfo candidate : physicalTables(config)) {
            if (candidate == null || candidate.getTableName() == null
                    || !candidate.getTableName().equalsIgnoreCase(requested.table())) {
                continue;
            }
            if (candidate.getSchemaName() != null && !candidate.getSchemaName().isBlank()
                    && !candidate.getSchemaName().equalsIgnoreCase(scope)) {
                continue;
            }
            matches.add(candidate);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("目标数据源中存在同名表，无法确认唯一物理表：" + requested.table());
        }
        String physicalName = matches.isEmpty() ? requested.table() : matches.getFirst().getTableName();
        return new ResolvedTarget(config, scope, physicalName, !matches.isEmpty());
    }

    private DataSourceConfig scopedCopy(DataSourceConfig source) {
        if (source == null || source.getDatabaseType() == null) {
            throw new IllegalArgumentException("目标数据源缺少数据库类型，无法删除目标表");
        }
        boolean managedHive = serverManagedHive(source);
        if (blank(source.getDatabase())
                || (!managedHive && (blank(source.getHost()) || blank(source.getUsername())))) {
            throw new IllegalArgumentException("目标数据源连接信息不完整，无法删除目标表");
        }
        DataSourceConfig copy = new DataSourceConfig();
        BeanUtils.copyProperties(source, copy);
        copy.setHost(blank(source.getHost()) ? null : source.getHost().trim());
        copy.setDatabase(source.getDatabase().trim());
        copy.setSchema(blank(source.getSchema()) ? null : source.getSchema().trim());
        copy.setUsername(blank(source.getUsername()) ? null : source.getUsername().trim());
        if (copy.getDatabaseType() == DatabaseType.HIVE) {
            if (!copy.getDatabase().matches(HIVE_IDENTIFIER)) {
                throw new IllegalArgumentException("Hive 目标数据库名不合法");
            }
            // Hive's scope is its registered database, not a JDBC schema/metadata database.
            copy.setSchema(null);
        }
        if (managedHive) {
            String profile = connectorValue(copy, "hiveProfile", "hive_profile");
            if (!blank(profile) && !"default".equalsIgnoreCase(profile)) {
                throw new IllegalArgumentException("未配置的华为 MRS Hive 配置集: " + profile);
            }
        }

        // Oracle-compatible explorers intentionally support broad ALL_* discovery when
        // no schema is supplied.  Destructive work must instead be confined to the
        // connection owner's schema, matching the create-target-table contract.
        if (oracleLike(copy.getDatabaseType()) && blank(copy.getSchema())) {
            copy.setSchema(ownerFromUsername(copy.getUsername()));
        }
        return copy;
    }

    private List<TableInfo> physicalTables(DataSourceConfig config) {
        if (!serverManagedHive(config)) {
            return metadataExplorerService.getTables(config);
        }
        try {
            List<TableInfo> tables = new ArrayList<>();
            // SHOW TABLES reads the complete registered database, without a page limit
            // or a direct Hive Metastore connection. The pool owns MRS authentication.
            for (String name : huaweiMrsHive.showTables(config.getDatabase())) {
                if (blank(name)) continue;
                TableInfo table = new TableInfo();
                table.setTableName(name.trim());
                table.setSchemaName(config.getDatabase());
                tables.add(table);
            }
            return tables;
        } catch (Exception error) {
            throw new IllegalStateException("华为 Hive 目标表探查失败：" + safeMessage(error), error);
        }
    }

    private boolean serverManagedHive(DataSourceConfig config) {
        if (config.getDatabaseType() != DatabaseType.HIVE) return false;
        String accessMode = connectorValue(config, "metadataAccessMode", "metadata_access_mode");
        String connectionMode = connectorValue(config, "hiveConnectionMode", "hive_connection_mode");
        if ("jdbc".equalsIgnoreCase(accessMode) || "open-source".equalsIgnoreCase(connectionMode)) {
            return false;
        }
        return "server-managed-mrs".equalsIgnoreCase(accessMode)
                || "huawei-mrs".equalsIgnoreCase(connectionMode)
                || !blank(connectorValue(config, "hiveProfile", "hive_profile"));
    }

    private String connectorValue(DataSourceConfig config, String... keys) {
        Map<String, Object> properties = config.getConnectorProperties();
        if (properties != null) {
            for (String key : keys) {
                Object value = properties.get(key);
                if (value != null && !value.toString().isBlank()) return value.toString().trim();
            }
        }
        return null;
    }

    private String configuredScope(DataSourceConfig config) {
        if (!blank(config.getSchema())) {
            return unquote(config.getSchema());
        }
        if (oracleLike(config.getDatabaseType())) {
            return ownerFromUsername(config.getUsername());
        }
        return config.getDatabase().trim();
    }

    private Name parseName(String value) {
        String raw = value == null ? "" : value.trim();
        String[] parts = raw.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2 || !parts[parts.length - 1].matches(IDENTIFIER)
                || (parts.length == 2 && !parts[0].matches(IDENTIFIER))) {
            throw new IllegalArgumentException("目标表名不合法，只允许 Schema.表名或表名：" + raw);
        }
        return new Name(parts.length == 2 ? parts[0] : null, parts[parts.length - 1]);
    }

    private String quoteIdentifier(DatabaseType type, String identifier) {
        if (!identifier.matches(type == DatabaseType.HIVE ? HIVE_IDENTIFIER : IDENTIFIER)) {
            throw new IllegalArgumentException("目标物理表名不合法：" + identifier);
        }
        if (type == DatabaseType.MYSQL || type == DatabaseType.OCEANBASE_MYSQL || type == DatabaseType.HIVE) {
            return "`" + identifier + "`";
        }
        if (oracleLike(type) || type == DatabaseType.POSTGRESQL || type == DatabaseType.GAUSSDB
                || type == DatabaseType.KINGBASE || type == DatabaseType.HIGHGO || type == DatabaseType.VERTICA) {
            return "\"" + identifier + "\"";
        }
        return identifier;
    }

    private boolean oracleLike(DatabaseType type) {
        return type == DatabaseType.ORACLE || type == DatabaseType.OCEANBASE_ORACLE
                || type == DatabaseType.DAMENG;
    }

    private String ownerFromUsername(String username) {
        String owner = username == null ? "" : username.trim().split("[@#]", 2)[0];
        if (!owner.matches(IDENTIFIER)) {
            throw new IllegalArgumentException("Oracle 兼容目标库缺少合法 Schema 或用户名");
        }
        return owner.toUpperCase(Locale.ROOT);
    }

    private String unquote(String value) {
        String plain = value == null ? "" : value.trim();
        if ((plain.startsWith("\"") && plain.endsWith("\""))
                || (plain.startsWith("`") && plain.endsWith("`"))) {
            plain = plain.substring(1, plain.length() - 1);
        }
        if (!plain.matches(IDENTIFIER)) {
            throw new IllegalArgumentException("目标数据源 Schema 不合法：" + value);
        }
        return plain;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return blank(message) ? error.getClass().getSimpleName() : message;
    }

    /**
     * A target may be removed between preflight and DROP.  Oracle-compatible
     * databases (including Dameng) report that race with dialect-specific
     * messages, but the intended cleanup is still idempotent.
     */
    private boolean isMissingPhysicalObject(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = safeMessage(current).toLowerCase(Locale.ROOT);
            if (message.contains("ora-00942")
                    || message.contains("table or view does not exist")
                    || message.contains("object does not exist")
                    || message.contains("表不存在")
                    || message.contains("对象不存在")
                    || message.contains("无效的表或视图名")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Name(String scope, String table) { }

    private record ResolvedTarget(DataSourceConfig config, String scope, String tableName, boolean exists) { }
}
