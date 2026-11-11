{
  "properties": {},
  "id": "ods_api_pull_sync_01",
  "script": null,
  "groupId": "ods_api_pull_group_01",
  "name": "01.同步第一步API定义",
  "path": "/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "API 拉取登记、表规则与 NiFi 任务绑定；不保存明文凭据；使用请求参数 datasourceId。"
}
================================
import tenantRuntime
import apiPullConfig
import cn.hutool.core.util.IdUtil
import java.time.LocalDateTime

// POST /ods/api-pull/sync。按当前租户及 API 数据源一次读取现有服务、逻辑表和拉取规则，批量同步草稿。
var tenantId = tenantRuntime.id()
var datasourceId = body?.datasourceId == null ? '' : ('' + body.datasourceId).trim()
if (datasourceId == '') { exit 400, '数据源标识不能为空' }
var definitions = apiPullConfig.definitions(datasourceId)
if (definitions.size() > 100) { exit 400, '一个数据源最多登记 100 条 API' }
if (definitions.isEmpty()) { return {datasourceId:datasourceId,definitions:0,services:0,rules:0,entries:[]} }
try { apiPullConfig.validateDefinitions(definitions) }
catch (e) { exit 400, e.message }
var text = value => value == null ? '' : ('' + value).trim()
var names = []
var codes = []
var identities = []
for (item in definitions) {
    var tableName = text(item.tableName)
    var serviceName = text(item.serviceName)
    if (!tableName.matches('[A-Za-z][A-Za-z0-9_]{0,95}') || serviceName == ''
        || text(item.tableComment) == '' || text(item.baseUrl) == '' || text(item.endpointPath) == '') {
        exit 400, '请填写 API 逻辑表、名称、地址和请求路径'
    }
    var tableKey = tableName.toLowerCase()
    if (names.contains(tableKey)) { exit 400, 'API 逻辑表英文名重复：' + tableName }
    names.add(tableKey)
    var code = apiPullConfig.suggestedServiceCode(tableName, serviceName)
    if (codes.contains(code)) { exit 400, 'API 服务编码重复，请调整逻辑表英文名' }
    codes.add(code)
    identities.add({item:item, tableName:tableName, serviceName:serviceName, code:code})
}
var now = LocalDateTime.now()
return db.transaction(() => {
    var tables = {}
    var tableIds = []
    var scopedTables = db.table('db_table_t').where().eq('tenant_id',tenantId).eq('datasource_id',datasourceId).eq('is_del',0).select()
    for (row in scopedTables) {
        var key = text(row.tableName || row.table_name).toLowerCase()
        if (names.contains(key)) {
            if (tables[key] != null) { exit 409, 'API 来源中存在同名逻辑表，请核对登记' }
            tables[key] = text(row.tid)
            tableIds.add(row.tid)
        }
    }
    var serviceIds = {}
    var scopedServices = db.table('data_pull_service_t').where().eq('tenant_id',tenantId)
        .eq('datasource_id',datasourceId).eq('is_del',0).in('service_code',codes).select()
    for (row in scopedServices) { serviceIds[text(row.serviceCode || row.service_code)] = row.tid }
    var ruleIds = {}
    if (!tableIds.isEmpty()) {
        var scopedRules = db.table('data_pull_rule_t').where().eq('tenant_id',tenantId)
            .eq('datasource_id',datasourceId).eq('is_del',0).in('source_table_id',tableIds).select()
        for (row in scopedRules) { ruleIds[text(row.sourceTableId || row.source_table_id)] = row.tid }
    }
    var newServices = []
    var updatedServices = []
    var newRules = []
    var updatedRules = []
    var entries = []
    var rules = 0
    for (identity in identities) {
        var item = identity.item
        var serviceId = serviceIds[identity.code]
        var auth = text(item.authType).toUpperCase()
        if (auth == '') { auth = 'NONE' }
        var protocol = text(item.baseUrl).toLowerCase().startsWith('https:') ? 'HTTPS' : 'HTTP'
        var serviceType = text(item.apiProtocol).toUpperCase() == 'SOAP' ? 'SOAP' : 'REST'
        if (serviceId == null) {
            serviceId = IdUtil.getSnowflakeNextIdStr()
            newServices.add({tid:serviceId,tenant_id:tenantId,datasource_id:datasourceId,
                service_code:identity.code,service_name:identity.serviceName,base_url:text(item.baseUrl),
                protocol:protocol,service_type:serviceType,
                common_headers_json:item.commonHeadersJson,auth_type:auth,credential_ref:item.credentialRef,
                created_time:now,updated_time:now,is_del:0})
        } else {
            updatedServices.add([identity.serviceName,text(item.baseUrl),protocol,serviceType,item.commonHeadersJson,auth,item.credentialRef,now,
                serviceId,tenantId,datasourceId].toArray())
        }
        var tableId = tables[identity.tableName.toLowerCase()]
        var entry = {tableName:identity.tableName,tableComment:item.tableComment,serviceId:serviceId,ruleReady:tableId != null}
        if (tableId != null) {
            var ruleId = ruleIds[tableId]
            var method = text(item.endpointMethod) == '' ? 'GET' : text(item.endpointMethod).toUpperCase()
            var schedule = item.captureSchedule
            var cron = text(schedule?.cronExpression || item.cronExpression)
            var trigger = cron == '' ? 'MANUAL' : 'CRON'
            var name = text(item.tableComment) + '拉取规则'
            if (ruleId == null) {
                ruleId = IdUtil.getSnowflakeNextIdStr()
                newRules.add({tid:ruleId,tenant_id:tenantId,datasource_id:datasourceId,source_table_id:tableId,
                    service_id:serviceId,rule_name:name,endpoint_method:method,endpoint_path:text(item.endpointPath),
                    request_template_json:item.requestTemplateJson,response_config_json:item.responseConfigJson,
                    pagination_config_json:item.paginationConfigJson,incremental_config_json:item.incrementalConfigJson,
                    runtime_config_json:item.runtimeConfigJson,trigger_mode:trigger,cron_expression:cron,
                    enabled:0,created_time:now,updated_time:now,is_del:0})
            } else {
                updatedRules.add([serviceId,name,method,text(item.endpointPath),item.requestTemplateJson,item.responseConfigJson,
                    item.paginationConfigJson,item.incrementalConfigJson,item.runtimeConfigJson,trigger,cron,now,
                    ruleId,tenantId,datasourceId,tableId].toArray())
            }
            entry.tableId = tableId
            entry.ruleId = ruleId
            rules = rules + 1
        }
        entries.add(entry)
    }
    if (!newServices.isEmpty()) { db.table('data_pull_service_t').batchInsert(newServices,100) }
    if (!updatedServices.isEmpty()) {
        db.batchUpdate('UPDATE data_pull_service_t SET service_name=?,base_url=?,protocol=?,service_type=?,common_headers_json=?,auth_type=?,credential_ref=?,updated_time=? WHERE tid=? AND tenant_id=? AND datasource_id=? AND is_del=0',updatedServices)
    }
    if (!newRules.isEmpty()) { db.table('data_pull_rule_t').batchInsert(newRules,100) }
    if (!updatedRules.isEmpty()) {
        db.batchUpdate('UPDATE data_pull_rule_t SET service_id=?,rule_name=?,endpoint_method=?,endpoint_path=?,request_template_json=?,response_config_json=?,pagination_config_json=?,incremental_config_json=?,runtime_config_json=?,trigger_mode=?,cron_expression=?,updated_time=? WHERE tid=? AND tenant_id=? AND datasource_id=? AND source_table_id=? AND is_del=0',updatedRules)
    }
    return {datasourceId:datasourceId,definitions:definitions.size(),services:identities.size(),rules:rules,entries:entries}
})
