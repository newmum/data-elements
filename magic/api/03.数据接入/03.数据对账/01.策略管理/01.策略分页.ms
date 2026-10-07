{
  "properties": {},
  "id": "a51c07d0f6814d78b15fbcd1adfed1c8",
  "groupId": "ccb8f94e621a4feaa18362a325890270",
  "name": "01.策略分页",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"page\":1,\"size\":20,\"keyword\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/policy/page；只读；分页查询当前租户对账策略及最近一次执行结果；策略列表主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：01.策略分页
// 路由：POST /page（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/policy/page；只读；分页查询当前租户对账策略及最近一次执行结果；策略列表主接口。

import tenantRuntime
import reconcile
import cn.hutool.core.util.NumberUtil

// 从当前会话取得租户，并规范化筛选词和分页参数。
var tenantId = tenantRuntime.id()
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)

// 仓储只查询当前租户的策略，保留既有排序和最近执行结果。
return reconcile.policyPage(keyword, page, size)
