{
  "properties": {},
  "id": "e3e39c7d77a3481685b05a20dc8c780f",
  "groupId": "986c0e9209674f818072e3044d98013c",
  "name": "03.实例分页",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"page\":1,\"size\":20,\"status\":\"\",\"keyword\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/page；只读；分页查询当前租户历史执行实例及一致率；历史对账单主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：03.实例分页
// 路由：POST /page（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/page；只读；分页查询当前租户历史执行实例及一致率；历史对账单主接口。

import tenantRuntime
import reconcile
import cn.hutool.core.util.NumberUtil

// 分页展示当前租户历史实例，并允许按状态和关键词筛选。
var tenantId = tenantRuntime.id()
var status = body?.status == null ? '' : ('' + body.status).trim()
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return reconcile.runPage(status, keyword, page, size)
