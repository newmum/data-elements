package com.linewell.dataelement.integration.metadata;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.service.MetadataExplorerService;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 以已登记的连接配置探查物理表，返回用于识别历史名称的连接所有者。 */
@Component
public class TableInventoryProbe {
    private static final String COLLECT_ALL_VISIBLE_OWNERS = "metadataCollectAllVisibleOwners";

    private final JdbcTemplate jdbc;
    private final MetadataExplorerService explorer;
    private final DataSourceConnectionPropertyResolver connections;
    private final HiveModule huaweiMrsHive;

    public TableInventoryProbe(JdbcTemplate jdbc, MetadataExplorerService explorer,
            DataSourceConnectionPropertyResolver connections, HiveModule huaweiMrsHive) {
        this.jdbc = jdbc;
        this.explorer = explorer;
        this.connections = connections;
        this.huaweiMrsHive = huaweiMrsHive;
    }

    public void validate(String datasourceId) {
        loadConfig(TenantContext.requireTenantId(), datasourceId);
    }

    public Inventory scan(String datasourceId) throws Exception {
        var config = loadConfig(TenantContext.requireTenantId(), datasourceId);
        var tables = collectPhysicalTables(config);
        String legacyOwner = alwaysOwnerQualifiedSnapshot(config.getDatabaseType()) ? currentOwner(config) : "";
        return new Inventory(tables == null ? List.of() : tables, legacyOwner,config.getDatabaseType());
    }

    public record Inventory(List<TableInfo> tables, String legacyOwner,DatabaseType databaseType) { }

    private List<TableInfo> collectPhysicalTables(DataSourceConfig config)
            throws Exception {
        if (config.getDatabaseType() == DatabaseType.MONGODB) {
            return explorer.getTableInventory(config);
        }
        if (!isServerManagedHuaweiMrs(config.getConnectorProperties())) {
            if (usesOwnerQualifiedSnapshot(config.getDatabaseType())) {
                return collectVisibleOwnerTables(config);
            }
            return explorer.getTables(config);
        }
        List<TableInfo> tables = new ArrayList<>();
        for (String name : huaweiMrsHive.showTables(config.getDatabase())) {
            if (blank(name)) continue;
            TableInfo table = new TableInfo();
            table.setTableName(name.trim());
            table.setTableType("TABLE");
            table.setSchemaName(config.getDatabase());
            tables.add(table);
        }
        return tables;
    }

    /**
     * Collects every object the registered account can read for the database
     * families that expose objects from several users/schemas.  This method is
     * intentionally used only by the asynchronous collector: opening step 2
     * continues to read the durable local snapshot and never opens a physical
     * database connection.
     */
    private List<TableInfo> collectVisibleOwnerTables(DataSourceConfig config) {
        DatabaseType type = config.getDatabaseType();
        if (type == DatabaseType.ORACLE || type == DatabaseType.OCEANBASE_ORACLE) {
            // Oracle explorers interpret a blank schema as ALL_* metadata for
            // the current account.  A configured default schema must not turn
            // an all-access collection back into a one-owner scan.
            String originalSchema = config.getSchema();
            try {
                config.setSchema(null);
                return qualifyOwnerTables(config, explorer.getTables(config));
            } finally {
                config.setSchema(originalSchema);
            }
        }

        Map<String, Object> originalProperties = config.getConnectorProperties();
        Map<String, Object> collectionProperties = new LinkedHashMap<>();
        if (originalProperties != null) collectionProperties.putAll(originalProperties);
        collectionProperties.put(COLLECT_ALL_VISIBLE_OWNERS, true);
        try {
            config.setConnectorProperties(collectionProperties);
            return qualifyOwnerTables(config, explorer.getTables(config));
        } finally {
            config.setConnectorProperties(originalProperties);
        }
    }

    private boolean usesOwnerQualifiedSnapshot(DatabaseType type) {
        return type == DatabaseType.ORACLE
                || type == DatabaseType.KINGBASE
                || type == DatabaseType.OCEANBASE_MYSQL
                || type == DatabaseType.OCEANBASE_ORACLE;
    }

