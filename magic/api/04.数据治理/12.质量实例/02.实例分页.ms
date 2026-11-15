{
  "properties": {},
  "id": "a1000000000000000000000000000008",
  "groupId": "7391edbbd74b46969f5a885e04cd5ee7",
  "name": "02.实例分页",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"page\":1,\"size\":20,\"status\":\"\",\"keyword\":\"人口\"}",
  "headers": [],
  "paths": [],
  "description": "POST /dwm/qrun/page；只读；分页查询当前租户质检执行实例；执行记录主接口。",
  "createTime": 1785628800000,
  "updateTime": 1785628800000,
  "script": null
}
================================
import tenantRuntime
import dataQuality
import cn.hutool.core.util.NumberUtil
// POST /dwm/qrun/page 实例分页
// POST /dwm/qrun/page；只读；分页查询当前租户质检执行实例；执行记录主接口。
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
