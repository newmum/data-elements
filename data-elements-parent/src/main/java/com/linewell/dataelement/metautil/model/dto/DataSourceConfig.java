package com.linewell.dataelement.metautil.model.dto;

import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 数据源连接配置
 * 
 * @author MetaUtil
 */
@Data
public class DataSourceConfig {

    @NotNull(message = "数据库类型不能为空")
    /**
     * 数据库类型
     */
    private DatabaseType databaseType;

    @NotBlank(message = "主机地址不能为空")
    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    @NotBlank(message = "数据库名称不能为空")
    /**
     * 数据库名称
     */
    private String database;

    /**
     * Schema名称（PostgreSQL/Oracle等）
     */
    private String schema;

    @NotBlank(message = "用户名不能为空")
    /**
     * 用户名
     */
    private String username;

    @NotBlank(message = "密码不能为空")
    /**
     * 密码
     */
    private String password;

    /**
     * 额外连接参数 useSSL=false&serverTimezone=GMT%2B8
     */
    private String extraParams;

    /**
     * Vendor JDBC driver properties, such as Oracle Native Network Encryption settings.
     * These are passed as {@link java.util.Properties}, not appended to a JDBC URL.
     */
    private Map<String, String> jdbcProperties = new LinkedHashMap<>();

    /**
     * Connector-specific settings used by non-JDBC metadata explorers.
     *
     * <p>FTP/SFTP, object storage and similar sources require discovery options
     * such as the remote path, filename pattern and character encoding.  These
     * values are kept with the transient exploration request rather than being
     * squeezed into the relational connection fields.</p>
     */
    private Map<String, Object> connectorProperties = new LinkedHashMap<>();

    /** Huawei MRS security mode, for example SIMPLE, KERBEROS or BASIC. */
    private String authMode;

    /** Kerberos or component service principal, such as hive/hadoop.hadoop.com@HADOOP.COM. */
    private String principal;

    /** Client principal used with keytab, such as user@HADOOP.COM. */
    private String userPrincipal;

    /** Absolute path of user.keytab downloaded from the Huawei MRS client. */
    private String keytabPath;

    /** Absolute path of krb5.conf downloaded from the Huawei MRS client. */
    private String krb5ConfPath;

    /** Absolute JAAS config path used by Kafka, HBase and ZooKeeper clients. */
    private String jaasConfPath;

    /** ZooKeeper quorum in host:port,host:port format for HiveServer HA or HBase. */
    private String zookeeperQuorum;

    /** ZooKeeper namespace, for example hiveserver2. */
    private String zookeeperNamespace;

    /** Hive/Hetu service discovery mode, for example zooKeeper or hsbroker. */
    private String serviceDiscoveryMode;

    /** SASL quality of protection, for example auth-conf. */
    private String saslQop;

    /** Whether SSL is enabled for the component. */
    private Boolean ssl;

    /** Whether the Huawei MRS ZooKeeper client uses TLS. */
    private Boolean zookeeperSsl;

    /** Truststore path used by IoTDB, ClickHouse or component SSL. */
    private String trustStorePath;

    /** Truststore password when the target driver requires it. */
    private String trustStorePassword;

    /** Client config directory, normally the conf directory from the MRS client package. */
    private String clientConfigDir;

    /**
     * Complete JDBC URL explicitly configured by the user.
     *
     * <p>Oracle supports both SID ({@code @host:port:SID}) and Service Name
     * ({@code @//host:port/service}) formats. Host, port and database alone
     * cannot reliably reconstruct the selected mode, so metadata exploration
     * must reuse the URL that already passed the connection test.</p>
     */
    private String jdbcUrl;

    /**
     * 连接超时时间(秒)
     */
    private Integer connectionTimeout;

    /**
     * 获取端口，如果未设置则返回默认端口
     */
    public int getPortOrDefault() {
        return port != null ? port : databaseType.getDefaultPort();
    }

