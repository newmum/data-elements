package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Clears selected targets once, before starting any processor for a fresh full load. */
@Service
public class TargetTableCleanupService {
    private static final Logger log = LoggerFactory.getLogger(TargetTableCleanupService.class);
    private final DataSourceConnectionPropertyResolver connections;
    private final HiveModule hive;

    public TargetTableCleanupService(DataSourceConnectionPropertyResolver connections, HiveModule hive) {
        this.connections = connections;
        this.hive = hive;
    }

    public static boolean requested(Pipeline pipeline) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null) return false;
        return pipeline.dsl().nodes().stream().anyMatch(node -> "source".equals(node.category())
                && List.of("FULL", "FULL_THEN_INCR").contains(text(node.config(), "syncMode").toUpperCase(Locale.ROOT))
                && Boolean.parseBoolean(text(node.config(), "deleteTargetData")));
    }

    /** Runs once per explicit start, never per FlowFile (which would erase earlier batches). */
    public void clear(Pipeline pipeline) {
        if (!requested(pipeline)) return;
        // Resolve every target before executing any destructive statement. A mixed
        // pipeline must not silently leave Hive or unsupported destinations uncleared.
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (!"sink".equals(node.category())) continue;
            if (!List.of("sink.jdbc", "sink.hive").contains(node.manifestKey())) {
                throw new IllegalStateException("目标节点不支持启动前清理存量数据：" + node.label());
            }
            Target target = resolve(node);
            targets.putIfAbsent(target.key(), target);
        }
        if (targets.isEmpty()) throw new IllegalStateException("已勾选清理存量数据，但流程没有可清理的目标表");

        List<OpenedTarget> opened = new ArrayList<>();
        try {
            for (Target target : targets.values()) {
                Connection connection = open(target);
                opened.add(new OpenedTarget(target, connection));
                if (target.hive()) validateHiveTable(connection, target.table());
            }
            for (OpenedTarget item : opened) {
                Target target = item.target();
                try (Statement statement = item.connection().createStatement()) {
                    statement.setQueryTimeout(300);
                    statement.execute((target.hive() ? "TRUNCATE TABLE " : "DELETE FROM ") + target.table());
                    if (!target.hive() && !item.connection().getAutoCommit()) item.connection().commit();
                    if (target.hive()) {
                        // A completed command is not enough: do not start the writer
                        // if the table still contains rows or the verification fails.
                        try (ResultSet remaining = statement.executeQuery("SELECT 1 FROM " + target.table() + " LIMIT 1")) {
                            if (remaining.next()) throw new IllegalStateException("Hive 目标表清理后仍有数据：" + target.table());
                        }
                    }
                    log.info("Cleared target {} before full sync of pipeline {}", target.table(), pipeline.id());
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("目标表存量数据清理失败，已中止启动：" + failure.getMessage(), failure);
        } finally {
            for (OpenedTarget item : opened) {
                try { item.connection().close(); }
                catch (Exception failure) { log.warn("Failed to release cleanup connection for {}", item.target().table()); }
            }
        }
    }

    private Target resolve(Pipeline.Node node) {
        Map<String, Object> config = node.config() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(node.config());
        String table = first(config, "table", "tableName", "targetTable");
        boolean hiveTarget = "sink.hive".equals(node.manifestKey()) || "HIVE".equalsIgnoreCase(text(config, "dbType"))
                || first(config, "jdbcUrl", "jdbcURL").startsWith("jdbc:hive2:");
        if (hiveTarget) {
            boolean canvasManaged = managedHive(node, config);
            String canvasUrl = first(config, "jdbcUrl", "jdbcURL");
            String id = first(config, "selectedDatabaseId", "registeredDatasourceId", "targetDbId", "datasourceId", "dataSourceId", "dbId");
            if (!id.isBlank()) {
                Map<String, Object> registered = connections.resolve(TenantContext.requireTenantId(), id);
                if (registered.isEmpty() || !"HIVE".equalsIgnoreCase(text(registered, "dbType")))
                    throw new IllegalStateException("当前租户中不存在所选 Hive 目标库");
                String selectedDatabase = first(registered, "database", "dbName", "dbMetaDbName");
                String canvasDatabase = first(config, "database", "dbName");
                if (!canvasDatabase.isBlank() && !canvasDatabase.equalsIgnoreCase(selectedDatabase))
                    throw new IllegalStateException("Hive 节点数据库与已登记目标库不一致，请重新选择目标库");
                for (String key : List.of("hiveProfile", "hive_profile", "metadataAccessMode", "metadata_access_mode",
                        "hiveConnectionMode", "hive_connection_mode", "jdbcUrl", "jdbcURL", "dbName", "dbMetaDbName",
                        "database", "host", "port", "username", "password")) config.remove(key);
                config.putAll(registered);
                // jdbcURL is the authoritative master-record value returned by the resolver.
                if (!text(registered, "jdbcURL").isBlank()) config.put("jdbcUrl", registered.get("jdbcURL"));
                if (canvasManaged != managedHive(node, config)
                        || (!canvasManaged && !canvasUrl.isBlank() && !canvasUrl.equals(first(config, "jdbcUrl", "jdbcURL"))))
                    throw new IllegalStateException("Hive 节点连接与已登记目标库不一致，请重新选择目标库后部署");
            }
            String profile = first(config, "hiveProfile", "hive_profile");
            boolean managed = managedHive(node, config);
            if ("sink.hive".equals(node.manifestKey()) && !managed)
                throw new IllegalStateException("专用 Hive 节点需要使用服务端 MRS 配置，请重新选择目标库");
            if (managed && !profile.isBlank() && !"default".equalsIgnoreCase(profile))
                throw new IllegalStateException("未配置的 Hive 服务端配置集：" + profile);
            String database = first(config, "database", "dbName", "dbMetaDbName");
            if (database.isBlank()) throw new IllegalStateException("Hive 目标库名不能为空：" + node.label());
            String qualified = hiveTable(database, table);
            String url = first(config, "jdbcUrl", "jdbcURL");
            if (!managed && url.isBlank()) throw new IllegalStateException("Hive 目标库缺少 JDBC 地址：" + node.label());
            return new Target(true, managed, qualified, config,
                    (managed ? "mrs:default" : url + "\n" + text(config, "username")) + "\n" + qualified.toLowerCase(Locale.ROOT));
        }
        String url = first(config, "jdbcUrl", "jdbcURL");
        if (url.isBlank() || text(config, "username").isBlank() || table.isBlank())
            throw new IllegalStateException("清理目标表需要 JDBC 地址、用户名和表名：" + node.label());
        if (!table.matches("[A-Za-z0-9_$]+(\\.[A-Za-z0-9_$]+){0,2}"))
            throw new IllegalStateException("不支持的目标表名：" + table);
        return new Target(false, false, table, config, url + "\n" + text(config, "username") + "\n" + table);
    }

    private Connection open(Target target) throws Exception {
        if (target.managed()) return hive.getConnection();
        Properties properties = new Properties();
        JdbcDriverPropertyResolver.apply(properties, target.config());
        properties.setProperty("user", text(target.config(), "username"));
        properties.setProperty("password", text(target.config(), "password"));
        return DriverManager.getConnection(first(target.config(), "jdbcUrl", "jdbcURL"), properties);
    }

    private boolean managedHive(Pipeline.Node node, Map<String, Object> config) {
        String mode = first(config, "metadataAccessMode", "metadata_access_mode");
        String connectionMode = first(config, "hiveConnectionMode", "hive_connection_mode");
        boolean direct = "jdbc".equalsIgnoreCase(mode) || "open-source".equalsIgnoreCase(connectionMode);
        return !direct && (!first(config, "hiveProfile", "hive_profile").isBlank() || "sink.hive".equals(node.manifestKey())
                || "server-managed-mrs".equalsIgnoreCase(mode) || "huawei-mrs".equalsIgnoreCase(connectionMode));
    }

    private void validateHiveTable(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            try (ResultSet metadata = statement.executeQuery("DESCRIBE FORMATTED " + table)) {
                while (metadata.next()) {
                    String key = metadata.getString(1);
                    if (key == null || !key.trim().replace(":", "").equalsIgnoreCase("Table Type")) continue;
                    if (!"MANAGED_TABLE".equalsIgnoreCase(String.valueOf(metadata.getString(2)).trim())) {
                        throw new IllegalStateException("Hive 目标 " + table + " 不是托管表，不能使用 TRUNCATE 清理；已中止启动，保留表结构和数据");
                    }
                    return;
                }
            }
        }
        throw new IllegalStateException("无法确认 Hive 目标表类型，已中止启动：" + table);
    }

    static String hiveTable(String database, String rawTable) {
        String db = identifier(database);
        String[] parts = rawTable.split("\\.", -1);
        if (parts.length == 2 && identifier(parts[0]).equalsIgnoreCase(db)) return db + "." + identifier(parts[1]);
        if (parts.length != 1) throw new IllegalStateException("Hive 目标表所属数据库与所选库不一致");
        return db + "." + identifier(parts[0]);
    }

    private static String identifier(String value) {
        String plain = value.trim();
        if (plain.startsWith("`") && plain.endsWith("`") && plain.length() > 1) plain = plain.substring(1, plain.length() - 1);
        if (!plain.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("无效的 Hive 数据库或表名：" + value);
        return "`" + plain + "`";
    }
    private static String text(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
    private static String first(Map<String, Object> config, String... keys) {
        for (String key : keys) if (!text(config, key).isBlank()) return text(config, key);
        return "";
    }
    private record Target(boolean hive, boolean managed, String table, Map<String, Object> config, String key) { }
    private record OpenedTarget(Target target, Connection connection) { }
}
