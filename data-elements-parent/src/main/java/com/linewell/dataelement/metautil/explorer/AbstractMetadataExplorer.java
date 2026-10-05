package com.linewell.dataelement.metautil.explorer;

import com.linewell.dataelement.metautil.exception.DataSourceConnectionException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.SqlValidationResult;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 抽象元数据探查器基类
 * 提供通用的JDBC操作和工具方法；基于 HikariCP 连接池缓存，同一数据源复用连接池，避免连接耗尽。
 *
 * @author MetaUtil
 */
@Slf4j
public abstract class AbstractMetadataExplorer implements MetadataExplorer {

    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30;
    private static final String DEFAULT_MYSQL_PARAMS = "useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final Pattern KINGBASE_LITERAL_CAST_DEFAULT = Pattern.compile(
            "^('(?:''|[^'])*'|[+-]?\\d+(?:\\.\\d+)?|TRUE|FALSE|NULL)\\s*::\\s*"
                    + "[A-Za-z_][A-Za-z_0-9]*(?:\\s+[A-Za-z_][A-Za-z_0-9]*)*"
                    + "(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * 达梦内置模式不允许承载业务表。物化建表使用管理员账号时，如果没有明确
     * 配置业务 Schema，极易因默认模式落到这些系统模式而导致 DDL 失败。
     */
    private static final Set<String> DAMENG_SYSTEM_SCHEMAS = Set.of(
            "SYS", "SYSTEM", "SYSDBA", "SYSAUDITOR", "SYSSSO", "SYSJOB", "SYSDATE",
            "SYSCONSOLE", "SYSDBO", "CTISYS", "DBA", "INFORMATION_SCHEMA"
    );

    /**
     * 仅对 JDBC 类型使用连接池（MongoDB 等非 JDBC 不经过此类）
     */
    private static final Set<DatabaseType> JDBC_TYPES = EnumSet.of(
            DatabaseType.MYSQL, DatabaseType.ORACLE, DatabaseType.POSTGRESQL, DatabaseType.SQLSERVER,
            DatabaseType.GBASE8A, DatabaseType.VERTICA, DatabaseType.KINGBASE,
            DatabaseType.HIVE, DatabaseType.OCEANBASE_MYSQL, DatabaseType.OCEANBASE_ORACLE,
            DatabaseType.DAMENG, DatabaseType.GAUSSDB, DatabaseType.MAXCOMPUTE,
            DatabaseType.HETU, DatabaseType.DORIS, DatabaseType.STARROCKS,
            DatabaseType.CLICKHOUSE, DatabaseType.IOTDB
    );

    private static final Map<DatabaseType, String[]> DRIVER_JAR_PATTERNS = Map.ofEntries(
            Map.entry(DatabaseType.SQLSERVER, new String[]{"mssql-jdbc"}),
            Map.entry(DatabaseType.GBASE8A, new String[]{"gbase8a-driver", "gbase"}),
            Map.entry(DatabaseType.VERTICA, new String[]{"vertica-jdbc", "vertica"}),
            Map.entry(DatabaseType.MAXCOMPUTE, new String[]{"odps-jdbc", "maxcompute"}),
            Map.entry(DatabaseType.DAMENG, new String[]{"DmJdbcDriver", "dm"}),
            Map.entry(DatabaseType.KINGBASE, new String[]{"kingbase"}),
            Map.entry(DatabaseType.OCEANBASE_ORACLE, new String[]{"oceanbase-client", "oceanbase"}),
            Map.entry(DatabaseType.HIVE, new String[]{"huawei-mrs-hive", "hive-jdbc"}),
            Map.entry(DatabaseType.HETU, new String[]{"trino-jdbc", "presto-jdbc", "hetu"}),
            Map.entry(DatabaseType.DORIS, new String[]{"mysql-connector", "mysql"}),
            Map.entry(DatabaseType.STARROCKS, new String[]{"mysql-connector", "mysql"}),
            Map.entry(DatabaseType.CLICKHOUSE, new String[]{"clickhouse-jdbc", "clickhouse"}),
            Map.entry(DatabaseType.IOTDB, new String[]{"iotdb-jdbc", "iotdb"})
    );

    private static final Set<String> REGISTERED_EXTERNAL_DRIVERS = ConcurrentHashMap.newKeySet();

    /**
     * 连接池缓存：按数据源配置键复用 HikariDataSource，避免每次新建连接池导致连接耗尽
     */
    private static final Cache<String, HikariDataSource> DATA_SOURCE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener(notification -> {
                Object v = notification.getValue();
                if (v instanceof HikariDataSource) {
                    try {
                        ((HikariDataSource) v).close();
                        log.debug("连接池已关闭: key={}", notification.getKey());
                    } catch (Exception e) {
                        log.warn("关闭连接池异常: {}", e.getMessage());
                    }
                }
            })
            .build();

