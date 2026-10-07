{
  "properties": {},
  "id": "a1000000000000000000000000000008",
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "09.实例分页",
  "path": "/run/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"page\":1,\"size\":20,\"status\":\"\",\"keyword\":\"人口\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/quality/run/page；只读；分页查询当前租户质检执行实例；执行记录主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
// 接口：09.实例分页
// 业务说明：POST /dwm/quality/run/page；只读；分页查询当前租户质检执行实例；执行记录主接口。

import tenantRuntime
import dataQuality
import cn.hutool.core.util.NumberUtil

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
// 读取当前租户的质检历史，并允许按任务类型和状态筛选。
var status = body?.status == null ? '' : ('' + body.status).trim()
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
var kind = body?.kind == null || ('' + body.kind).trim() == '' ? null : ('' + body.kind).trim()
if (kind != null && kind != 'PROFILE' && kind != 'QUALITY') { exit 400, '任务类型无效' }
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return dataQuality.runPage(status, keyword, page, size, kind)
