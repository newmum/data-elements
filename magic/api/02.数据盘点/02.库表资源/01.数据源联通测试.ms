{
  "properties": {},
  "id": "5c1e80431c9b440881fcce698f0b8634",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "01.数据源联通测试",
  "createTime": 1782955344253,
  "updateTime": 1787156109406,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/srctest",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "requestBody": "{\n    \"tid\": \"validation_oracle_metadata\",\n    \"dbType\": \"oracle\",\n    \"host\": \"192.168.175.86\",\n    \"port\": 1521,\n    \"database\": \"helowin\",\n    \"username\": \"system\",\n    \"password\": \"******\",\n    \"jdbcURL\": \"jdbc:oracle:thin:@192.168.175.86:1521:helowin\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前行业读取已登记的数据源或测试未保存的连接参数。按协议选择连通性探查，已登记的数据源记录测试结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import '@/common/nifiConnectionHealth' as probeHealth
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorer
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredSourceProbe
import com.linewell.dataelement.shared.error.ApiErrorDetails
import com.linewell.dataelement.shared.error.ApiErrorResolver
import com.linewell.dataelement.utils.MinioUtils
import java.lang.Boolean
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Integer
import java.lang.NumberFormatException
import java.lang.System
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.LinkedHashMap
import java.util.Map
import org.slf4j.LoggerFactory
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tenantRuntime

// 数据源登记页测试未保存或已登记的连接。按协议探查并保留诊断，只记录当前行业内登记源的测试状态。

var blank = (value) => value == null || (value + '').trim() == ''
var failure = (message) => ({
    connected: false,
    success: false,
    product: '数据源',
    errorCode: 'CONNECTION-TEST-001',
    error: message,
    elapsedMs: 0
})

if (body == null) {
    return failure('请求参数不能为空')
}

/*
 * 已登记数据源以主表和 da_prop_t 连接扩展属性为唯一可靠配置来源。登记页动态表单会提交
 * 未填字段的空串，空串不能覆盖已保存的连接参数，否则会把一次测试变成假失败。
 */
var source = {}
var tenantId = tenantRuntime.id()
var persistedTid = null
var requestTid = body.tid
if (!blank(requestTid)) {
    var main = db.table('db_datasource_t')
        .where()
        .eq('tid', requestTid)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .selectOne()
    if (main == null) {
        return failure('数据源不存在或已删除，请返回第一步重新选择数据源')
    }

    persistedTid = main.tid
    source.putAll(datasourceConnectionConfig('read', main.tid, null, false))

    // 主表字段补齐连接扩展属性，保持和详情/探查同一口径。
    source.tid = main.tid
    if (!blank(main.dbType)) {
        source.dbType = main.dbType
    }
    if (!blank(main.databaseType)) {
        source.databaseType = main.databaseType
    }
    if (!blank(main.host)) {
        source.host = main.host
    }
    if (!blank(main.port)) {
        source.port = main.port
    }
    if (!blank(main.database)) {
        source.database = main.database
    }
    if (!blank(main.dbName)) {
        source.dbName = main.dbName
    }
    if (!blank(main.jdbcUrl)) {
        source.jdbcURL = main.jdbcUrl
    }
    if (!blank(main.username)) {
        source.username = main.username
    }
    if (!blank(main.password)) {
        source.password = main.password
    }
    if (!blank(main.appId)) {
        source.appId = main.appId
    }
    if (!blank(main.orgId)) {
        source.orgId = main.orgId
    }
    if (!blank(main.showConnect)) {
        source.showConnect = main.showConnect
    }
}

// 仅使用前端传入的非空值覆盖配置，支持尚未保存的新数据源和用户刚修改的参数。
var overrideKeys = [
    'dbType', 'databaseType', 'dataSourceType', 'dbName', 'database', 'host', 'dbIp',
    'port', 'dbPort', 'username', 'password', 'jdbcURL', 'jdbcUrl', 'schema',
    'gaussdbVariant', 'hiveConnectionMode', 'hiveProfile', 'metadataAccessMode', 'transportMode', 'httpPath',
    'authMode', 'principal', 'userPrincipal', 'keytabPath', 'krb5ConfPath', 'ssl', 'ftpsImplicit',
    'trustStorePath', 'trustStorePassword', 'nifiTrustStorePath',
    'defaultSchema', 'currentSchema', 'dbVersion', 'jdbcType', 'serviceName',
    'dbMetaType', 'dbMetaDbName', 'dbMetaIp', 'dbMetaPort', 'dbMetaUser', 'dbMetaPassword',
    'maxcomputeEndpoint', 'maxcomputeProject', 'maxcomputeAccessKeyId',
    'maxcomputeAccessKeySecret', 'maxcomputeTunnelEndpoint', 'minioEndpoint',
    'minioAccessKey', 'minioSecretKey', 'minioBucket', 'minioRegion', 'minioUseSSL',
    'minioPrefix', 'minioFilePattern', 'minioSingleFile',
    'ftpProtocol', 'ftpHost', 'ftpPort', 'ftpPath', 'ftpUsername', 'ftpPassword',
    'ftpPassiveMode', 'ftpFilePattern', 'ftpRecursive', 'ftpCharset', 'ftpDelimiter',
    'apiMethod', 'apiUrl', 'apiAuthType', 'apiUsername', 'apiPassword', 'apiToken',
    'apiProtocol', 'apiWsdlUrl', 'apiSoapVersion', 'apiSoapAction', 'apiProbeAction',
    'apiOAuthTokenUrl', 'apiOAuthClientId', 'apiOAuthClientSecret', 'apiOAuthScope',
    'apiKeyName', 'apiKeyValue', 'apiKeyPosition', 'apiHeaders', 'apiBody',
    'apiTimeoutSeconds', 'apiCollectionPaths', 'kafkaBootstrapServers', 'kafkaTopic',
    'kafkaGroupId', 'kafkaSecurityProtocol', 'kafkaSaslMechanism', 'kafkaUsername',
    'kafkaPassword', 'kafkaSchemaRegistryUrl', 'kafkaSampleSize', 'kafkaPollTimeoutMs',
    'nodes', 'protocol', 'authMethod', 'apiKey', 'manifestKey'
]
for (key in overrideKeys) {
    if (!blank(body[key])) {
        source[key] = body[key]
    }
}
if (blank(source.tid) && !blank(requestTid)) {
    source.tid = requestTid
}

var first = (values, keys) => {
    for (key in keys) {
        var value = values.get(key)
        var text = value == null ? null : (value + '').trim()
        if (text != null && !text.isBlank()) { return text }
    }
    return null
}
var fallback = (value, defaultValue) => value == null || value.isBlank() ? defaultValue : value
var setting = keys => first(source, keys)
var required = (message, keys) => {
    var value = setting(keys)
    if (value == null) { throw new IllegalArgumentException(message) }
    return value
}
var rootCause = failure => {
    while ((failure instanceof InvocationTargetException || failure instanceof MagicScriptException
        || failure instanceof MagicScriptRuntimeException) && failure.getCause() != null) {
        failure = failure.getCause()
    }
    return failure
}
var integer = value => {
    if (value == null) { return null }
    try {
        return Integer.valueOf(value)
    } catch (error) {
        if (!(rootCause(error) instanceof NumberFormatException)) { throw error }
        throw new IllegalArgumentException('端口或超时参数格式不正确: ' + value, rootCause(error))
    }
}
var flag = value => value == null ? null : Boolean.parseBoolean(value)

var sourceType = setting(['dbType', 'databaseType', 'dataSourceType'])
if ('oceanbase'.equalsIgnoreCase(sourceType)) {
    sourceType = 'ORACLE'.equalsIgnoreCase(setting(['compatibleMode', 'mode', 'databaseMode']))
        ? DatabaseType.OCEANBASE_ORACLE.getCode() : DatabaseType.OCEANBASE_MYSQL.getCode()
}
var product = '数据源'
if (sourceType != null) {
    try {
        var type = DatabaseType.fromCode(sourceType)
        sourceType = type.getCode()
        product = type.getDisplayName()
    } catch (error) {
        if (!(rootCause(error) instanceof IllegalArgumentException)) { throw error }
        sourceType = sourceType.toLowerCase()
        product = sourceType
    }
}

// 表单字段转为画布协议探针的配置。显式开源 Hive 继续走 JDBC，避免继承遗留托管配置。
var adapterRequest = () => {
    var config = {}
    var manifest = null
    if (sourceType == 'kafka') {
        manifest = 'source.kafka'
        config = {
            bootstrapServers: setting(['kafkaBootstrapServers', 'bootstrapServers']),
            clientId: setting(['kafkaClientId', 'clientId']),
            securityProtocol: fallback(setting(['kafkaSecurityProtocol', 'securityProtocol']), 'PLAINTEXT'),
            saslMechanism: fallback(setting(['kafkaSaslMechanism', 'saslMechanism']), 'PLAIN'),
            saslUsername: setting(['kafkaUsername', 'username']),
            saslPassword: setting(['kafkaPassword', 'password'])
        }
    } else if (sourceType == 'api') {
        manifest = 'source.api'
        var authHeader = setting(['authHeader', 'authorization'])
        var token = setting(['apiToken', 'token'])
        if (authHeader == null && token != null) {
            authHeader = token.startsWith('Bearer ') ? token : 'Bearer ' + token
        }
        config = {
            url: setting(['apiUrl', 'url']),
            method: fallback(setting(['apiMethod', 'method']), 'GET'),
            contentType: fallback(setting(['contentType']), 'application/json'),
            requestBody: setting(['apiBody', 'requestBody']),
            authHeader: authHeader
        }
    } else if (sourceType == 'ftp') {
        manifest = 'source.ftp'
        config = {
            protocol: fallback(setting(['ftpProtocol']), sourceType),
            hostname: setting(['ftpHost', 'hostname', 'host']),
            port: fallback(setting(['ftpPort', 'port']), '21'),
            username: setting(['ftpUsername', 'username']),
            password: setting(['ftpPassword', 'password']),
            remotePath: fallback(setting(['ftpPath', 'remotePath']), '/'),
            passiveMode: setting(['ftpPassiveMode', 'passiveMode'])
        }
    } else if (sourceType == 'elasticsearch') {
        manifest = 'source.elasticsearch'
        var protocol = fallback(setting(['protocol']), 'http')
        var endpoint = setting(['nodes', 'jdbcURL', 'jdbcUrl', 'apiUrl'])
        if (endpoint == null) {
            var host = setting(['host', 'dbIp'])
            endpoint = host != null && host.matches('(?i)^https?://.*') ? host
                : protocol + '://' + (host == null ? 'null' : host) + ':' + fallback(setting(['port', 'dbPort']), '9200')
        }
        var username = setting(['username'])
        var apiKey = setting(['apiKey'])
        config = {
            nodes: endpoint, protocol: protocol, username: username, apiKey: apiKey,
            authMethod: fallback(setting(['authMethod']), apiKey != null ? 'API_KEY' : (username != null ? 'BASIC' : 'NONE')),
            password: setting(['password'])
        }
    } else if (sourceType == 'hdfs') {
        manifest = 'source.hdfs'
        config = {
            host: setting(['hdfsHost', 'host', 'hostname', 'nameNodeHost']),
            port: fallback(setting(['hdfsPort', 'port']), '8020'),
            path: fallback(setting(['hdfsPath', 'path', 'remotePath']), '/')
        }
    } else if (sourceType == 'hbase') {
        manifest = 'source.hbase'
        config = {
            zookeeperQuorum: setting(['zookeeperQuorum', 'zkQuorum', 'host', 'hostname']),
            port: fallback(setting(['zookeeperPort', 'port']), '2181'),
            table: setting(['hbaseTable', 'table'])
        }
    } else if (sourceType == 'hive') {
        var profile = setting(['hiveProfile', 'hive_profile'])
        var accessMode = setting(['metadataAccessMode', 'metadata_access_mode'])
        var mode = setting(['hiveConnectionMode', 'hive_connection_mode'])
        if (!'open-source'.equalsIgnoreCase(mode) && (profile != null
            || 'server-managed-mrs'.equalsIgnoreCase(accessMode) || 'huawei-mrs'.equalsIgnoreCase(mode))) {
            manifest = 'source.hive'
            config = {
                hiveProfile: fallback(profile, 'default'), metadataAccessMode: 'server-managed-mrs',
                hiveConnectionMode: 'huawei-mrs', database: setting(['database', 'dbName', 'dbMetaDbName'])
            }
        }
    }
    if (manifest == null) { return null }
    var submitted = {}
    for (key in config.keySet()) {
        if (config[key] != null) { submitted.put(key, config[key]) }
    }
    return {manifestKey: manifest, config: submitted}
}

var metadataConfig = () => {
    if (sourceType == null) { throw new IllegalArgumentException('数据源类型不能为空') }
    var config = new DataSourceConfig()
    var type = DatabaseType.fromCode(sourceType)
    config.setDatabaseType(type)
    config.setConnectorProperties(new LinkedHashMap(source))
    var quorum = setting(['zookeeperQuorum', 'zkQuorum'])
    var host = setting(['host', 'dbIp'])
    if (host == null && (type != DatabaseType.HIVE || quorum == null)) {
        throw new IllegalArgumentException('关系型数据源主机地址不能为空')
    }
    // ZooKeeper 服务发现无需固定 HiveServer2 主机。
    config.setHost(fallback(host, ''))
    config.setPort(integer(setting(['port', 'dbPort'])))
    config.setDatabase(required('关系型数据源数据库名称不能为空',
        ['database', 'dbName', 'dbMetaDbName', 'schema', 'defaultSchema', 'currentSchema']))
    config.setUsername(fallback(setting(['username', 'dbUser', 'dbMetaUser', 'user']), ''))
    config.setPassword(fallback(setting(['password', 'dbPassword', 'dbMetaPassword']), ''))
    config.setJdbcUrl(setting(['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    config.setJdbcProperties(JdbcDriverPropertyResolver.resolve(source))
    config.setSchema(setting(['schema', 'defaultSchema', 'currentSchema']))
    config.setAuthMode(setting(['authMode', 'auth']))
    config.setPrincipal(setting(['principal', 'hivePrincipal', 'servicePrincipal']))
    config.setUserPrincipal(setting(['userPrincipal', 'clientPrincipal', 'kerberosUserPrincipal']))
    config.setKeytabPath(setting(['keytabPath', 'keytab', 'userKeytab']))
    config.setKrb5ConfPath(setting(['krb5ConfPath', 'krb5Conf', 'krb5']))
    config.setJaasConfPath(setting(['jaasConfPath', 'jaasConfig']))
    config.setZookeeperQuorum(quorum)
    config.setZookeeperNamespace(setting(['zookeeperNamespace', 'zooKeeperNamespace']))
    config.setServiceDiscoveryMode(setting(['serviceDiscoveryMode', 'serviceDiscovery']))
    config.setSaslQop(setting(['saslQop', 'sasl.qop']))
    config.setSsl(flag(setting(['ssl'])))
    config.setZookeeperSsl(flag(setting(['zookeeperSsl', 'zkSsl', 'zk.ssl.enable'])))
    config.setClientConfigDir(setting(['clientConfigDir', 'mrsClientConfigDir']))
    config.setExtraParams(setting(['extraParams', 'connectParams', 'jdbcParams']))
    config.setConnectionTimeout(integer(setting(['connectionTimeout'])))
    return config
}

var startedAt = System.currentTimeMillis()
var success = name => ({
    connected: true, success: true, product: name, elapsedMs: System.currentTimeMillis() - startedAt
})
var probe = input => {
    var response = probeHealth(input.manifestKey, input.config).getBody()
    if (!(response instanceof Map)) { throw new IllegalStateException('连接适配器未返回有效结果') }
    if (response.success != true) {
        throw new IllegalStateException(fallback(first(response, ['error']), '目标服务未通过连通性校验'))
    }
    var outcome = success(fallback(first(response, ['product']), product))
    for (key in ['url', 'version', 'elapsedMs']) {
        if (response[key] != null) { outcome[key] = response[key] }
    }
    return outcome
}
var testConnection = () => {
    if (source.isEmpty()) { throw new IllegalArgumentException('请求参数不能为空') }
    if (structuredSourceProbe.supports(source)) { return structuredSourceProbe.testAndProbe(source) }
    if (setting(['manifestKey']) != null) { return probe(source) }
    if (sourceType == 'minio') {
        var endpoint = required('MinIO 服务地址不能为空', ['minioEndpoint'])
        var accessKey = required('MinIO Access Key 不能为空', ['minioAccessKey'])
        var secretKey = required('MinIO Secret Key 不能为空', ['minioSecretKey'])
        var bucket = required('MinIO 存储桶不能为空', ['minioBucket'])
        MinioUtils.getMinioClient(endpoint, accessKey, secretKey)
        if (!MinioUtils.bucketExists(bucket)) {
            throw new IllegalStateException('存储桶不存在或当前凭证无访问权限')
        }
        var outcome = success('MinIO')
        outcome.version = '存储桶可访问'
        return outcome
    }
    if (sourceType == 'maxcompute') {
        var endpoint = required('MaxCompute 服务端点不能为空', ['maxcomputeEndpoint'])
        var uri = URI.create(endpoint.contains('://') ? endpoint : 'https://' + endpoint)
        if (blank(uri.getHost())) { throw new IllegalArgumentException('MaxCompute 服务端点格式不正确') }
        var port = uri.getPort() > 0 ? uri.getPort() : ('http'.equalsIgnoreCase(uri.getScheme()) ? 80 : 443)
        var socket = new Socket()
        try {
            socket.connect(new InetSocketAddress(uri.getHost(), port), 10000)
        } finally {
            socket.close()
        }
        var outcome = success('MaxCompute')
        outcome.version = '服务端点可达'
        return outcome
    }
    var adapter = adapterRequest()
    if (adapter != null) { return probe(adapter) }
    var config = metadataConfig()
    if (!metadataExplorer.testConnection(config)) { throw new IllegalStateException('目标服务未通过连通性校验') }
    return success(config.getDatabaseType().getDisplayName())
}

var result = null
try {
    result = testConnection()
} catch (error) {
    var cause = rootCause(error)
    var diagnostic = ApiErrorResolver.resolve(cause)
    if (diagnostic.code() == 'MAGIC-RUNTIME-001' && !blank(cause.getMessage())) {
        diagnostic = new ApiErrorDetails('CONNECTION-TEST-001', cause.getMessage(),
            diagnostic.detail(), diagnostic.traceId(), diagnostic.type())
    }
    LoggerFactory.getLogger('data.source.connection').error(
        'Datasource connection test failed, traceId={}, errorCode={}, product={}',
        diagnostic.traceId(), diagnostic.code(), product)
    result = {
        connected: false, success: false, product: product, elapsedMs: System.currentTimeMillis() - startedAt,
        error: diagnostic.message(), errorCode: diagnostic.code(), rawError: diagnostic.detail(),
        traceId: diagnostic.traceId(), errorDetail: diagnostic.toMap('POST', '/dst/db/srctest')
    }
}

// 状态记录不参与连接测试结果。写入或历史数据异常时保留测试结果，不能影响前端。
if (!blank(persistedTid) && result?.metadataOnly != true) {
    try {
        var status = result != null && result.connected == true ? 'success' : 'failed'
        db.table('db_datasource_t')
            .where()
            .eq('tid', persistedTid)
            .eq('tenant_id', tenantId)
            .eq('is_del', 0)
            .update({
                connection_status: status,
                updated_time: new Date()
            })
    } catch (ignored) {
    }
}

return result
