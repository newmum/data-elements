package com.linewell.dataelement.metautil.jdbc;

import java.util.Locale;
import java.util.Map;

/** Product identity is persisted independently from the GaussDB catalogue type. */
public enum GaussDbVariant {
    GAUSSDB("gaussdb", "com.huawei.opengauss.jdbc.Driver", "jdbc:opengauss:", "opengaussjdbc.jar", 8000),
    GAUSSDB_JDBC("gaussdb-jdbc", "com.huawei.gaussdb.jdbc.Driver", "jdbc:gaussdb:", "gaussdbjdbc.jar", 8000),
    OPENGAUSS("opengauss", "org.opengauss.Driver", "jdbc:opengauss:", "opengauss-jdbc.jar", 5432),
    DWS("dws", "org.postgresql.Driver", "jdbc:postgresql:", "postgresql-42.6.0.jar", 8000),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql:", "mysql-connector-j-8.4.0.jar", 3306),
    LEGACY_POSTGRESQL("legacy-postgresql", "org.postgresql.Driver", "jdbc:postgresql:", "postgresql-42.6.0.jar", 5432);

    private final String code;
    private final String driverClass;
    private final String urlPrefix;
    private final String driverFile;
    private final int defaultPort;

    GaussDbVariant(String code, String driverClass, String urlPrefix, String driverFile, int defaultPort) {
        this.code=code; this.driverClass=driverClass; this.urlPrefix=urlPrefix;
        this.driverFile=driverFile; this.defaultPort=defaultPort;
    }
    public String code() { return code; }
    public String driverClass() { return driverClass; }
    public String urlPrefix() { return urlPrefix; }
    public String driverFile() { return driverFile; }
    public int defaultPort() { return defaultPort; }
    public String nodeDriverKey() {
        return switch(this) {
            case GAUSSDB -> "GAUSSDB_NATIVE";
            case GAUSSDB_JDBC -> "GAUSSDB_JDBC";
            case OPENGAUSS -> "GAUSSDB_OPENGAUSS";
            case DWS -> "GAUSSDB_DWS";
            case MYSQL -> "GAUSSDB_MYSQL";
            case LEGACY_POSTGRESQL -> "GAUSSDB";
        };
    }

    public static GaussDbVariant resolve(String raw) {
        if (raw == null || raw.isBlank()) return LEGACY_POSTGRESQL;
        return switch(raw.trim().toLowerCase(Locale.ROOT).replace('_','-')) {
            case "gaussdb", "native", "huawei", "gaussdb-native", "centralized", "distributed" -> GAUSSDB;
            case "gaussdb-jdbc", "gaussdbjdbc" -> GAUSSDB_JDBC;
            case "opengauss", "open-gauss" -> OPENGAUSS;
            case "dws", "gaussdb-dws", "gaussdb(dws)" -> DWS;
            case "mysql", "gaussdb-mysql", "gaussdb-for-mysql", "taurus", "m-compatible" -> MYSQL;
            case "legacy-postgresql", "postgresql", "legacy" -> LEGACY_POSTGRESQL;
            default -> throw new IllegalArgumentException("未知 GaussDB 产品类型，请重新选择: " + raw);
        };
    }

    public static GaussDbVariant from(Map<String, ?> config) {
        Object value=config.get("gaussdbVariant");
        if (value == null) value=config.get("gaussdb_variant");
        if (value == null) {
            Object rawType=config.get("dbType");
            if (rawType != null && "opengauss".equalsIgnoreCase(rawType.toString())) return OPENGAUSS;
        }
        return resolve(value == null ? null : value.toString());
    }

    public String validateUrl(String url) {
        if (url != null && !url.isBlank() && !url.startsWith(urlPrefix))
            throw new IllegalArgumentException("GaussDB 产品类型与 JDBC 协议不一致，请重新生成连接地址");
        return url;
    }
}
