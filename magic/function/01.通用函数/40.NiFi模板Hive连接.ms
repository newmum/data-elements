{
  "properties": {},
  "id": "nifi_canvas_template_hive_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "40.NiFi模板Hive连接",
  "path": "/nifiTemplateHiveConnection",
  "description": "公共模板连接投影。以已校验的来源或目标配置生成 JDBC 地址及 MRS Hive 选项，服务器管理模式只保留配置引用。",
  "parameters": [
    {
      "name": "datasource",
      "description": "已校验的数据源",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "config",
      "description": "已解析类型与主机等信息",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "rawType",
      "description": "登记的方言名称",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "isSink",
      "description": "目标写入节点处理显式地址优先级",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType
import java.lang.StringBuilder

// @/common/nifiTemplateHiveConnection 供来源和目标模板共享 JDBC/MRS Hive 配置。
// 本函数只组装已授权连接的参数，不读取数据库或凭据文件。
var first = (row, keys) => {
    for (key in keys) {
        var value = row[key]
        if (value != null && !(value + '').isBlank()) {
            return value + ''
        }
    }
    return null
}
var result = {
    hiveProfile: first(datasource, ['hiveProfile', 'hive_profile']),
    metadataAccessMode: first(datasource, ['metadataAccessMode', 'metadata_access_mode']),
    hiveConnectionMode: first(datasource, ['hiveConnectionMode', 'hive_connection_mode'])
}
var managed = !'open-source'.equalsIgnoreCase(result.hiveConnectionMode)
    && (result.hiveProfile != null || 'server-managed-mrs'.equalsIgnoreCase(result.metadataAccessMode)
        || 'huawei-mrs'.equalsIgnoreCase(result.hiveConnectionMode))
if (managed) {
    // MRS 服务端配置由登记引用解析，地址与 Kerberos 私有参数不回传到画布。
    return result
}
var aliases = {
    transportMode: ['transportMode'], httpPath: ['httpPath'], authMode: ['authMode', 'auth_mode', 'auth'],
    zookeeperQuorum: ['zookeeperQuorum', 'zookeeper_quorum', 'zkQuorum', 'zk_quorum'],
    serviceDiscoveryMode: ['serviceDiscoveryMode', 'service_discovery_mode'],
    zookeeperNamespace: ['zookeeperNamespace', 'zooKeeperNamespace', 'zookeeper_namespace'],
    principal: ['principal', 'hivePrincipal', 'servicePrincipal'],
    userPrincipal: ['userPrincipal', 'clientPrincipal', 'kerberosUserPrincipal'],
    keytabPath: ['keytabPath', 'keytab', 'userKeytab'], krb5ConfPath: ['krb5ConfPath', 'krb5Conf', 'krb5'],
    clientConfigDir: ['clientConfigDir', 'mrsClientConfigDir'], saslQop: ['saslQop', 'sasl.qop'],
    ssl: ['ssl'], driverLocations: ['driverLocations', 'nifiDriverLocations'], extraParams: ['extraParams']
}
for (key in aliases.keySet()) {
    var value = first(datasource, aliases[key])
    if (value != null) {
        result[key] = value
    }
}
var hive = 'HIVE'.equalsIgnoreCase(config.dbType)
var explicit = first(datasource, ['jdbcUrl', 'jdbcURL', 'jdbc_url'])
if (!isSink || !hive || explicit != null) {
    result.jdbcUrl = isSink && explicit != null ? explicit : RegisteredDatasourceType.jdbcUrl(rawType, datasource, {})
}
if (hive && result.jdbcUrl == null) {
    var quorum = result.zookeeperQuorum
    var host = config.host
    if (quorum != null || host != null) {
        var endpoint = quorum == null ? host + ':' + (config.port == null ? 10000 : config.port) : quorum
        var address = new StringBuilder('jdbc:hive2://').append(endpoint).append('/').append(config.database == null ? 'default' : config.database)
        var append = (key, value) => {
            if (value != null && !(value + '').isBlank()) {
                address.append(';').append(key).append('=').append(value.trim())
            }
        }
        if (quorum != null) {
            append('serviceDiscoveryMode', result.serviceDiscoveryMode == null ? 'zooKeeper' : result.serviceDiscoveryMode)
            append('zooKeeperNamespace', result.zookeeperNamespace == null ? 'hiveserver2' : result.zookeeperNamespace)
        }
        if ('KERBEROS'.equalsIgnoreCase(result.authMode)) {
            append('auth', 'KERBEROS')
            append('sasl.qop', result.saslQop)
            append('principal', result.principal)
            append('ssl', result.ssl)
            append('user.principal', result.userPrincipal)
            append('user.keytab', result.keytabPath)
        } else if (quorum != null) {
            append('auth', 'none')
        }
        if (result.extraParams != null) {
            var extra = result.extraParams
            address.append(extra.startsWith(';') ? extra : ';' + extra.replace('&', ';'))
        }
        result.jdbcUrl = address.toString()
    }
}
return result
