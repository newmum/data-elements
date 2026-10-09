package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Locale;
import java.util.Map;

/** Shared registration-to-canvas mapping. Never guess MySQL for an unknown type. */
public final class RegisteredDatasourceType {
    private RegisteredDatasourceType() { }

    private static String key(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("数据源尚未登记类型，请先完善数据源登记");
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    public static DatabaseType databaseType(String raw, Map<String, Object> config) {
        String value = key(raw);
        value = switch (value) {
            case "oracle12", "oracle12+" -> "oracle";
            case "mssql", "mssql2012+", "mssql2008" -> "sqlserver";
            case "oceanbase" -> "ORACLE".equalsIgnoreCase(first(config, "compatibleMode", "compatible_mode"))
                    ? "oceanbaseoracle" : "oceanbasemysql";
            default -> value;
        };
        return DatabaseType.fromCode(value);
    }

    public static String sourceManifest(String raw, Map<String, Object> config) {
        String value = key(raw);
        // Protocol compatibility must not erase the datasource's canvas identity.
        if ("tdsqlmysql".equals(value)) return "source.tdsql-mysql";
        if ("tdsqlpg".equals(value)) return "source.tdsql-pg";
        if ("hailiang".equals(value) || "vastbase".equals(value)) return "source.hailiang";
        DatabaseType type = databaseType(raw, config);
        return switch (type) {
            case OCEANBASE_MYSQL, OCEANBASE_ORACLE -> "source.oceanbase";
            case FTP -> "sftp".equals(value)
                    || "sftp".equalsIgnoreCase(first(config, "ftpProtocol", "ftp_protocol", "protocol"))
                    ? "source.sftp" : "source.ftp";
            case VERTICA, MAXCOMPUTE, MONGODB -> throw new IllegalArgumentException(
                    "当前画布尚未提供该数据源的来源组件: " + type.getDisplayName());
            default -> "source." + type.getCode();
        };
    }

    /** Preserve vendor identity in the persisted config; protocol reuse is done by the compiler. */
    public static String configType(String raw, Map<String, Object> config) {
        return databaseType(raw, config).name();
    }

    public static String sinkType(String raw, Map<String, Object> config) {
        DatabaseType type = databaseType(raw, config);
        if (!type.isRelational() || type == DatabaseType.VERTICA || type == DatabaseType.MAXCOMPUTE) {
            throw new IllegalArgumentException("物化建表不支持该目标数据源类型: " + type.getDisplayName());
        }
        return switch (type) {
            case MYSQL -> "MySQL";
            case ORACLE -> "Oracle";
            case POSTGRESQL -> "PostgreSQL";
            case DAMENG -> "DM";
            default -> type.name();
        };
    }

    /** Reuse registered endpoints; construct missing relational URLs using the metadata connector rules. */
    public static String jdbcUrl(String raw, Map<String, Object> config, Map<String, Object> row) {
        DatabaseType type = databaseType(raw, config);
        if (!type.isRelational()) return null;
        String explicit = first(config, "jdbcURL", "jdbcUrl", "jdbc_url");
        if (explicit == null) explicit = first(row, "jdbcURL", "jdbcUrl", "jdbc_url");
        if (explicit != null) return explicit;
        String legacyUrl = first(config, "url");
        if (legacyUrl != null && legacyUrl.startsWith("jdbc:")) return legacyUrl;

        DataSourceConfig connection = new DataSourceConfig();
        connection.setDatabaseType(type);
        connection.setHost(or(first(config, "host", "dbMetaIp", "hostname"), first(row, "host", "db_meta_ip"), "127.0.0.1"));
        String port = or(first(config, "port", "dbMetaPort"), first(row, "port", "db_meta_port"));
        if (port != null) connection.setPort(Integer.valueOf(port));
        connection.setDatabase(or(first(config, "database", "serviceName", "sid", "dbName", "dbMetaDbName"),
                first(row, "database", "dbMetaDbName"), ""));
        connection.setSchema(first(config, "defaultSchema", "schema", "currentSchema", "schemaName"));
        connection.setExtraParams(first(config, "extraParams", "extra_params"));
        connection.setAuthMode(first(config, "authMode"));
        connection.setPrincipal(first(config, "principal"));
        connection.setUserPrincipal(first(config, "userPrincipal"));
        connection.setKeytabPath(first(config, "keytabPath"));
        connection.setZookeeperQuorum(first(config, "zookeeperQuorum"));
        connection.setZookeeperNamespace(first(config, "zookeeperNamespace"));
        connection.setServiceDiscoveryMode(first(config, "serviceDiscoveryMode"));
        connection.setSaslQop(first(config, "saslQop"));
        if (type == DatabaseType.ORACLE
                && !"SID".equalsIgnoreCase(first(config, "connectionType", "jdbcType"))) {
            return "jdbc:oracle:thin:@//" + connection.getHost() + ":" + connection.getPortOrDefault()
                    + "/" + connection.getDatabase();
        }
        return connection.buildJdbcUrl();
    }

    private static String first(Map<String, Object> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return null;
    }

    private static String or(String... values) {
        for (String value : values) if (value != null) return value;
        return null;
    }
}
