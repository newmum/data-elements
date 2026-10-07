{
  "properties": {},
  "id": "datapd_database_detail_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "03.数据源详情",
  "createTime": null,
  "updateTime": 1787156037968,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"8b0a98de552b4bc68a203293fadfff33\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "数据源 03：读取 db_datasource_t 详情；使用请求参数 tid、silent。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据源详情
// 路由：POST /dst/database/detail
// 业务说明：数据源 03：读取 db_datasource_t 详情；使用请求参数 tid、silent。
import com.fasterxml.jackson.databind.ObjectMapper
import tenantRuntime
var tenantId = tenantRuntime.id()
import java.util.Map
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig

/*
 * 数据源 03：读取 db_datasource_t 详情。
 *
 * 连接参数统一从 db_datasource_t.pool_cfg 读取；主数据、描述、网域、统计字段从 db_datasource_t 读取。
 * showConnect 由 show_connect 派生，兼容前端旧字段。
 */

var blank = (value) => value == null || (value + '').trim() == ''
var first = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstText = (row, keys) => {
    var value = first(row, keys)
    return blank(value) ? null : value + ''
}
var inferDbType = (value, jdbcUrl) => {
    var raw = blank(value) ? '' : (value + '').toLowerCase()
    if (!blank(raw)) {
        return raw
    }
    var url = blank(jdbcUrl) ? '' : (jdbcUrl + '').toLowerCase()
    if (url.indexOf(':oracle:') >= 0) {
        return 'oracle'
    }
    if (url.indexOf(':mysql:') >= 0) {
        return 'mysql'
    }
    if (url.indexOf(':postgresql:') >= 0) {
        return 'postgresql'
    }
    if (url.indexOf(':dm:') >= 0) {
        return 'dameng'
    }
    if (url.indexOf(':kingbase') >= 0) {
        return 'kingbase8'
    }
    if (url.indexOf(':sqlserver:') >= 0) {
        return 'sqlserver'
    }
    return null
}
var databaseTypeName = (code) => {
    if (blank(code)) {
        return null
    }
    var value = (code + '').toLowerCase()
    var names = {
        mysql: 'MySQL',
        oracle: 'Oracle',
        kingbase8: 'Kingbase',
        kingbase: 'Kingbase',
        dm: 'Dameng',
        dameng: 'Dameng',
        postgres: 'PostgreSQL',
        postgresql: 'PostgreSQL',
        sqlserver: 'SQL Server',
        elasticsearch: 'Elasticsearch',
        es: 'Elasticsearch',
        hive: 'Hive',
        maxcompute: 'MaxCompute',
        vertica: 'Vertica',
        gbase8a: 'GBase 8a'
    }
    return names[value] == null ? code + '' : names[value]
}
// 解析请求体并为可选参数设置默认值。
var tid = body == null ? null : body.tid
var silent = body != null && body.silent == true
if (blank(tid)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tid不能为空'
}
// 从 MySQL 业务表读取当前有效数据。
var main = db.table('db_datasource_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
if (main == null) {
    if (silent) {
        return { found: false, tid: tid, id: tid }
    }
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '数据源不存在'
}
var result = {}
result.found = true
var connectionConfig = datasourceConnectionConfig('read', tid, null, false)
var hasConnectionConfig = connectionConfig != null && !connectionConfig.isEmpty()
// show_connect 表示是否提供连接；实际参数由 da_prop_t 的 connection.* 属性统一承载。
var showConnectValue = first(main, ['showConnect', 'show_connect'])
var hasConnection = showConnectValue == 1 || showConnectValue == '1'
if (hasConnectionConfig) {
    result.putAll(connectionConfig)
}
result.tid = tid
result.id = tid
result.assetType = 'db'
result.dbName = first(main, ['dbName', 'db_name'])
result.database = blank(result.database) ? result.dbName : result.database
result.jdbcURL = first(main, ['jdbcUrl', 'jdbc_url'])
result.dbType = inferDbType(first(main, ['dbType', 'db_type']), result.jdbcURL || first(result, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
result.db_type = result.dbType
result.databaseType = result.dbType
result.username = first(main, ['username'])
result.password = first(main, ['password'])
result.appId = first(main, ['appId', 'app_id'])
result.orgId = first(main, ['orgId', 'org_id'])
result.storageDomain = first(main, ['storageDomain', 'storage_domain'])
result.SSWL = result.storageDomain
var storedNodeId = first(main, ['nodeId', 'node_id'])
result.nodeId = storedNodeId
result.dbVersion = first(main, ['dbVersion', 'db_version'])
result.userCount = first(main, ['userCount', 'user_count'])
result.viewCount = first(main, ['viewCount', 'view_count'])
result.procedureCount = first(main, ['procedureCount', 'procedure_count'])
result.dataSize = first(main, ['dataSize', 'data_size'])
result.totalSize = first(main, ['totalSize', 'total_size'])
result.contactName = first(connectionConfig, ['contactName', 'contact_name'])
result.contactPhone = first(connectionConfig, ['contactPhone', 'contact_phone'])
result.showConnect = hasConnection ? 1 : 0
result.assetDesc = first(main, ['remark'])
result.tableCount = first(main, ['tableNum', 'table_num'])
result.tenantId = first(main, ['tenantId', 'tenant_id'])
result.createdTime = first(main, ['createdTime', 'created_time'])
result.updatedTime = first(main, ['updatedTime', 'updated_time'])
result.regTime = first(main, ['regTime', 'reg_time'])
result.assetStatus = first(main, ['assetStatus', 'asset_status'])
result.flowStatus = first(main, ['flowStatus', 'flow_status'])
// 登记与连通状态是库详情动态表单的基础字段：登记状态允许在编辑态调整，
// 连通状态仅反映最近一次连通性检测结果，保持只读。
result.connectionStatus = first(main, ['connectionStatus', 'connection_status'])
// “数据组织”是库详情动态表单属性，读取与保存统一使用 da_prop_t。
// dataSourceType 已由上方连接扩展属性直接回填。
if (!blank(result.appId)) {
    var app = db.table('sym_application_t').where().eq('tid', result.appId).eq('is_del', 0).selectOne()
    result.appName = firstText(app, ['appName', 'app_name', 'applicationName', 'application_name', 'name'])
    result.applicationName = result.appName
    result.systemName = result.appName
}
if (!blank(storedNodeId)) {
    var node = db.table('sym_dict_t').where().eq('tid', storedNodeId).eq('is_del', 0).selectOne()
    // dict-select 的 option.value 是 dict_code；数据库仍保存字典 tid 以维持关联完整性。
    result.nodeId = firstText(node, ['dictCode', 'dict_code']) || storedNodeId
    result.nodeName = firstText(node, ['dictName', 'dict_name', 'name'])
    result.nodeTypeName = result.nodeName
}
result.dbTypeName = databaseTypeName(result.dbType)
// 返回当前业务分支的标准结果。
return result
