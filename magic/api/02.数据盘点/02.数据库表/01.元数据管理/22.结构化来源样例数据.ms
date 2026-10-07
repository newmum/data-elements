{
  "properties": {},
  "id": "metadata_structured_sample_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "22.结构化来源样例数据",
  "createTime": 1785686400000,
  "updateTime": 1788343414621,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/structuredSampleData",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"dbId\": \"数据源ID\",\n  \"tableName\": \"探查得到的数据集名称\",\n  \"sampleSize\": 10\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取 API、FTP/SFTP、Kafka 数据源配置，并返回选定数据集的标准样例行；使用请求参数 dbId、tableName、sampleSize；涉及 db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：22.结构化来源样例数据
// 路由：POST /dst/database/metadata/structuredSampleData
// 业务说明：读取 API、FTP/SFTP、Kafka 数据源配置，并返回选定数据集的标准样例行；使用请求参数 dbId、tableName、sampleSize；涉及 db_datasource_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import tenantRuntime
import java.util.Map
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredSourceProbeService

/*
 * 元数据管理 22：读取结构化来源样例数据。
 *
 * 正常可连接来源继续实时读取样例数据；数据上报方式无探查连接，实际业务数据
 * 尚未在登记阶段推送，安全返回空样例而不伪造数据或调用带空 URL 的探查服务。
 * 接口全程只读，不写 MySQL、ES 或物理数据源。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => blank(value) ? '' : textOf(value).trim().toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            return value
        }
    }
    return null
}
var falseLike = (value) => {
    var normalized = lower(value)
    return normalized == '0' || normalized == 'false' || normalized == 'no' || normalized == 'off'
}
var loadSource = (datasourceId, tenantId) => {
    var row = db.table('db_datasource_t').where()
        .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (row == null) {
        return null
    }
    var result = {
        tid: rowValue(row, ['tid']),
        dbType: rowValue(row, ['dbType', 'db_type']),
        dbName: rowValue(row, ['dbName', 'db_name']),
        username: rowValue(row, ['username']),
        password: rowValue(row, ['password']),
        showConnect: rowValue(row, ['showConnect', 'show_connect']),
        accessMode: rowValue(row, ['accessMode', 'access_mode'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(row, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) {
        result.putAll(connectionConfig)
    }
    result.dbType = rowValue(row, ['dbType', 'db_type'])
    result.showConnect = rowValue(row, ['showConnect', 'show_connect'])
    return result
}
var isDataUploadSource = (source) => {
    var accessMode = lower(firstValue(source, ['accessMode', 'access_mode']))
    return accessMode == 'upload' || accessMode == 'data-upload' || falseLike(firstValue(source, ['showConnect', 'show_connect']))
}
var hasProbeEndpoint = (source) => !blank(firstValue(source, [
    'url', 'apiUrl', 'apiBaseUrl', 'baseUrl', 'endpoint', 'host', 'jdbcURL', 'jdbcUrl', 'jdbc_url',
    'ftpHost', 'nodes', 'bootstrapServers', 'kafkaBootstrapServers'
]))

var dbId = body == null ? null : body.dbId
var tableName = body == null ? null : body.tableName
if (blank(dbId) || blank(tableName)) {
    exit 400, 'dbId 和 tableName 不能为空'
}
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '租户会话已失效，请重新登录'
}
var source = loadSource(dbId, tenantId)
if (source == null) {
    exit 404, '数据源不存在'
}

// 数据上报的登记模板只提供表结构；实际行数据待完成审查后推送，预览显示为空即可。
if (isDataUploadSource(source) || !hasProbeEndpoint(source)) {
    return []
}
return structuredSourceProbeService.sampleData(source, textOf(tableName), body.sampleSize)
