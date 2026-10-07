{
  "properties": {},
  "id": "a1000000000000000000000000000002",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "02.任务分页",
  "createTime": 1785628800000,
  "updateTime": 1790498525171,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/task/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"page\":1,\"size\":20,\"keyword\":\"人口\",\"status\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "POST /dwm/quality/task/page；只读；分页查询当前租户质检任务及最近执行结果；任务列表主接口。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.任务分页
// 业务说明：POST /dwm/quality/task/page；只读；分页查询当前租户质检任务及最近执行结果；任务列表主接口。

import tenantRuntime
import dataQuality
import cn.hutool.core.util.NumberUtil

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 筛选任务状态、类型和关键词；非法类型应在查询前被拒绝。
var status = body?.status == null ? '' : ('' + body.status).trim()
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
var kind = body?.kind == null || ('' + body.kind).trim() == '' ? null : ('' + body.kind).trim()
if (kind != null && kind != 'PROFILE' && kind != 'QUALITY') { exit 400, '任务类型无效' }
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return dataQuality.taskPage(status, keyword, page, size, kind)