    /**
     * 规范化字符串：null/空串统一为 ""，trim 避免因空格导致重复建池
     */
    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }

    /**
     * 规范化 extraParams：按 & 分割后排序，避免同参不同序导致重复建池
     */
    private static String canonicalizeParams(String extraParams) {
        String p = normalize(extraParams);
        if (p.isEmpty()) return "";
        if (p.startsWith("?")) {
            p = p.substring(1);
        }
        String[] parts = p.split("&");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String it = normalize(part);
            if (!it.isEmpty()) items.add(it);
        }
        Collections.sort(items);
        return String.join("&", items);
    }

    private static String sha256Hex(String s) {
        if (s == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 极端情况下退化为 hashCode，避免阻断
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String redactKey(String key) {
        if (key == null) return null;
        // key 中包含 pwd=xxxx，日志输出时脱敏
        return key.replaceAll("\\|pwd=[^|]*", "|pwd=***");
    }

    /**
     * 将数据源配置补齐到“有效配置”，保证只传必要字段也能稳定命中同一连接池。
     * 不会修改入参对象。
     */
    private static DataSourceConfig effectiveConfig(DataSourceConfig config) {
        DataSourceConfig c = new DataSourceConfig();
        c.setDatabaseType(config.getDatabaseType());
        c.setHost(normalize(config.getHost()));
        c.setPort(config.getPort());
        c.setDatabase(normalize(config.getDatabase()));
        c.setUsername(normalize(config.getUsername()));
        c.setPassword(config.getPassword());
        c.setJdbcUrl(normalize(config.getJdbcUrl()));
        c.setJdbcProperties(JdbcDriverPropertyResolver.resolve(config.getJdbcProperties()));
        c.setAuthMode(normalize(config.getAuthMode()));
        c.setPrincipal(normalize(config.getPrincipal()));
        c.setUserPrincipal(normalize(config.getUserPrincipal()));
        c.setKeytabPath(normalize(config.getKeytabPath()));
        c.setKrb5ConfPath(normalize(config.getKrb5ConfPath()));
        c.setJaasConfPath(normalize(config.getJaasConfPath()));
        c.setZookeeperQuorum(normalize(config.getZookeeperQuorum()));
        c.setZookeeperNamespace(normalize(config.getZookeeperNamespace()));
        c.setServiceDiscoveryMode(normalize(config.getServiceDiscoveryMode()));
        c.setSaslQop(normalize(config.getSaslQop()));
        c.setSsl(config.getSsl());
        c.setTrustStorePath(normalize(config.getTrustStorePath()));
        c.setTrustStorePassword(config.getTrustStorePassword());
        c.setClientConfigDir(normalize(config.getClientConfigDir()));

        Integer timeout = config.getConnectionTimeout();
        c.setConnectionTimeout(timeout != null ? timeout : DEFAULT_CONNECTION_TIMEOUT_SECONDS);

        String schema = normalize(config.getSchema());
        if (c.getDatabaseType() == DatabaseType.DAMENG) {
            // 达梦的 database（服务名）与 Schema 是两个概念。必须优先尊重用户
            // 显式填写的业务 Schema；旧数据未填写时才兼容使用 database/username。
            if (schema.isEmpty()) {
                schema = normalize(config.getDatabase());
            }
            if (schema.isEmpty()) {
                schema = c.getUsername().isEmpty() ? "" : c.getUsername().toUpperCase();
            }
        } else if (schema.isEmpty()) {
            if (c.getDatabaseType() == DatabaseType.POSTGRESQL
                    || c.getDatabaseType() == DatabaseType.KINGBASE
                    || c.getDatabaseType() == DatabaseType.GAUSSDB
                    || c.getDatabaseType() == DatabaseType.VERTICA
                    || c.getDatabaseType() == DatabaseType.HETU) {
                schema = "public";
            } else if (c.getDatabaseType() == DatabaseType.SQLSERVER) {
                schema = "dbo";
            } else if (c.getDatabaseType() == DatabaseType.ORACLE
                    || c.getDatabaseType() == DatabaseType.OCEANBASE_ORACLE) {
                schema = c.getUsername().isEmpty() ? "" : c.getUsername().toUpperCase();
            }
        }
        c.setSchema(schema);

        String extra = canonicalizeParams(config.getExtraParams());
        if (extra.isEmpty() && (c.getDatabaseType() == DatabaseType.MYSQL || c.getDatabaseType() == DatabaseType.OCEANBASE_MYSQL)) {
            extra = DEFAULT_MYSQL_PARAMS;
        }
        c.setExtraParams(extra.isEmpty() ? null : extra);
        return c;
    }

    private static void assertWritableDdlSchema(DataSourceConfig config) {
        if (config == null || config.getDatabaseType() != DatabaseType.DAMENG) {
            return;
        }
        String schema = normalize(config.getSchema());
        if (schema.isEmpty()) {
            throw new IllegalArgumentException("达梦目标数据源未配置业务模式（Schema），无法执行物化建表");
        }
        if (DAMENG_SYSTEM_SCHEMAS.contains(schema.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("达梦目标数据源的默认模式“" + schema
                    + "”是系统内部模式；请在数据源连接信息中配置可建表的业务 Schema 后重试");
        }
    }

    private static String escapeSqlStringLiteral(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    private static String quoteIdent(String ident) {
        String v = normalizeIdentifierName(ident);
        if (v.isEmpty()) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 规范化标识符名称：
     * - 去除首尾空格
     * - 去除外层一到多层双引号
     * - 还原转义双引号
     */
    private static String normalizeIdentifierName(String ident) {
        String name = normalize(ident);
        if (name.isEmpty()) {
            return name;
        }
        while (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.replace("\"\"", "\"");
    }

    private static String normalizeColumnNameForDdl(DatabaseType dbType, String columnName) {
        String name = stripOwnerPrefix(columnName);
        if (name.isEmpty()) {
            return name;
        }
        if (dbType == DatabaseType.DAMENG && name.startsWith("\"") && name.endsWith("\"")) {
            return quoteIdent(normalizeIdentifierName(name));
        }
        return name;
    }

    /**
     * 建表脚本应在当前连接的默认 schema 中执行，展示和执行的表名、字段名均不携带用户名/schema 前缀。
     */
    private static String stripOwnerPrefix(String identifier) {
        String value = normalize(identifier);
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1).trim();
    }

    private static String qualifyTable(DataSourceConfig cfg, String tableName) {
        String schema = normalize(cfg.getSchema());
        if (schema.isEmpty()) return quoteIdent(tableName);
        return quoteIdent(schema) + "." + quoteIdent(tableName);
    }

    /**
     * 针对生成 DDL 的字段类型做规范化，避免出现诸如 DATE(0)、TIMESTAMP(0) 这类语义不正确的类型。
     */
    private static String normalizeGeneratedColumnType(DatabaseType dbType, ColumnInfo col, String type) {
        if (type == null) {
            return null;
        }
        String t = type.trim();
        if (t.isEmpty()) {
            return t;
        }

        // 统一用大写做匹配，返回时保持大写关键字
        String upper = t.toUpperCase(Locale.ROOT);

        // 去掉无意义的 (0) 显示宽度，适用于整数类型、日期时间类型等
        // 这些类型的 (0) 是显示宽度或无效精度，生成 DDL 时应去除
        if (upper.matches("(INT|TINYINT|SMALLINT|MEDIUMINT|BIGINT|DATE|TIME|TIMESTAMP|DATETIME|YEAR)\\s*\\(0\\)")) {
            return upper.replaceAll("\\s*\\(0\\)", "");
        }

        // 去掉 TEXT/LONGTEXT 等文本类型的长度参数，这些类型不支持或不应该有长度参数
        // LONGTEXT(4294967295)、MEDIUMTEXT(16777215)、TEXT(65535) 等应去除括号部分
        if (upper.matches("(LONGTEXT|MEDIUMTEXT|TINYTEXT|TEXT)\\s*\\(\\d+\\)")) {
            return upper.replaceAll("\\s*\\(\\d+\\)", "");
        }

        String base = upper.replaceAll("\\s*\\(.*$", "");
        String args = "";
        int leftParen = upper.indexOf('(');
        int rightParen = upper.lastIndexOf(')');
        if (leftParen >= 0 && rightParen > leftParen) {
            args = upper.substring(leftParen + 1, rightParen).trim();
        }

        if (dbType == DatabaseType.ORACLE
                || dbType == DatabaseType.DAMENG
                || dbType == DatabaseType.OCEANBASE_ORACLE) {
            return normalizeOracleLikeColumnType(base, args, upper, col);
        }
        if (dbType == DatabaseType.POSTGRESQL
                || dbType == DatabaseType.KINGBASE
                || dbType == DatabaseType.GAUSSDB
                || dbType == DatabaseType.VERTICA
                || dbType == DatabaseType.HETU) {
            return normalizePostgresLikeColumnType(base, args, upper, col);
        }
        if (dbType == DatabaseType.HIVE) {
            return normalizeHiveColumnType(base, upper);
        }
        if (dbType == DatabaseType.MYSQL
                || dbType == DatabaseType.MARIADB
                || dbType == DatabaseType.OCEANBASE_MYSQL
                || dbType == DatabaseType.DORIS
                || dbType == DatabaseType.STARROCKS) {
            return normalizeMySqlLikeColumnType(base, args, upper, col);
        }

        return t;
    }

    private static String normalizeOracleLikeColumnType(
            String base, String args, String originalUpper, ColumnInfo column) {
        switch (base) {
            case "VARCHAR":
            case "VARCHAR2":
                return "VARCHAR2(" + normalizeLength(args, columnLength(column, "255")) + ")";
            case "CHAR":
                return "CHAR(" + normalizeLength(args, columnLength(column, "1")) + ")";
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
                return "CLOB";
            case "DATETIME":
                return "TIMESTAMP";
            case "BOOL":
            case "BOOLEAN":
                return "NUMBER(1)";
            case "TINYINT":
            case "SMALLINT":
            case "MEDIUMINT":
            case "INT":
            case "INTEGER":
                return "NUMBER(10)";
            case "BIGINT":
                return "NUMBER(19)";
            case "DOUBLE":
                return "BINARY_DOUBLE";
            case "FLOAT":
                return "BINARY_FLOAT";
            case "DECIMAL":
            case "NUMERIC":
                return args.isEmpty() ? "NUMBER" : "NUMBER(" + args + ")";
            default:
                return originalUpper;
        }
    }

    private static String normalizePostgresLikeColumnType(
            String base, String args, String originalUpper, ColumnInfo column) {
        switch (base) {
            case "VARCHAR2":
            case "NVARCHAR2":
                return "VARCHAR(" + normalizeLength(args, columnLength(column, "255")) + ")";
            case "DATETIME":
                return "TIMESTAMP";
            case "TINYINT":
                return "SMALLINT";
            case "MEDIUMINT":
                return "INTEGER";
            case "DOUBLE":
                return "DOUBLE PRECISION";
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
                return "TEXT";
            case "NUMBER":
                return args.isEmpty() ? "NUMERIC" : "NUMERIC(" + args + ")";
            default:
                return originalUpper;
        }
    }

    private static String normalizeHiveColumnType(String base, String originalUpper) {
        switch (base) {
            case "VARCHAR":
            case "VARCHAR2":
            case "CHAR":
            case "TEXT":
            case "TINYTEXT":
            case "MEDIUMTEXT":
            case "LONGTEXT":
                return "STRING";
            case "DATETIME":
                return "TIMESTAMP";
            default:
                return originalUpper;
        }
    }

    /**
     * MySQL family requires a length for VARCHAR/CHAR.  Low-code field forms
     * keep the type and length in separate properties (for example,
     * {@code columnType = "varchar"} and {@code length = 32}); retaining the
     * bare column type produces invalid SQL such as {@code VARCHAR NOT NULL}.
     */
    private static String normalizeMySqlLikeColumnType(
            String base, String args, String originalUpper, ColumnInfo column) {
        switch (base) {
            case "VARCHAR":
            case "VARCHAR2":
                return "VARCHAR(" + normalizeLength(args, columnLength(column, "255")) + ")";
            case "CHAR":
                return "CHAR(" + normalizeLength(args, columnLength(column, "1")) + ")";
            case "DECIMAL":
            case "NUMERIC":
                if (!args.isEmpty()) {
                    return "DECIMAL(" + args + ")";
                }
                if (column != null && column.getPrecision() != null && column.getPrecision() > 0) {
                    String precision = String.valueOf(column.getPrecision());
                    if (column.getScale() != null && column.getScale() >= 0) {
                        return "DECIMAL(" + precision + "," + column.getScale() + ")";
                    }
                    return "DECIMAL(" + precision + ")";
                }
                return "DECIMAL(18,0)";
            default:
                return originalUpper;
        }
    }

    private static String columnLength(ColumnInfo column, String defaultLength) {
        if (column != null && column.getLength() != null && column.getLength() > 0) {
            return String.valueOf(column.getLength());
        }
        return defaultLength;
    }

    private static String normalizeLength(String args, String defaultLength) {
        if (args == null || args.isBlank()) {
            return defaultLength;
        }
        String first = args.split(",", 2)[0].trim();
        return first.matches("\\d+") ? first : defaultLength;
    }

    /**
     * A source column default can contain a PostgreSQL-style cast. KingBase
     * accepts some of these casts, but the canvas DDL parser does not parse
     * them reliably. For a plain literal the cast is redundant because the
     * destination column supplies the type. Do not silently rewrite complex
     * expressions (for example nextval(...::regclass)).
     */
    private static String normalizeGeneratedDefaultValue(DatabaseType dbType, String columnName, String rawValue) {
        String value = normalize(rawValue);
        if (dbType != DatabaseType.KINGBASE || value.isEmpty()) {
            return value;
        }
        var literalCast = KINGBASE_LITERAL_CAST_DEFAULT.matcher(value);
        if (literalCast.matches()) {
            return literalCast.group(1);
        }
        if (containsCastOutsideString(value)) {
            throw new IllegalArgumentException("字段 " + columnName
                    + " 的默认值包含暂不支持的类型转换，请改为目标库可用的默认值表达式: " + value);
        }
        return value;
    }

    private static boolean containsCastOutsideString(String value) {
        boolean quoted = false;
        for (int i = 0; i < value.length() - 1; i++) {
            if (value.charAt(i) == '\'') {
                if (quoted && value.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && value.charAt(i) == ':' && value.charAt(i + 1) == ':') {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成连接池缓存键（包含密码指纹，不包含明文）。
     * 以“有效 JDBC URL + 用户名 + 驱动 + 超时 + 密码指纹”为主，避免同参不同序/缺省值导致重复建池；
     * 同时避免密码变更时误复用旧连接池。
     */
    private static String poolCacheKey(DataSourceConfig config) {
        DataSourceConfig c = effectiveConfig(config);
        String jdbcUrl = c.buildJdbcUrl();
        String pwdHashShort = sha256Hex(c.getPassword());
        if (pwdHashShort.length() > 12) {
            pwdHashShort = pwdHashShort.substring(0, 12);
        }
        // krb5/JAAS are JVM-side settings rather than JDBC URL parameters. Keep
        // their identity in the pool key so different MRS client profiles never
        // reuse one another's authenticated Hive connection pool.
        String securityProfile = sha256Hex(String.join("|",
                normalize(c.getKrb5ConfPath()),
                normalize(c.getJaasConfPath()),
                normalize(c.getClientConfigDir()),
                JdbcDriverPropertyResolver.resolve(c.getJdbcProperties()).toString()));
        if (securityProfile.length() > 12) {
            securityProfile = securityProfile.substring(0, 12);
        }
        return String.join("|",
                "type=" + c.getDatabaseType().name(),
                "driver=" + normalize(c.getDatabaseType().getDriverClass()),
                "url=" + jdbcUrl,
                "user=" + normalize(c.getUsername()),
                "timeout=" + String.valueOf(c.getConnectionTimeout() != null ? c.getConnectionTimeout() : DEFAULT_CONNECTION_TIMEOUT_SECONDS),
                "pwd=" + pwdHashShort,
                "security=" + securityProfile
        );
    }

    /**
     * 获取或创建 HikariDataSource。同一 key 在并发下也只会创建一次（Guava Cache.get 保证）。
     */
    private static HikariDataSource getOrCreateDataSource(DataSourceConfig config) {
        DataSourceConfig c = effectiveConfig(config);
        String key = poolCacheKey(c);
        try {
            return DATA_SOURCE_CACHE.get(key, () -> createHikariDataSource(c, key));
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("获取连接池失败: " + cause.getMessage(), cause);
        }
    }

    /**
     * 创建新的 HikariDataSource（连接池），供缓存使用
     */
    private static HikariDataSource createHikariDataSource(DataSourceConfig config, String key) {
        int connectionTimeoutSeconds = config.getConnectionTimeout() != null
                ? config.getConnectionTimeout()
                : DEFAULT_CONNECTION_TIMEOUT_SECONDS;
        long connectionTimeoutMillis = connectionTimeoutSeconds * 1000L;
        String driverClass = config.getDatabaseType().getDriverClass();
        boolean driverVisible = ensureDriverVisible(config, driverClass);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.buildJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        if (config.getDatabaseType() == DatabaseType.HETU && config.getSsl() != null) {
            hikariConfig.addDataSourceProperty("SSL", String.valueOf(config.getSsl()));
        }
        JdbcDriverPropertyResolver.resolve(config.getJdbcProperties()).forEach(
                hikariConfig::addDataSourceProperty);
        if (driverVisible && driverClass != null && !driverClass.isBlank()) {
            hikariConfig.setDriverClassName(driverClass);
        }
        hikariConfig.setPoolName("metaUtil-" + Integer.toHexString(key.hashCode()));
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(connectionTimeoutMillis);
        applyDriverTimeouts(hikariConfig, config.getDatabaseType(), connectionTimeoutSeconds, connectionTimeoutMillis);
        hikariConfig.setIdleTimeout(600000L);
        hikariConfig.setMaxLifetime(1800000L);
        HikariDataSource ds = new HikariDataSource(hikariConfig);
        log.debug("新建连接池: key={}", redactKey(key));
        return ds;
    }

    /**
     * Hikari 的 connectionTimeout 只限制“等待连接池返回连接”的时长，不能可靠中断 JDBC 驱动底层建连。
     * 因此同步配置各驱动自己的网络超时，避免目标地址不可达时被操作系统重试拖到数分钟。
     */
    /**
     * Ensure a JDBC driver can be used by Hikari. Drivers on the application
     * classpath are used directly; vendor jars under deploy/nifi/drivers are
     * registered with DriverManager on demand.
     */
    private static boolean ensureDriverVisible(DataSourceConfig config, String driverClass) {
        DatabaseType databaseType = config.getDatabaseType();
        if (driverClass == null || driverClass.isBlank()) {
            return false;
        }
        // Huawei MRS distributes a Hive JDBC implementation together with a matching
        // Hadoop/ZooKeeper client set.  When it is supplied locally, prefer that
        // isolated client over the generic Hive dependency packaged with the service.
        // This avoids mixing an MRS 3.x Kerberos client with unrelated Hive libraries.
        if (requiresHuaweiMrsDriver(config) && findDriverJar(databaseType) != null) {
            return registerExternalDriver(databaseType, driverClass);
        }
        try {
            Class.forName(driverClass);
            return true;
        } catch (ClassNotFoundException ignored) {
            return registerExternalDriver(databaseType, driverClass);
        }
    }

    private static boolean registerExternalDriver(DatabaseType databaseType, String driverClass) {
        String cacheKey = databaseType.name() + "|" + driverClass;
        if (REGISTERED_EXTERNAL_DRIVERS.contains(cacheKey)) {
            return false;
        }
        File jar = findDriverJar(databaseType);
        if (jar == null) {
            log.warn("未找到 {} 的外部 JDBC 驱动 jar，driverClass={}", databaseType, driverClass);
            return false;
        }
        try {
            URLClassLoader classLoader = databaseType == DatabaseType.HIVE
                    ? new HiveClientClassLoader(driverUrls(jar), Thread.currentThread().getContextClassLoader())
                    : new URLClassLoader(new URL[]{jar.toURI().toURL()}, Thread.currentThread().getContextClassLoader());
            Class<?> driverType = Class.forName(driverClass, true, classLoader);
            Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
            REGISTERED_EXTERNAL_DRIVERS.add(cacheKey);
            log.info("已加载外部 JDBC 驱动: type={}, jar={}, class={}", databaseType, jar.getAbsolutePath(), driverClass);
            // Do not set Hikari's driverClassName for a child-loader driver:
            // DriverManager discovers the registered DriverShim without asking the
            // application class loader to resolve this vendor class again.
            return false;
        } catch (Exception e) {
            log.warn("加载外部 JDBC 驱动失败: type={}, driverClass={}, jar={}, error={}",
                    databaseType, driverClass, jar.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    private static File findDriverJar(DatabaseType databaseType) {
        String[] patterns = DRIVER_JAR_PATTERNS.get(databaseType);
        if (patterns == null || patterns.length == 0) {
            return null;
        }
        File dir = new File(System.getProperty("user.dir"), "deploy/nifi/drivers");
        if (!dir.isDirectory()) {
            dir = new File(System.getProperty("user.dir"), "data-elements/deploy/nifi/drivers");
        }
        // MRS client libraries belong in a dedicated directory so their Hadoop/Hive
        // versions do not get mixed with other vendor drivers.
        if (databaseType == DatabaseType.HIVE) {
            File mrsDir = new File(dir, "huawei-mrs");
            if (mrsDir.isDirectory()) {
                dir = mrsDir;
            }
        }
        File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar")) : null;
        if (files == null) {
            return null;
        }
        for (String pattern : patterns) {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            for (File file : files) {
                if (file.getName().toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static boolean requiresHuaweiMrsDriver(DataSourceConfig config) {
        return config != null
                && config.getDatabaseType() == DatabaseType.HIVE
                && "KERBEROS".equalsIgnoreCase(normalize(config.getAuthMode()));
    }

    private static URL[] driverUrls(File driverJar) throws java.net.MalformedURLException {
        File directory = driverJar.getParentFile();
        File[] jars = directory == null ? null : directory.listFiles((d, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return new URL[]{driverJar.toURI().toURL()};
        }
        return Arrays.stream(jars)
                .sorted(Comparator.comparing(File::getName))
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (java.net.MalformedURLException ex) {
                        throw new IllegalStateException("非法 MRS 驱动路径: " + file, ex);
                    }
                })
                .toArray(URL[]::new);
    }

    /**
     * MRS ships Hive/Hadoop/ZooKeeper as one compatible set.  The application also
     * bundles generic Hive dependencies, therefore the MRS package must resolve its
     * own classes first while JDBC interfaces continue to come from the parent JVM.
     */
    private static final class HiveClientClassLoader extends URLClassLoader {
        private HiveClientClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.hive.")
                    || name.startsWith("org.apache.hadoop.")
                    || name.startsWith("org.apache.zookeeper.")
                    || name.startsWith("org.apache.curator.")) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class DriverShim implements Driver {
        private final Driver delegate;

        private DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, java.util.Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    private static void applyDriverTimeouts(
            HikariConfig hikariConfig,
            DatabaseType databaseType,
            int connectionTimeoutSeconds,
            long connectionTimeoutMillis
    ) {
        switch (databaseType) {
            case ORACLE:
            case OCEANBASE_ORACLE:
                hikariConfig.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", connectionTimeoutMillis);
                hikariConfig.addDataSourceProperty("oracle.jdbc.ReadTimeout", 60000);
                hikariConfig.addDataSourceProperty("oracle.net.keepAlive", true);
                break;
            case MYSQL:
            case OCEANBASE_MYSQL:
            case GBASE8A:
                hikariConfig.addDataSourceProperty("connectTimeout", connectionTimeoutMillis);
                hikariConfig.addDataSourceProperty("socketTimeout", 60000);
                break;
            case POSTGRESQL:
            case KINGBASE:
            case GAUSSDB:
            case VERTICA:
                hikariConfig.addDataSourceProperty("connectTimeout", connectionTimeoutSeconds);
                hikariConfig.addDataSourceProperty("loginTimeout", connectionTimeoutSeconds);
                hikariConfig.addDataSourceProperty("socketTimeout", 60);
                break;
            case SQLSERVER:
                hikariConfig.addDataSourceProperty("loginTimeout", connectionTimeoutSeconds);
                hikariConfig.addDataSourceProperty("socketTimeout", 60000);
                break;
            default:
                // 其他驱动继续使用 Hikari 的连接池超时，避免传入驱动不支持的属性。
                break;
        }
    }

    /**
     * 从连接池获取数据库连接；调用方须在 try-with-resources 中关闭 connection，连接将归还池内。
     */
    protected Connection getConnection(DataSourceConfig config) throws Exception {
        if (config.getDatabaseType() == null || !JDBC_TYPES.contains(config.getDatabaseType())) {
            throw new IllegalArgumentException("不支持的数据库类型或非 JDBC 类型: " + config.getDatabaseType());
        }
        HikariDataSource dataSource = getOrCreateDataSource(config);
        return dataSource.getConnection();
    }

    /**
     * 通用建表 DDL 生成：使用 ColumnInfo 中的 columnType / dataType 等信息拼装 ANSI 风格 DDL。
     * 具体数据库若有差异，可在各自实现类中覆盖。
     */
    @Override
    public String generateCreateTableDdl(DataSourceConfig config, String tableName, List<ColumnInfo> columns, String tableComment) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("字段列表不能为空");
        }

        DataSourceConfig cfg = config != null ? effectiveConfig(config) : null;
        DatabaseType dbType = cfg != null ? cfg.getDatabaseType() : null;

        // 兼容：若无 config，退化为无注释的通用 DDL
        if (dbType == null) {
            return generateCreateTableDdl(tableName, columns, tableComment);
        }
        assertWritableDdlSchema(cfg);

        String tableCmt = normalize(tableComment);

        // 1) 生成 CREATE TABLE 主体
        StringBuilder ddl = new StringBuilder();
        String createTableName = stripOwnerPrefix(tableName);
        ddl.append("CREATE TABLE ").append(createTableName).append(" (\n");

        List<String> pkColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            if (col == null || normalize(col.getColumnName()).isEmpty()) {
                continue;
            }
            String name = normalizeColumnNameForDdl(dbType, col.getColumnName());
            String type = normalize(col.getColumnType());
            if (type.isEmpty()) {
                type = normalize(col.getDataType());
            }
            if (type.isEmpty()) {
                throw new IllegalArgumentException("字段 " + name + " 的类型不能为空");
            }

            type = normalizeGeneratedColumnType(dbType, col, type);

            ddl.append("  ").append(name).append(" ").append(type);

            if (Boolean.FALSE.equals(col.getNullable())) {
                ddl.append(" NOT NULL");
            }
            String defaultValue = normalizeGeneratedDefaultValue(dbType, name, col.getDefaultValue());
            if (!defaultValue.isEmpty()) {
                ddl.append(" DEFAULT ").append(defaultValue);
            }

            // MySQL 系支持 AUTO_INCREMENT
            if ((dbType == DatabaseType.MYSQL || dbType == DatabaseType.OCEANBASE_MYSQL)
                    && Boolean.TRUE.equals(col.getAutoIncrement())) {
                ddl.append(" AUTO_INCREMENT");
            }

            // 列注释
            String colCmt = normalize(col.getColumnComment());
            if (!colCmt.isEmpty()) {
                switch (dbType) {
                    case MYSQL:
                    case OCEANBASE_MYSQL:
                    case DORIS:
                    case STARROCKS:
                    case CLICKHOUSE:
                    case IOTDB:
                        ddl.append(" COMMENT '").append(escapeSqlStringLiteral(colCmt)).append("'");
                        break;
                    case HIVE:
                        ddl.append(" COMMENT '").append(escapeSqlStringLiteral(colCmt)).append("'");
                        break;
                    default:
                        // 其余库使用 COMMENT ON COLUMN 语句在后面追加
                        break;
                }
            }

            if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                pkColumns.add(name);
            }

            // 后面还会追加 PK 约束时，这里需要逗号
            if (i < columns.size() - 1 || !pkColumns.isEmpty()) {
                ddl.append(",");
            }
            ddl.append("\n");
        }

        if (!pkColumns.isEmpty()) {
            ddl.append("  PRIMARY KEY (")
                    .append(String.join(", ", pkColumns))
                    .append(")\n");
        }

        ddl.append(")");

        // 表注释（MySQL/Hive 可内联）
        if (!tableCmt.isEmpty()) {
            if (dbType == DatabaseType.MYSQL
                    || dbType == DatabaseType.OCEANBASE_MYSQL
                    || dbType == DatabaseType.DORIS
                    || dbType == DatabaseType.STARROCKS
                    || dbType == DatabaseType.CLICKHOUSE
                    || dbType == DatabaseType.IOTDB) {
                ddl.append(" COMMENT='").append(escapeSqlStringLiteral(tableCmt)).append("'");
            } else if (dbType == DatabaseType.HIVE) {
                ddl.append(" COMMENT '").append(escapeSqlStringLiteral(tableCmt)).append("'");
            }
        }
        ddl.append(";\n");

        // 2) 追加 COMMENT ON（PG/KingBase/GaussDB/Oracle/达梦/OceanBase(Oracle)）
        boolean useCommentOn = switch (dbType) {
            case POSTGRESQL, KINGBASE, GAUSSDB, ORACLE, DAMENG, OCEANBASE_ORACLE -> true;
            default -> false;
        };

        if (useCommentOn) {
            // Reuse the exact identifier form from CREATE TABLE. Quoting an
            // unquoted lower-case Oracle name here would look up a different,
            // lower-case object and cause ORA-00942 after table creation.
            String qualifiedTable = stripOwnerPrefix(tableName);
            if (!tableCmt.isEmpty()) {
                ddl.append("COMMENT ON TABLE ").append(qualifiedTable)
                        .append(" IS '").append(escapeSqlStringLiteral(tableCmt)).append("';\n");
            }
            for (ColumnInfo col : columns) {
                if (col == null) continue;
                String colName = normalizeColumnNameForDdl(dbType, col.getColumnName());
                String colCmt = normalize(col.getColumnComment());
                if (colName.isEmpty() || colCmt.isEmpty()) continue;
                ddl.append("COMMENT ON COLUMN ").append(qualifiedTable).append(".").append(colName)
                        .append(" IS '").append(escapeSqlStringLiteral(colCmt)).append("';\n");
            }
        }

        return ddl.toString();
    }

    /**
     * 旧签名：无 config 情况下生成不含注释的通用 DDL（兼容保留）。
     */
    @Override
    public String generateCreateTableDdl(String tableName, List<ColumnInfo> columns, String tableComment) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("字段列表不能为空");
        }

        StringBuilder ddl = new StringBuilder();
        if (tableComment != null && !tableComment.trim().isEmpty()) {
            ddl.append("-- ").append(tableComment.replace('\n', ' ')).append('\n');
        }

        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        List<String> pkColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            if (col.getColumnName() == null || col.getColumnName().trim().isEmpty()) {
                continue;
            }
            String name = normalizeColumnNameForDdl(null, col.getColumnName());
            String type = col.getColumnType();
            if (type == null || type.trim().isEmpty()) {
                type = col.getDataType();
            }
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("字段 " + name + " 的类型不能为空");
            }

            type = normalizeGeneratedColumnType(null, col, type);

            ddl.append("  ").append(name).append(" ").append(type);

            if (Boolean.FALSE.equals(col.getNullable())) {
                ddl.append(" NOT NULL");
            }
            if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT ").append(col.getDefaultValue());
            }

            if (Boolean.TRUE.equals(col.getPrimaryKey())) {
                pkColumns.add(name);
            }

            if (i < columns.size() - 1 || !pkColumns.isEmpty()) {
                ddl.append(",");
            }
            ddl.append("\n");
        }

        if (!pkColumns.isEmpty()) {
            ddl.append("  PRIMARY KEY (")
                    .append(String.join(", ", pkColumns))
                    .append(")\n");
        }

        ddl.append(");");
        return ddl.toString();
    }

    /**
     * 通用建表执行：直接执行传入的 DDL 语句。
     */
    public void createTable(DataSourceConfig config, String ddl) {
        if (ddl == null || ddl.trim().isEmpty()) {
            throw new IllegalArgumentException("DDL 语句不能为空");
        }
        DataSourceConfig effectiveConfig = effectiveConfig(config);
        assertWritableDdlSchema(effectiveConfig);
        try (Connection conn = getConnection(effectiveConfig);
             Statement stmt = conn.createStatement()) {
            // 在执行 DDL 之前，根据不同数据库类型应用 schema / search_path / current_schema 等设置
            applySchemaForDdl(conn, effectiveConfig);
            List<String> statements = splitSqlStatements(ddl);
            if (statements.isEmpty()) {
                throw new IllegalArgumentException("DDL 璇彞涓嶈兘涓虹┖");
            }
            for (String sql : statements) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            log.error("执行建表DDL失败: {}", e.getMessage(), e);
            throw new RuntimeException("执行建表DDL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按语句终止符拆分 SQL，仅在不在单双引号内时按 ';' 分割。
     */
    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);

            if (ch == '\'' && !inDoubleQuote) {
                // SQL 字符串转义使用两个连续单引号
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(ch).append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                current.append(ch);
                continue;
            }

            if (ch == '"' && !inSingleQuote) {
                // 标识符转义使用两个连续双引号
                if (inDoubleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    current.append(ch).append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
                current.append(ch);
                continue;
            }

            if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    /**
     * 针对建表等 DDL，在当前连接上应用合适的 schema 设置，使传入的 schema 参数在不同数据源下生效。
     * <p>
     * - PostgreSQL/KingBase/GaussDB: 使用 SET search_path TO "schema"
     * - Oracle/OceanBase(Oracle)/Dameng: 使用 ALTER SESSION SET CURRENT_SCHEMA = schema
     * - 其他数据库（MySQL/OceanBase(MySQL)/Hive 等）主要依赖 database，不使用 schema 参数
     */
    private void applySchemaForDdl(Connection conn, DataSourceConfig originalConfig) {
        DataSourceConfig config = effectiveConfig(originalConfig);
        String schema = normalize(config.getSchema());
        if (schema.isEmpty()) {
            return;
        }

        String applySql = null;
        DatabaseType type = config.getDatabaseType();
        switch (type) {
            case POSTGRESQL:
            case KINGBASE:
            case GAUSSDB:
                // PostgreSQL 语系使用 search_path 控制默认 schema
                String escaped = schema.replace("\"", "\"\"");
                applySql = "SET search_path TO \"" + escaped + "\"";
                break;
            case ORACLE:
            case OCEANBASE_ORACLE:
            case DAMENG:
                applySql = "ALTER SESSION SET CURRENT_SCHEMA = " + schema;
                break;
            default:
                // 其余类型暂不处理 schema（MySQL/Hive 等）
                break;
        }

        if (applySql == null) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(applySql);
            log.debug("建表前设置 schema 成功: type={}, schema={}", type, schema);
        } catch (Exception e) {
            // 不影响主流程，只记录告警
            log.warn("建表前设置 schema 失败: type={}, schema={}, msg={}", type, schema, e.getMessage());
        }
    }

    /**
     * 测试连接
     */
    @Override
    public boolean testConnection(DataSourceConfig config) {
        try (Connection conn = getConnection(config)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("连接测试失败: {}", e.getMessage(), e);
            throw new DataSourceConnectionException("数据源连接测试失败", e);
        }
    }

    /**
     * 格式化字节大小
     */
    protected String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);

        return String.format("%.2f %s",
                bytes / Math.pow(1024, digitGroups),
                units[digitGroups]);
    }

    /**
     * 执行抽样查询
     */
    protected SampleDataResult executeSampleQuery(DataSourceConfig config,
                                                  SampleRequest request,
                                                  String sql) {
        SampleDataResult result = new SampleDataResult();
        result.setTableName(request.getTableName());
        result.setSampleSize(request.getSampleSize());
        result.setSampleMethod(request.getSampleMethod().name());

        List<Map<String, Object>> data = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();

        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 获取列信息
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnName(i));
                columnTypes.add(metaData.getColumnTypeName(i));
            }

            // 获取数据
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    // 处理特殊类型
                    if (value instanceof java.sql.Clob) {
                        java.sql.Clob clob = (java.sql.Clob) value;
                        value = clob.getSubString(1, (int) Math.min(clob.length(), 1000));
                    } else if (value instanceof java.sql.Blob) {
                        value = "[BLOB DATA]";
                    } else if (value instanceof byte[]) {
                        value = "[BINARY DATA]";
                    }
                    row.put(metaData.getColumnName(i), JdbcValueNormalizer.normalize(value));
                }
                data.add(row);
            }

        } catch (Exception e) {
            log.error("数据抽样失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据抽样失败: " + e.getMessage(), e);
        }

        result.setColumnNames(columnNames);
        result.setColumnTypes(columnTypes);
        result.setData(data);
        result.setActualSize(data.size());

        // 获取总记录数
        try {
            result.setTotalCount(getTableRowCount(config, request.getTableName()));
        } catch (Exception e) {
            log.warn("获取表记录数失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 校验 SQL 语句语法是否正确。
     * <p>
     * 根据不同数据库类型采用不同的校验策略：
     * <ul>
     *   <li>MySQL / OceanBase(MySQL)：使用 EXPLAIN 语法校验</li>
     *   <li>PostgreSQL / KingBase / GaussDB：使用 PREPARE 语法校验</li>
     *   <li>Oracle / OceanBase(Oracle) / 达梦：使用 EXPLAIN PLAN FOR 语法校验</li>
     *   <li>Hive：使用 EXPLAIN 语法校验</li>
     * </ul>
     * 对于 DDL 语句（CREATE/ALTER/DROP 等），统一使用 JDBC prepareStatement 进行语法校验。
     * </p>
     *
     * @param config 数据源配置
     * @param sql    待校验的 SQL 语句
     * @return 校验结果
     */
    @Override
    public SqlValidationResult validateSql(DataSourceConfig config, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return SqlValidationResult.failure("SQL 语句不能为空", null, null);
        }
        if (config == null || config.getDatabaseType() == null) {
            return SqlValidationResult.failure("数据源配置或数据库类型不能为空", null, null);
        }

        String trimmedSql = sql.trim();
        String sqlType = detectSqlType(trimmedSql);
        String dbTypeName = config.getDatabaseType().getDisplayName();

        // 非 JDBC 类型不支持
        if (!JDBC_TYPES.contains(config.getDatabaseType())) {
            return SqlValidationResult.failure("不支持的数据库类型: " + dbTypeName, sqlType, dbTypeName);
        }

        try (Connection conn = getConnection(config)) {
            // DDL 语句使用 prepareStatement 校验
            if (isDdlStatement(trimmedSql)) {
                return validateDdlSql(conn, trimmedSql, sqlType, dbTypeName);
            }
            // DML 语句根据数据库类型选择校验策略
            return validateDmlSql(conn, config, trimmedSql, sqlType, dbTypeName);
        } catch (Exception e) {
            log.warn("SQL 校验异常: dbType={}, sql={}, error={}", dbTypeName, trimmedSql, e.getMessage());
            return SqlValidationResult.failure("SQL 校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 检测 SQL 语句类型（SELECT / INSERT / UPDATE / DELETE / CREATE / ALTER / DROP / OTHER）
     */
    private String detectSqlType(String sql) {
        if (sql == null || sql.isEmpty()) return "UNKNOWN";
        String upper = sql.toUpperCase(Locale.ROOT).trim();
        // 去掉开头的注释
        while (upper.startsWith("--") || upper.startsWith("/*")) {
            if (upper.startsWith("--")) {
                int nl = upper.indexOf('\n');
                if (nl < 0) return "UNKNOWN";
                upper = upper.substring(nl + 1).trim();
            } else if (upper.startsWith("/*")) {
                int end = upper.indexOf("*/");
                if (end < 0) return "UNKNOWN";
                upper = upper.substring(end + 2).trim();
            }
        }
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE")) return "CREATE";
        if (upper.startsWith("ALTER")) return "ALTER";
        if (upper.startsWith("DROP")) return "DROP";
        if (upper.startsWith("TRUNCATE")) return "TRUNCATE";
        if (upper.startsWith("WITH")) return "SELECT"; // CTE 视为 SELECT
        return "OTHER";
    }

    /**
     * 判断是否为 DDL 语句
     */
    private boolean isDdlStatement(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT).trim();
        while (upper.startsWith("--") || upper.startsWith("/*")) {
            if (upper.startsWith("--")) {
                int nl = upper.indexOf('\n');
                if (nl < 0) return false;
                upper = upper.substring(nl + 1).trim();
            } else if (upper.startsWith("/*")) {
                int end = upper.indexOf("*/");
                if (end < 0) return false;
                upper = upper.substring(end + 2).trim();
            }
        }
        return upper.startsWith("CREATE") || upper.startsWith("ALTER")
                || upper.startsWith("DROP") || upper.startsWith("TRUNCATE");
    }

    /**
     * 校验 DDL 语句：使用 prepareStatement 进行语法校验（不会实际执行）
     */
    private SqlValidationResult validateDdlSql(Connection conn, String sql, String sqlType, String dbTypeName) {
        try {
            // 使用 prepareStatement 校验语法，不执行
            try (var ps = conn.prepareStatement(sql)) {
                // 仅 prepare，不执行
            }
            return SqlValidationResult.success(sqlType, dbTypeName);
        } catch (Exception e) {
            String msg = e.getMessage();
            log.debug("DDL 语法校验失败: {}", msg);
            return SqlValidationResult.failure("DDL 语法校验失败: " + msg, sqlType, dbTypeName);
        }
    }

    /**
     * 校验 DML 语句：根据不同数据库类型采用不同策略
     */
    private SqlValidationResult validateDmlSql(Connection conn, DataSourceConfig config,
                                               String sql, String sqlType, String dbTypeName) {
        DatabaseType dbType = config.getDatabaseType();
        try {
            switch (dbType) {
                case MYSQL:
                case OCEANBASE_MYSQL:
                case HIVE:
                    // MySQL / Hive 使用 EXPLAIN
                    return validateWithExplain(conn, sql, sqlType, dbTypeName);
                case POSTGRESQL:
                case KINGBASE:
                case GAUSSDB:
                    // PG 系使用 PREPARE
                    return validateWithPrepare(conn, sql, sqlType, dbTypeName);
                case ORACLE:
                case OCEANBASE_ORACLE:
                case DAMENG:
                    // Oracle 系使用 EXPLAIN PLAN FOR
                    return validateWithExplainPlan(conn, sql, sqlType, dbTypeName);
                default:
                    // 其他类型回退到 prepareStatement
                    return validateWithPrepareStatement(conn, sql, sqlType, dbTypeName);
            }
        } catch (Exception e) {
            log.debug("DML 语法校验异常: dbType={}, error={}", dbTypeName, e.getMessage());
            return SqlValidationResult.failure("SQL 语法校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 使用 EXPLAIN 校验（MySQL / Hive / OceanBase MySQL 模式）
     */
    private SqlValidationResult validateWithExplain(Connection conn, String sql,
                                                    String sqlType, String dbTypeName) {
        String explainSql = "EXPLAIN " + sql;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(explainSql);
            return SqlValidationResult.success(sqlType, dbTypeName);
        } catch (Exception e) {
            return SqlValidationResult.failure("SQL 语法校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 使用 PREPARE 校验（PostgreSQL / KingBase / GaussDB）
     */
    private SqlValidationResult validateWithPrepare(Connection conn, String sql,
                                                    String sqlType, String dbTypeName) {
        String stmtName = "_metautil_validate_" + System.nanoTime();
        try (Statement stmt = conn.createStatement()) {
            // PREPARE 会解析 SQL 语法但不执行
            stmt.execute("PREPARE " + stmtName + " AS " + sql);
            // 校验通过后清理
            try {
                stmt.execute("DEALLOCATE PREPARE " + stmtName);
            } catch (Exception ignored) {
                // 清理失败不影响校验结果
            }
            return SqlValidationResult.success(sqlType, dbTypeName);
        } catch (Exception e) {
            return SqlValidationResult.failure("SQL 语法校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 使用 EXPLAIN PLAN FOR 校验（Oracle / OceanBase Oracle 模式 / 达梦）
     */
    private SqlValidationResult validateWithExplainPlan(Connection conn, String sql,
                                                        String sqlType, String dbTypeName) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("EXPLAIN PLAN FOR " + sql);
            return SqlValidationResult.success(sqlType, dbTypeName);
        } catch (Exception e) {
            return SqlValidationResult.failure("SQL 语法校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 使用 prepareStatement 校验（通用回退方案）
     */
    private SqlValidationResult validateWithPrepareStatement(Connection conn, String sql,
                                                             String sqlType, String dbTypeName) {
        try (var ps = conn.prepareStatement(sql)) {
            // 仅 prepare，不执行
            return SqlValidationResult.success(sqlType, dbTypeName);
        } catch (Exception e) {
            return SqlValidationResult.failure("SQL 语法校验失败: " + e.getMessage(), sqlType, dbTypeName);
        }
    }

    /**
     * 构建列选择SQL
     */
    protected String buildColumnSelection(String[] columns) {
        if (columns == null || columns.length == 0) {
            return "*";
        }
        return String.join(", ", columns);
    }

    /**
     * 构建WHERE子句
     */
    protected String buildWhereClause(String whereClause) {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return "";
        }
        return " WHERE " + whereClause;
    }
}
