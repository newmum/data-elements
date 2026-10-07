{
  "properties": {},
  "id": "7e1e2a2090934d67954d5d9e3df59c66",
  "groupId": "ccb8f94e621a4feaa18362a325890270",
  "name": "03.保存策略",
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"policyName\":\"接入任务每日主键对账\",\"accessTaskId\":\"接入任务ID\",\"compareMode\":\"KEY\",\"triggerMode\":\"MANUAL\",\"bucketCount\":8,\"fetchSize\":2000}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/policy/save；写入；保存策略并从 data_access_field_mapping 初始化字段规则；策略维护主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：03.保存策略
// 路由：POST /save（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/policy/save；写入；保存策略并从 data_access_field_mapping 初始化字段规则；策略维护主接口。

import tenantRuntime
import reconcile

// 先检查策略名称和接入任务；新增时仓储从接入任务字段映射初始化规则。
var tenantId = tenantRuntime.id()
if (body == null) { exit 400, '请填写对账策略' }
if (body?.policyName == null || ('' + body.policyName).trim() == '') { exit 400, '策略名称不能为空' }
if (body?.accessTaskId == null || ('' + body.accessTaskId).trim() == '') { exit 400, '请选择数据接入任务' }
var triggerMode = body?.triggerMode == null ? 'MANUAL' : ('' + body.triggerMode).trim()
if (triggerMode == 'CRON' && (body?.cronExpression == null || ('' + body.cronExpression).trim() == '')) {
    exit 400, '定时策略必须配置 Cron 表达式'
}

// 策略和规则的多表写入由仓储事务完成；保存后重新读取完整详情。
var policyId = reconcile.persistPolicy(body)
return reconcile.policyDetail(policyId)