    /**
     * Persist a qualified table name whenever a visible-owner database can
     * expose more than one user/schema.  Oracle, Kingbase, and OceanBase in
     * Oracle mode intentionally qualify even the registered account's own
     * tables.  That makes the name unambiguous in the snapshot and lets the
     * table search work consistently for every owner.
     */
    private List<TableInfo> qualifyOwnerTables(DataSourceConfig config, List<TableInfo> tables) {
        if (tables == null || tables.isEmpty()) return List.of();
        DatabaseType type = config.getDatabaseType();
        boolean alwaysQualify = alwaysOwnerQualifiedSnapshot(type);
        String currentOwner = currentOwner(config);
        for (TableInfo table : tables) {
            if (table == null || blank(table.getTableName())) continue;
            String tableName = table.getTableName().trim();
            String owner = text(table.getSchemaName());
            boolean oracle=type==DatabaseType.ORACLE||type==DatabaseType.OCEANBASE_ORACLE;
            boolean unqualified=oracle?com.linewell.dataelement.metautil.ddl.DdlDialect.names(tableName).size()==1:tableName.indexOf('.')<0;
            if (!blank(owner) && (alwaysQualify || !owner.equalsIgnoreCase(currentOwner)) && unqualified) {
                String qualifiedOwner=type==DatabaseType.ORACLE||type==DatabaseType.OCEANBASE_ORACLE
                        ?com.linewell.dataelement.metautil.ddl.DdlDialect.oraclePhysicalName(owner):owner;
                table.setTableName(qualifiedOwner + "." + tableName);
            }
        }
        return tables;
    }

    private boolean alwaysOwnerQualifiedSnapshot(DatabaseType type) {
        return type == DatabaseType.ORACLE
                || type == DatabaseType.KINGBASE
                || type == DatabaseType.OCEANBASE_ORACLE;
    }

    private String currentOwner(DataSourceConfig config) {
        if(config.getDatabaseType()==DatabaseType.ORACLE||config.getDatabaseType()==DatabaseType.OCEANBASE_ORACLE) {
            String raw=blank(config.getSchema())?text(config.getUsername()):text(config.getSchema());
            if(!raw.startsWith("\""))raw=raw.split("[@#]",2)[0];
            var name=com.linewell.dataelement.metautil.ddl.DdlDialect.names(raw).getFirst();
            return name.quoted()?name.value():name.value().toUpperCase(java.util.Locale.ROOT);
        }
        if (config.getDatabaseType() == DatabaseType.OCEANBASE_MYSQL) {
            return text(config.getDatabase());
        }
        if (config.getDatabaseType() == DatabaseType.KINGBASE) {
            return blank(config.getSchema()) ? "public" : text(config.getSchema());
        }
        return text(config.getUsername());
    }

    private boolean isServerManagedHuaweiMrs(Map<String, Object> source) {
        String dbType = first(source, "dbType", "db_type");
        if (!"HIVE".equalsIgnoreCase(dbType)) return false;
        String accessMode = first(source, "metadataAccessMode", "metadata_access_mode");
        String connectionMode = first(source, "hiveConnectionMode", "hive_connection_mode");
        if ("open-source".equalsIgnoreCase(connectionMode)) return false;
        return "server-managed-mrs".equalsIgnoreCase(accessMode)
                || "huawei-mrs".equalsIgnoreCase(connectionMode)
                || !blank(first(source, "hiveProfile", "hive_profile"));
    }

