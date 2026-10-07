{
  "properties": {},
  "id": "a1000000000000000000000000000009",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "10.实例详情",
  "path": "/run/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"质检实例ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/run/detail；只读；查询实例、分片进度和规则指标；实例详情主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：10.实例详情
// 业务说明：POST /dwm/quality/run/detail；只读；查询实例、分片进度和规则指标；实例详情主接口。

import tenantRuntime
import dataQuality

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 返回实例指标和分片进度；仓储限定当前租户。
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '实例ID不能为空' }
return dataQuality.runDetail(runId)
