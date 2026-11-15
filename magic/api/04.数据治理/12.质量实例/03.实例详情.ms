{
  "properties": {},
  "id": "a1000000000000000000000000000009",
  "groupId": "7391edbbd74b46969f5a885e04cd5ee7",
  "name": "03.实例详情",
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"质检实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qrun/detail；只读；查询实例、分片进度和规则指标；实例详情主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
// POST /dwm/qrun/detail 实例详情
// POST /dwm/qrun/detail；只读；查询实例、分片进度和规则指标；实例详情主接口。
// 业务租户始终来自当前登录会话。

var tenantId = tenantRuntime.id()
// 返回实例指标和分片进度；仓储限定当前租户。
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '实例ID不能为空' }
return dataQuality.runDetail(runId,body||{})