    private DataSourceConfig loadConfig(String tenantId, String datasourceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, db_name, db_type, jdbc_url, username, password, pool_cfg
                  from db_datasource_t where tid=? and tenant_id=? and is_del=0
                """, datasourceId, tenantId);
        if (rows.isEmpty()) throw new IllegalArgumentException("数据源不存在或无访问权限");
        Map<String, Object> source = new LinkedHashMap<>(rows.getFirst());
        source.putAll(connections.resolve(tenantId, datasourceId));
        DatabaseType type = DatabaseType.fromCode(first(source, "dbType", "db_type"));
        // FTP/SFTP files are logical tables.  They use the FtpFileMetadataExplorer
        // rather than JDBC. ES uses ordinary indices and bulk mappings through
        // its HTTP explorer. Both reuse the durable step-2 collection workflow.
        // Object files and configured Kafka topics also have real inferred schemas.
        if (type == null || (!type.isRelational() && !Set.of(DatabaseType.FTP, DatabaseType.ELASTICSEARCH,
                DatabaseType.MINIO, DatabaseType.KAFKA, DatabaseType.MONGODB).contains(type))) {
            throw new IllegalArgumentException("当前数据源不支持数据表采集");
        }
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(type);
        // FTP/SFTP keeps its connection values under ftp* keys in pool_cfg.
        // Do not fall back to FTP's protocol default here: doing so changes a
        // saved SFTP port 22 into 21 before FtpFileMetadataExplorer is called.
        boolean ftpSource = type == DatabaseType.FTP;
        config.setHost(ftpSource
                ? first(source, "ftpHost", "host", "dbIp", "hostname")
                : first(source, "host", "dbIp", "hostname"));
        config.setPort(integer(ftpSource
                ? first(source, "ftpPort", "port", "dbPort")
                : first(source, "port", "dbPort"), type.getDefaultPort()));
        // ES 的显示名称不是索引范围；未设置范围时由探查器列出当前连接可见的普通索引。
        config.setDatabase(type == DatabaseType.ELASTICSEARCH
                ? first(source, "database", "indexName", "defaultIndex")
                : first(source, "database", "dbName", "db_name", "dbMetaDbName"));
        config.setUsername(type == DatabaseType.MINIO ? first(source, "minioAccessKey", "username") : ftpSource
                ? first(source, "ftpUsername", "username", "dbUser", "dbMetaUser", "user")
                : first(source, "username", "dbUser", "dbMetaUser", "user"));
        config.setPassword(type == DatabaseType.MINIO ? first(source, "minioSecretKey", "password") : ftpSource
                ? first(source, "ftpPassword", "password", "dbPassword", "dbMetaPassword")
                : first(source, "password", "dbPassword", "dbMetaPassword"));
        config.setJdbcUrl(first(source, "jdbcURL", "jdbcUrl", "jdbc_url"));
        config.setSchema(first(source, "schema", "defaultSchema", "currentSchema"));
        config.setExtraParams(first(source, "extraParams", "connectParams", "jdbcParams"));
        config.setAuthMode(first(source, "authMode", "auth"));
        config.setPrincipal(first(source, "principal", "hivePrincipal", "servicePrincipal"));
        config.setUserPrincipal(first(source, "userPrincipal", "clientPrincipal"));
        config.setKeytabPath(first(source, "keytabPath", "keytab"));
        config.setKrb5ConfPath(first(source, "krb5ConfPath", "krb5Conf"));
        config.setJaasConfPath(first(source, "jaasConfPath", "jaasConfig"));
        config.setZookeeperQuorum(first(source, "zookeeperQuorum", "zkQuorum"));
        config.setZookeeperNamespace(first(source, "zookeeperNamespace", "zooKeeperNamespace"));
        config.setServiceDiscoveryMode(first(source, "serviceDiscoveryMode", "serviceDiscovery"));
        config.setSaslQop(first(source, "saslQop"));
        config.setClientConfigDir(first(source, "clientConfigDir", "mrsClientConfigDir"));
        // Preserve the complete connector profile (protocol, remote path,
        // wildcard, recursion and CSV options).  FTP discovery is not possible
        // with only the denormalized host/port/user fields.
        config.setConnectorProperties(new LinkedHashMap<>(source));
        config.setConnectionTimeout(30);
        return config;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static String first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
