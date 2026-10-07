{
  "properties": {},
  "id": "5c1e80431c9b440881fcce698f0b8634",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "01.数据源联通测试",
  "createTime": 1782955344253,
  "updateTime": 1787156109406,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/test-connection",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"codex_oracle_helowin\",\n    \"dbType\": \"oracle\",\n    \"host\": \"192.168.175.86\",\n    \"port\": 1521,\n    \"database\": \"helowin\",\n    \"username\": \"system\",\n    \"password\": \"******\",\n    \"jdbcURL\": \"jdbc:oracle:thin:@192.168.175.86:1521:helowin\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据源联通测试（POST /dst/database/metadata/test-connection）。读取 db_datasource_t 中已保存的连接配置，调用 Java DataSourceConnectionTestService 完成协议适配、连接探测和统一异常诊断，并写入 db_datasource_t.connection_status；该接口是数据源登记页的主连通性测试端点；使用请求参数 tid。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.数据源联通测试
// 路由：POST /dst/database/metadata/test-connection
// 业务说明：数据源联通测试（POST /dst/database/metadata/test-connection）。读取 db_datasource_t 中已保存的连接配置，调用 Java DataSourceConnectionTestService 完成协议适配、连接探测和统一异常诊断，并写入 db_datasource_t.connection_status；该接口是数据源登记页的主连通性测试端点；使用请求参数 tid。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import com.linewell.dataelement.metautil.service.DataSourceConnectionTestService as dataSourceConnectionTestService

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
var persistedTid = null
var requestTid = body.tid
if (!blank(requestTid)) {
    var main = db.table('db_datasource_t')
        .where()
        .eq('tid', requestTid)
        .eq('is_del', 0)
        .selectOne()
    if (main == null) {
        return failure('数据源不存在或已删除，请返回第一步重新选择数据源')
    }

    persistedTid = main.tid    source.putAll(datasourceConnectionConfig('read', main.tid, null, false))

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
    'defaultSchema', 'currentSchema', 'dbVersion', 'jdbcType', 'serviceName',
    'dbMetaType', 'dbMetaDbName', 'dbMetaIp', 'dbMetaPort', 'dbMetaUser', 'dbMetaPassword',
    'maxcomputeEndpoint', 'maxcomputeProject', 'maxcomputeAccessKeyId',
    'maxcomputeAccessKeySecret', 'maxcomputeTunnelEndpoint', 'minioEndpoint',
    'minioAccessKey', 'minioSecretKey', 'minioBucket', 'minioRegion', 'minioUseSSL',
    'minioPrefix', 'minioFilePattern', 'minioSingleFile',
    'ftpProtocol', 'ftpHost', 'ftpPort', 'ftpPath', 'ftpUsername', 'ftpPassword',
    'ftpPassiveMode', 'ftpFilePattern', 'ftpRecursive', 'ftpCharset', 'ftpDelimiter',
    'apiMethod', 'apiUrl', 'apiAuthType', 'apiUsername', 'apiPassword', 'apiToken',
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

var result = null
try {
    result = dataSourceConnectionTestService.test(source)
} catch (error) {
    // Java 服务已处理大多数协议异常；这里兜住脚本边界异常，绝不向登记页泄漏 HTTP 500。
    result = failure('连接测试失败：' + (blank(error?.message) ? '请检查连接参数和网络可达性' : error.message))
}

// 状态记录不参与连接测试结果。写入或历史数据异常时保留测试结果，不能影响前端。
if (!blank(persistedTid)) {
    try {
        var status = result != null && result.connected == true ? 'success' : 'failed'
        db.table('db_datasource_t')
            .where()
            .eq('tid', persistedTid)
            .eq('is_del', 0)
            .update({
                connection_status: status,
                updated_time: new Date()
            })
    } catch (ignored) {
    }
}

return result
