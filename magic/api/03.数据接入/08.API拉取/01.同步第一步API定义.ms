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
// 接口：同步第一步 API 定义
// 业务说明：用户保存 API 数据源后，将第一步定义整理为服务和表级规则草稿；不会创建或启动 NiFi 任务。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
var datasourceId = body?.datasourceId == null ? '' : ('' + body.datasourceId).trim()
if (datasourceId == '') { exit 400, '数据源标识不能为空' }

// 先由服务端验证数据源归属与接入方式，并解析第一步保存的 API 定义。
var definitions = apiPullConfig.definitions(datasourceId)
var services = 0
var rules = 0
var entries = []

for (item in definitions) {
    var tableName = item.tableName == null ? '' : ('' + item.tableName).trim()
    var tableComment = item.tableComment == null ? '' : ('' + item.tableComment).trim()
    var serviceName = item.serviceName == null ? '' : ('' + item.serviceName).trim()
    var baseUrl = item.baseUrl == null ? '' : ('' + item.baseUrl).trim()
    var endpointPath = item.endpointPath == null ? '' : ('' + item.endpointPath).trim()
    if (tableName == '') { exit 400, 'API 定义缺少数据表英文名' }
    if (tableComment == '') { exit 400, 'API 定义缺少数据表中文名' }
    if (serviceName == '') { exit 400, 'API 定义缺少接口服务名称' }
    if (baseUrl == '') { exit 400, 'API 定义缺少 Base URL' }
    if (endpointPath == '') { exit 400, 'API 定义缺少接口路径' }

    var serviceCode = apiPullConfig.suggestedServiceCode(tableName, serviceName)
    var existingService = db.selectOne("""
        SELECT tid
          FROM data_pull_service_t
         WHERE tenant_id = #{tenantId}
           AND datasource_id = #{datasourceId}
           AND service_code = #{serviceCode}
           AND is_del = 0
         LIMIT 1
    """)
    var service = {
        datasourceId: datasourceId,
        serviceName: serviceName,
        serviceCode: serviceCode,
        baseUrl: baseUrl,
        commonHeadersJson: item.commonHeadersJson,
        authType: item.authType == null ? 'NONE' : item.authType,
        credentialRef: item.credentialRef
    }
    if (existingService != null) { service.tid = existingService.tid }
    var savedService = apiPullConfig.saveService(service)
    services = services + 1

    // 第二步未登记逻辑表时只保存服务；下次保存会继续补齐表级规则。
    var table = db.selectOne("""
        SELECT tid
          FROM db_table_t
         WHERE tenant_id = #{tenantId}
           AND datasource_id = #{datasourceId}
           AND table_name = #{tableName}
           AND is_del = 0
         ORDER BY updated_time DESC, tid
         LIMIT 1
    """)
    var entry = {tableName: tableName, tableComment: tableComment,
                 serviceId: savedService.tid, ruleReady: table != null}
    if (table != null) {
        var tableId = table.tid
        var existingRule = db.selectOne("""
            SELECT tid, nifi_task_id, enabled
              FROM data_pull_rule_t
             WHERE tenant_id = #{tenantId}
               AND datasource_id = #{datasourceId}
               AND source_table_id = #{tableId}
               AND is_del = 0
             ORDER BY updated_time DESC, tid
             LIMIT 1
        """)
        var rule = {
            datasourceId: datasourceId, sourceTableId: tableId,
            serviceId: savedService.tid, ruleName: tableComment + '拉取规则',
            endpointMethod: item.endpointMethod == null ? 'GET' : item.endpointMethod,
            endpointPath: endpointPath,
            requestTemplateJson: item.requestTemplateJson,
            responseConfigJson: item.responseConfigJson,
            paginationConfigJson: item.paginationConfigJson,
            incrementalConfigJson: item.incrementalConfigJson,
            runtimeConfigJson: item.runtimeConfigJson,
            triggerMode: item.triggerMode == null ? 'MANUAL' : item.triggerMode,
            cronExpression: item.cronExpression
        }
        if (existingRule != null) {
            rule.tid = existingRule.tid
            rule.nifiTaskId = existingRule.nifi_task_id
            rule.enabled = existingRule.enabled
        }
        var savedRule = apiPullConfig.saveRule(rule)
        entry.tableId = tableId
        entry.ruleId = savedRule.tid
        rules = rules + 1
    }
    entries.add(entry)
}

return {datasourceId: datasourceId, definitions: definitions.size(), services: services,
        rules: rules, entries: entries,
        message: rules == definitions.size()
            ? 'API 服务与表级拉取规则已同步，下一步可创建 NiFi 任务'
            : 'API 服务已同步；请在第二步完成相应数据表标注后再次保存，系统会自动生成表级规则'}