    /**
     * 构建JDBC URL
     */
    public String buildJdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.trim().isEmpty()) {
            return jdbcUrl.trim();
        }

        StringBuilder url = new StringBuilder();
        
        switch (databaseType) {
            case MYSQL:
                url.append("jdbc:mysql://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true");
                }
                break;

            case MARIADB:
                url.append("jdbc:mariadb://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
                
            case ORACLE:
                url.append("jdbc:oracle:thin:@").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append(":").append(database);
                }
                break;
                
            case POSTGRESQL:
                url.append("jdbc:postgresql://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case DB2:
                url.append("jdbc:db2://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (schema != null && !schema.isEmpty()) {
                    url.append(":currentSchema=").append(schema).append(";");
                }
                break;

            case SQLSERVER:
                url.append("jdbc:sqlserver://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append(";databaseName=").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append(extraParams.startsWith(";") ? extraParams : ";" + extraParams.replace("&", ";"));
                } else {
                    url.append(";encrypt=false;trustServerCertificate=true;loginTimeout=30;socketTimeout=60000");
                }
                break;

            case GBASE8A:
                url.append("jdbc:gbase://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?characterEncoding=utf8");
                }
                break;

            case GBASE8S:
                url.append("jdbc:gbasedbt-sqli://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append(":").append(extraParams);
                }
                break;

            case VERTICA:
                url.append("jdbc:vertica://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case GAUSSDB:
                // GaussDB（openGauss / PG 兼容）一般使用 PostgreSQL JDBC 协议
                url.append("jdbc:postgresql://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
                
            case KINGBASE:
                url.append("jdbc:kingbase8://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case OSCAR:
                url.append("jdbc:oscar://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case HIGHGO:
                url.append("jdbc:highgo://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                String highgoParams = extraParams;
                if (schema != null && !schema.isEmpty()
                        && (highgoParams == null || !highgoParams.contains("currentSchema="))) {
                    highgoParams = highgoParams == null || highgoParams.isEmpty()
                            ? "currentSchema=" + schema
                            : highgoParams + "&currentSchema=" + schema;
                }
                if (highgoParams != null && !highgoParams.isEmpty()) {
                    url.append("?").append(highgoParams);
                }
                break;
                
            case HIVE:
                url.append("jdbc:hive2://");
                if (zookeeperQuorum != null && !zookeeperQuorum.isBlank()) {
                    url.append(zookeeperQuorum.trim());
                } else {
                    url.append(host).append(":").append(getPortOrDefault());
                }
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                appendHiveParams(url);
                break;

            case HETU:
                url.append("jdbc:trino://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case DORIS:
            case STARROCKS:
                url.append("jdbc:mysql://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&useSSL=false");
                }
                break;

            case CLICKHOUSE:
                url.append("jdbc:clickhouse://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?compress=1");
                }
                break;

            case IOTDB:
                url.append("jdbc:iotdb://").append(host).append(":").append(getPortOrDefault()).append("/");
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case HDFS:
                if (host != null && (host.startsWith("hdfs://") || host.startsWith("viewfs://"))) {
                    url.append(host);
                } else {
                    url.append("hdfs://").append(host).append(":").append(getPortOrDefault());
                }
                break;

            case MAXCOMPUTE:
                if (extraParams != null && extraParams.startsWith("jdbc:")) {
                    url.append(extraParams);
                } else {
                    url.append("jdbc:odps:");
                    if (database != null && !database.isEmpty()) {
                        url.append(database);
                    }
                    if (extraParams != null && !extraParams.isEmpty()) {
                        url.append("?").append(extraParams);
                    } else if (host != null && !host.isEmpty()) {
                        url.append("?endpoint=").append(host);
                    }
                }
                break;

            case OCEANBASE_MYSQL:
                url.append("jdbc:mysql://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true");
                }
                break;

            case OCEANBASE_ORACLE:
                url.append("jdbc:oceanbase:oracle://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case DAMENG:
                // 达梦 DM8 JDBC URL: jdbc:dm://host:port/database?param=value&...
                url.append("jdbc:dm://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                String dmParams = extraParams;
                if (schema != null && !schema.isEmpty()) {
                    // 若未显式指定 schema，则自动补上（达梦连接串参数通常为 schema=xxx）
                    if (dmParams == null || dmParams.isEmpty()) {
                        dmParams = "schema=" + schema;
                    } else if (!dmParams.toUpperCase().contains("SCHEMA=")) {
                        dmParams = dmParams + "&schema=" + schema;
                    }
                }
                if (dmParams != null && !dmParams.isEmpty()) {
                    url.append("?").append(dmParams);
                }
                break;
                
            case MONGODB:
                url.append("mongodb://").append(host).append(":").append(getPortOrDefault());
                if (database != null && !database.isEmpty()) {
                    url.append("/").append(database);
                }
                break;

            case ELASTICSEARCH:
                String protocol = "http";
                if (extraParams != null && !extraParams.isBlank()) {
                    for (String item : extraParams.replaceFirst("^\\?", "").split("&")) {
                        if (item.startsWith("protocol=")) {
                            protocol = item.substring("protocol=".length());
                        } else if (item.startsWith("scheme=")) {
                            protocol = item.substring("scheme=".length());
                        }
                    }
                }
                if (host != null && (host.startsWith("http://") || host.startsWith("https://"))) {
                    url.append(host);
                } else {
                    url.append(protocol).append("://").append(host).append(":").append(getPortOrDefault());
                }
                break;
                
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + databaseType);
        }
        
        return url.toString();
    }

    private void appendHiveParams(StringBuilder url) {
        boolean kerberos = "KERBEROS".equalsIgnoreCase(authMode);
        StringBuilder params = new StringBuilder();
        if (serviceDiscoveryMode != null && !serviceDiscoveryMode.isBlank()) {
            params.append(";serviceDiscoveryMode=").append(serviceDiscoveryMode.trim());
        } else if (zookeeperQuorum != null && !zookeeperQuorum.isBlank()) {
            params.append(";serviceDiscoveryMode=zooKeeper");
        }
        if (zookeeperNamespace != null && !zookeeperNamespace.isBlank()) {
            params.append(";zooKeeperNamespace=").append(zookeeperNamespace.trim());
        } else if (zookeeperQuorum != null && !zookeeperQuorum.isBlank()) {
            params.append(";zooKeeperNamespace=hiveserver2");
        }
        if (kerberos) {
            params.append(";auth=KERBEROS");
            if (saslQop != null && !saslQop.isBlank()) {
                params.append(";sasl.qop=").append(saslQop.trim());
            }
            if (principal != null && !principal.isBlank()) {
                params.append(";principal=").append(principal.trim());
            }
            if (ssl != null) {
                params.append(";ssl=").append(ssl);
            }
            if (userPrincipal != null && !userPrincipal.isBlank()) {
                params.append(";user.principal=").append(userPrincipal.trim());
            }
            if (keytabPath != null && !keytabPath.isBlank()) {
                params.append(";user.keytab=").append(keytabPath.trim());
            }
        } else if (zookeeperQuorum != null && !zookeeperQuorum.isBlank()) {
            params.append(";auth=none");
        }
        if (extraParams != null && !extraParams.isEmpty()) {
            params.append(extraParams.startsWith(";") ? extraParams : ";" + extraParams.replace("&", ";"));
        }
        url.append(params);
    }
}
