package com.linewell.dataelement.module;

import com.linewell.dataelement.utils.security.KerberosUtil;
import com.linewell.dataelement.utils.security.LoginUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;

@Slf4j
// Keep the historical gateway module available to Magic API.  Its simple class
// name intentionally overlaps with the NiFi-focused hwhive module, so use an
// explicit bean name to prevent Spring from registering both as "hiveModule".
@Component("huaweiHiveGatewayModule")
@MagicModule("gateway")
public class HiveModule {

    private static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

    private static final String ZOOKEEPER_SERVER_PRINCIPAL_KEY = "zookeeper.server.principal";

    /**
     * 连接池默认参数，可在 hiveclient.properties 中覆盖：
     * pool.maximumPoolSize / pool.minimumIdle / pool.idleTimeout / pool.maxLifetime /
     * pool.connectionTimeout / pool.validationTimeout / pool.leakDetectionThreshold / pool.connectionTestQuery
     */
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    private static final int DEFAULT_MINIMUM_IDLE = 1;
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;
    /** 连接最大存活时间，需小于 Kerberos TGT 有效期，默认 30 分钟。 */
    private static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_VALIDATION_TIMEOUT_MS = 5_000L;
    /** 取到连接后未归还的告警阈值，用于发现脚本调用 getConnection() 后忘记 close()。 */
    private static final long DEFAULT_LEAK_DETECTION_MS = 60_000L;
    /**
     * Hive 的 HiveConnection#isValid 仅判断本地 closed 标志，无法感知服务端已断开的连接，
     * 因此默认用轻量 SQL 做真实校验；置为空（pool.connectionTestQuery=）可关闭该校验。
     */
    private static final String DEFAULT_CONNECTION_TEST_QUERY = "select 1";

    private final Object poolLock = new Object();

    /** Hive 连接池，懒加载；配置变更或应用关闭时重建/释放。 */
    private volatile HikariDataSource dataSource;

    private boolean hiveBasicAuthEnable = false;
    private String hiveConfDir = System.getProperty("hive.conf.dir", "/data/hive");
    private String userName = null;
    private String password = null;

    private int poolMaximumPoolSize = DEFAULT_MAXIMUM_POOL_SIZE;
    private int poolMinimumIdle = DEFAULT_MINIMUM_IDLE;
    private long poolIdleTimeout = DEFAULT_IDLE_TIMEOUT_MS;
    private long poolMaxLifetime = DEFAULT_MAX_LIFETIME_MS;
    private long poolConnectionTimeout = DEFAULT_CONNECTION_TIMEOUT_MS;
    private long poolValidationTimeout = DEFAULT_VALIDATION_TIMEOUT_MS;
    private long poolLeakDetection = DEFAULT_LEAK_DETECTION_MS;
    private String poolConnectionTestQuery = DEFAULT_CONNECTION_TEST_QUERY;

    private String zkQuorum = null;
    private String auth = null;
    private String saslQop = null;
    private String zooKeeperNamespace = null;
    private String serviceDiscoveryMode = null;
    private String principal = null;
    private String auditAddition = null;
    private String ssl = null;
    private String krb5File = null;
    private String userKeytabFile = null;

    public void initConf(String hiveConfDir){
        if(StringUtils.isNotBlank(hiveConfDir) && !hiveConfDir.equals(this.hiveConfDir)){
            this.hiveConfDir = hiveConfDir;
            // 配置目录变化后，原连接池的认证与连接参数已失效，释放后由下次请求重建
            closeDataSource();
        }
    }


    /**
     * 从连接池取连接。调用方必须在 try-with-resources 中使用：
     * close() 表示归还连接池，而不是关闭物理连接。
     */
    public Connection getConnection() throws IOException, SQLException, ClassNotFoundException {
        return getDataSource().getConnection();
    }

    public void execDDLSql(String sql) throws SQLException, IOException, ClassNotFoundException {
        try (Connection connection = getConnection()) {
            execDDL(connection, sql);
        }
    };

    public void execDMLSql(String sql) throws SQLException, IOException, ClassNotFoundException {
        try (Connection connection = getConnection()) {
            execDML(connection, sql);
        }
    };

