{
  "properties": {},
  "id": "api_pull_rule_save_01",
  "script": null,
  "groupId": "tenant_magic_api_pull_20261003",
  "name": "04.API 拉取 - 保存表规则",
  "path": "/api-pull/rules/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "校验数据源、逻辑表、接口服务及请求路径；保存规则配置，不创建或启动 NiFi 任务。"
}
================================
// 接口：保存 API 表级拉取规则
// 业务说明：校验数据源、逻辑表、接口服务及请求路径；保存规则配置，不创建或启动 NiFi 任务。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
if (body?.datasourceId == null || ('' + body.datasourceId).trim() == '') { exit 400, '请选择数据源' }
if (body?.sourceTableId == null || ('' + body.sourceTableId).trim() == '') { exit 400, '请选择逻辑数据表' }
if (body?.serviceId == null || ('' + body.serviceId).trim() == '') { exit 400, '请选择接口服务' }
if (body?.ruleName == null || ('' + body.ruleName).trim() == '') { exit 400, '请填写拉取规则名称' }
var endpointPath = body?.endpointPath == null ? '' : ('' + body.endpointPath).trim()
if (!endpointPath.startsWith('/')) { exit 400, '接口路径必须以 / 开头' }
var triggerMode = body?.triggerMode == null ? 'MANUAL' : ('' + body.triggerMode).trim().toUpperCase()
if (triggerMode == 'CRON' && (body?.cronExpression == null || ('' + body.cronExpression).trim() == '')) {
    exit 400, '定时任务必须填写 Cron 表达式'
}

// Java 校验表和服务确实属于当前租户，保留 NiFi 任务 ID 的内部关联约束。
return apiPullConfig.saveRule(body)
