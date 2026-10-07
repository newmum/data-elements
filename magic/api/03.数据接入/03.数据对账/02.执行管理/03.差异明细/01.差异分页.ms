{
  "properties": {},
  "id": "6b0450ec31e341be8c075c4d93d330e1",
  "groupId": "0c0fe2ac06f04679b4b52cfa19c54d96",
  "name": "01.差异分页",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"执行实例ID\",\"type\":\"VALUE_MISMATCH\",\"page\":1,\"size\":20}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/diff/page；只读；分页查询脱敏后的字段级差异；差异明细主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：01.差异分页
// 路由：POST /page（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/run/diff/page；只读；分页查询脱敏后的字段级差异；差异明细主接口。

import tenantRuntime
import reconcile
import cn.hutool.core.util.NumberUtil

// 差异明细只展示当前租户指定实例的脱敏字段差异。
var tenantId = tenantRuntime.id()
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '对账实例ID不能为空' }
var type = body?.type == null ? '' : ('' + body.type).trim()
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return reconcile.diffPage(runId, type, page, size)