    /**
     * 应用关闭或配置变更时释放连接池。
     */
    @PreDestroy
    public void closeDataSource() {
        synchronized (poolLock) {
            HikariDataSource ds = dataSource;
            dataSource = null;
            if (null != ds && !ds.isClosed()) {
                ds.close();
            }
        }
    }

    private HikariDataSource getDataSource() throws IOException, ClassNotFoundException {
        HikariDataSource ds = dataSource;
        if (null != ds && !ds.isClosed()) {
            return ds;
        }
        synchronized (poolLock) {
            ds = dataSource;
            if (null != ds && !ds.isClosed()) {
                return ds;
            }
            Class.forName(HIVE_DRIVER);
            init();
            String url = buildJdbcUrl();
            log.info("Hive JDBC url: {}", redact(url));
            dataSource = createDataSource(url);
            return dataSource;
        }
    }

    private HikariDataSource createDataSource(String url) {
        log.info("创建 Hive 连接池: maxPoolSize={}, minIdle={}, maxLifetime={}ms, idleTimeout={}ms",
                poolMaximumPoolSize, poolMinimumIdle, poolMaxLifetime, poolIdleTimeout);
        return new HikariDataSource(buildPoolConfig(url));
    }

    private HikariConfig buildPoolConfig(String url) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("hive-gateway");
        config.setJdbcUrl(url);
        config.setDriverClassName(HIVE_DRIVER);
        // Kerberos 模式下用户名密码为空，认证由 URL 中的 user.principal / user.keytab 完成
        config.setUsername(null == userName ? "" : userName);
        config.setPassword(null == password ? "" : password);
        config.setMaximumPoolSize(poolMaximumPoolSize);
        config.setMinimumIdle(poolMinimumIdle);
        config.setIdleTimeout(poolIdleTimeout);
        config.setMaxLifetime(poolMaxLifetime);
        config.setConnectionTimeout(poolConnectionTimeout);
        config.setValidationTimeout(poolValidationTimeout);
        if (poolLeakDetection > 0) {
            config.setLeakDetectionThreshold(poolLeakDetection);
        }
        if (StringUtils.isNotBlank(poolConnectionTestQuery)) {
            config.setConnectionTestQuery(poolConnectionTestQuery);
        }
        return config;
    }

    private String redact(String url) {
        return url.replaceAll("(?i)(user\\.keytab=)[^;]*", "$1***")
                .replaceAll("(?i)(password=)[^;]*", "$1***");
    }

    private String getUserRealm() {
        String serverRealm = System.getProperty("SERVER_REALM");
        if (serverRealm == null || serverRealm.isEmpty()) {
            serverRealm = KerberosUtil.getKrb5DomainRealm();
        }
        if (serverRealm != null && !serverRealm.isEmpty()) {
            return "hadoop." + serverRealm.toLowerCase();
        }
        return "hadoop";
    }

    private String resolveConfFile(String name) throws IOException {
        File file = new File(hiveConfDir, name);
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        URL resource = HiveModule.class.getClassLoader().getResource("hive/" + name);
        if (resource == null) {
            throw new FileNotFoundException(
                    "Hive conf file not found: " + name
                            + " (dir=" + hiveConfDir + ", classpath=hive/" + name + ")");
        }
        Path tmp = Files.createTempFile("hive-conf-", "-" + name);
        try (InputStream in = resource.openStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        File tmpFile = tmp.toFile();
        tmpFile.deleteOnExit();
        return tmpFile.getAbsolutePath();
    }

    private void init() throws IOException {

        Properties clientInfo = new Properties();
        try (InputStream in = new FileInputStream(resolveConfFile("hiveclient.properties"))) {
            clientInfo.load(in);
        }

        zkQuorum = clientInfo.getProperty("zk.quorum");
        auth = clientInfo.getProperty("auth");
        saslQop = clientInfo.getProperty("sasl.qop");
        zooKeeperNamespace = clientInfo.getProperty("zooKeeperNamespace");
        serviceDiscoveryMode = clientInfo.getProperty("serviceDiscoveryMode");
        principal = clientInfo.getProperty("principal");
        ssl = clientInfo.getProperty("ssl");
        auditAddition = clientInfo.getProperty("auditAddition");

        // krb5.conf / user.keytab 优先从固定目录 /data/hive 下获取，否则回退 classpath
        krb5File = resolveConfFile("krb5.conf");
        System.setProperty("java.security.krb5.conf", krb5File);

        // 设置新建用户的 userName；使用用户名和密码认证时需将 password 配置成真实密码
        userName = clientInfo.getProperty("username");
        password = clientInfo.getProperty("password");

        if ("KERBEROS".equalsIgnoreCase(auth) && !hiveBasicAuthEnable) {
            // 设置客户端的 keytab 和 zookeeper 认证 principal
            userKeytabFile = resolveConfFile("user.keytab");
            String zkServerPrincipal = "zookeeper/" + getUserRealm();
            log.info("zookeeper.server.principal = {}", zkServerPrincipal);
            System.setProperty(ZOOKEEPER_SERVER_PRINCIPAL_KEY, zkServerPrincipal);
        }

        // 连接池参数（缺省值见 DEFAULT_* 常量）
        poolMaximumPoolSize = intProp(clientInfo, "pool.maximumPoolSize", DEFAULT_MAXIMUM_POOL_SIZE);
        poolMinimumIdle = intProp(clientInfo, "pool.minimumIdle", DEFAULT_MINIMUM_IDLE);
        poolIdleTimeout = longProp(clientInfo, "pool.idleTimeout", DEFAULT_IDLE_TIMEOUT_MS);
        poolMaxLifetime = longProp(clientInfo, "pool.maxLifetime", DEFAULT_MAX_LIFETIME_MS);
        poolConnectionTimeout = longProp(clientInfo, "pool.connectionTimeout", DEFAULT_CONNECTION_TIMEOUT_MS);
        poolValidationTimeout = longProp(clientInfo, "pool.validationTimeout", DEFAULT_VALIDATION_TIMEOUT_MS);
        poolLeakDetection = longProp(clientInfo, "pool.leakDetectionThreshold", DEFAULT_LEAK_DETECTION_MS);
        // 显式配置为空串可关闭连接校验
        String testQuery = clientInfo.getProperty("pool.connectionTestQuery");
        if (testQuery != null) {
            poolConnectionTestQuery = testQuery.trim();
        }

        // zookeeper 开启 ssl 时需要设置 JVM 参数（hiveclient.properties 中 zk.ssl.enable=true 时生效）
        LoginUtil.processZkSsl(clientInfo);
    }

    private int intProp(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Hive 连接池参数 {} 不是合法整数，使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    private long longProp(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Hive 连接池参数 {} 不是合法整数，使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    private String buildJdbcUrl() {
        StringBuilder strBuilder = new StringBuilder("jdbc:hive2://").append(zkQuorum).append("/");

        if ("KERBEROS".equalsIgnoreCase(auth)) {
            strBuilder
                    .append(";serviceDiscoveryMode=").append(serviceDiscoveryMode)
                    .append(";zooKeeperNamespace=").append(zooKeeperNamespace)
                    .append(";sasl.qop=").append(saslQop)
                    .append(";auth=").append(auth)
                    .append(";principal=").append(principal)
                    .append(";ssl=").append(ssl);

            if (!hiveBasicAuthEnable) {
                strBuilder.append(";user.principal=").append(userName)
                        .append(";user.keytab=").append(userKeytabFile);
            }
        } else {
            strBuilder
                    .append(";serviceDiscoveryMode=").append(serviceDiscoveryMode)
                    .append(";zooKeeperNamespace=").append(zooKeeperNamespace)
                    .append(";auth=none");
        }
        if (auditAddition != null && !auditAddition.isEmpty()) {
            strBuilder.append(";auditAddition=").append(auditAddition);
        }
        return strBuilder.toString();
    }

    private void execDDL(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private void execDML(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            ResultSetMetaData resultMetaData = resultSet.getMetaData();
            int columnCount = resultMetaData.getColumnCount();
            StringBuilder resultMsg = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                resultMsg.append(resultMetaData.getColumnLabel(i)).append('\t');
            }
            log.info("Hive 查询结果头: {}", resultMsg);

            while (resultSet.next()) {
                StringBuilder result = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    result.append(resultSet.getString(i)).append('\t');
                }
                log.info("Hive 查询结果: {}", result);
            }
        }
    }

}
