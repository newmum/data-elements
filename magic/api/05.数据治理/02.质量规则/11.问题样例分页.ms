{
  "properties": {},
  "id": "a1000000000000000000000000000010",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "11.问题样例分页",
  "path": "/run/issues",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"质检实例ID\",\"page\":1,\"size\":20,\"status\":\"OPEN\",\"severity\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/run/issues；只读；分页查询有限问题样例，聚合异常数以指标表为准；问题查看主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：11.问题样例分页
// 业务说明：POST /dwm/quality/run/issues；只读；分页查询有限问题样例，聚合异常数以指标表为准；问题查看主接口。

import tenantRuntime
import dataQuality
import cn.hutool.core.util.NumberUtil

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 问题样例受实例归属约束，且仓储只返回数量受限的样例。
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '实例ID不能为空' }
var status = body?.status == null ? '' : ('' + body.status).trim()
var severity = body?.severity == null ? '' : ('' + body.severity).trim()
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return dataQuality.issuePage(runId, status, severity, page, size)
