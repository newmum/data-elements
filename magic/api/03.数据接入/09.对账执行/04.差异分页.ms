{
  "properties": {},
  "id": "6b0450ec31e341be8c075c4d93d330e1",
  "groupId": "56755e012bc14bec93d431716f86b595",
  "name": "04.差异分页",
  "path": "/diff",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"执行实例ID\",\"type\":\"VALUE_MISMATCH\",\"page\":1,\"size\":20}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/recon/diff；只读；分页查询脱敏后的字段级差异；差异明细主接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
import tenantRuntime
import reconcile
import cn.hutool.core.util.NumberUtil

// POST /ods/recon/diff 差异分页。
// 接口：01.差异分页
// 路由：POST /page（完整路由由目录分组组合）
// 业务说明：POST /ods/recon/diff；只读；分页查询脱敏后的字段级差异；差异明细主接口。


// 差异明细只展示当前租户指定实例的脱敏字段差异。
var tenantId = tenantRuntime.id()
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (runId == '') { exit 400, '对账实例ID不能为空' }
var type = body?.type == null ? '' : ('' + body.type).trim()
var page = body?.page == null || !('' + body.page).matches('^[0-9]+$') ? 1 : NumberUtil.parseInt('' + body.page)
var size = body?.size == null || !('' + body.size).matches('^[0-9]+$') ? 20 : NumberUtil.parseInt('' + body.size)
return reconcile.diffPage(runId, type, page, size)
