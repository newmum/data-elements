package com.linewell.dataelement.metautil.model.enums;

import lombok.Getter;

/**
 * Metadata datasource type registry.
 *
 * <p>The low-code registration page exposes 17 datasource cards. This enum is
 * the Java-side canonical list used by Magic metadata APIs, connection tests and
 * table/field exploration. Aliases are normalized in {@link #fromCode(String)}
 * so legacy values in saved rows keep working.</p>
 */
@Getter
public enum DatabaseType {

    MYSQL("mysql", "MySQL", "com.mysql.cj.jdbc.Driver", 3306),
    ORACLE("oracle", "Oracle", "oracle.jdbc.OracleDriver", 1521),
    OCEANBASE_MYSQL("oceanbasemysql", "OceanBase MySQL", "com.mysql.cj.jdbc.Driver", 2881),
    OCEANBASE_ORACLE("oceanbaseoracle", "OceanBase Oracle", "com.oceanbase.jdbc.Driver", 2881),
    GAUSSDB("gaussdb", "GaussDB", "org.postgresql.Driver", 5432),
    GBASE8A("gbase8a", "GBase 8a", "com.gbase.jdbc.Driver", 5258),
    SQLSERVER("sqlserver", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433),
    HIVE("hive", "Hive", "org.apache.hive.jdbc.HiveDriver", 10000),
    HETU("hetu", "Huawei MRS HetuEngine", "io.trino.jdbc.TrinoDriver", 29861),
    DORIS("doris", "Huawei MRS Doris", "com.mysql.cj.jdbc.Driver", 9030),
    STARROCKS("starrocks", "Huawei MRS StarRocks", "com.mysql.cj.jdbc.Driver", 9030),
    CLICKHOUSE("clickhouse", "Huawei MRS ClickHouse", "com.clickhouse.jdbc.ClickHouseDriver", 8123),
    IOTDB("iotdb", "Huawei MRS IoTDB", "org.apache.iotdb.jdbc.IoTDBDriver", 22260),
    HDFS("hdfs", "Huawei MRS HDFS", null, 8020),
    HBASE("hbase", "Huawei MRS HBase", null, 2181),
    VERTICA("vertica", "Vertica", "com.vertica.jdbc.Driver", 5433),
    DAMENG("dameng", "Dameng", "dm.jdbc.driver.DmDriver", 5236),
    POSTGRESQL("postgresql", "PostgreSQL", "org.postgresql.Driver", 5432),
    DB2("db2", "IBM Db2", "com.ibm.db2.jcc.DB2Driver", 50000),
    MARIADB("mariadb", "MariaDB", "org.mariadb.jdbc.Driver", 3306),
    GBASE8S("gbase8s", "GBase 8s", "com.gbasedbt.jdbc.IfxDriver", 9088),
    OSCAR("oscar", "Oscar", "com.oscar.Driver", 2003),
    HIGHGO("highgo", "HighGo", "com.highgo.jdbc.Driver", 5866),
    KINGBASE("kingbase", "KingBase", "com.kingbase8.Driver", 54321),
    MAXCOMPUTE("maxcompute", "MaxCompute", "com.aliyun.odps.jdbc.OdpsDriver", 443),
    MINIO("minio", "MinIO", null, 9000),
    FTP("ftp", "FTP/SFTP", null, 21),
    API("api", "API", null, 80),
    KAFKA("kafka", "Kafka", null, 9092),
    ELASTICSEARCH("elasticsearch", "Elasticsearch", null, 9200),
    MONGODB("mongodb", "MongoDB", null, 27017);

    private final String code;
    private final String displayName;
    private final String driverClass;
    private final int defaultPort;

    DatabaseType(String code, String displayName, String driverClass, int defaultPort) {
        this.code = code;
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
    }

    /**
     * Resolve current and historical low-code values to a Java enum.
     */
    public static DatabaseType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase();
        normalized = normalized.replace("_", "").replace("-", "");
        normalized = switch (normalized) {
            case "kingbase8" -> "kingbase";
            case "postgres" -> "postgresql";
            // Hailiang / Vastbase and the two TDSQL editions reuse PostgreSQL
            // or MySQL protocol. Keep the actual compatible explorer rather
            // than treating them as unknown source types in source management.
            case "hailiang", "vastbase", "tdsqlpg" -> "postgresql";
            case "tdsqlmysql" -> "mysql";
            // openGauss and GaussDB use the PostgreSQL protocol, but retain the
            // GaussDB metadata explorer and dialect rather than downgrading them.
            case "opengauss", "gauss" -> "gaussdb";
            case "dm" -> "dameng";
            case "oceanbasemysql", "oceanbasecompatiblemysql" -> "oceanbasemysql";
            case "oceanbaseoracle", "oceanbasecompatibleoracle" -> "oceanbaseoracle";
            case "sftp" -> "ftp";
            case "es" -> "elasticsearch";
            case "ch" -> "clickhouse";
            case "trino", "presto", "hetuengine" -> "hetu";
            case "apacheiotdb" -> "iotdb";
            case "mrsdoris" -> "doris";
            case "mrsstarrocks" -> "starrocks";
            case "mrsclickhouse" -> "clickhouse";
            case "mrshive" -> "hive";
            case "mrshdfs" -> "hdfs";
            case "mrshbase" -> "hbase";
            default -> normalized;
        };
        for (DatabaseType type : values()) {
            if (type.getCode().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + code);
    }

    /**
     * Whether the datasource can be explored through JDBC-style table metadata.
     */
    public boolean isRelational() {
        return this != MONGODB
                && this != MINIO
                && this != FTP
                && this != API
                && this != KAFKA
                && this != ELASTICSEARCH
                && this != HDFS
                && this != HBASE;
    }
}
