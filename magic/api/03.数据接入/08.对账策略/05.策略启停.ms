{
  "properties": {},
  "id": "fe4bfa8569fb4f22ab05ddf2af860118",
  "groupId": "9f1b442eff7655e186e68956bf1daaf0",
  "name": "05.策略启停",
  "path": "/switch",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"policyId\":\"策略ID\",\"enabled\":true}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/policy/switch；写入；启用或停用当前租户策略，启用前必须通过预检；策略状态接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
import tenantRuntime
import reconcile

// POST /ods/policy/switch 策略启停。
// 接口：05.策略启停
// 路由：POST /enable（完整路由由目录分组组合）
// 业务说明：POST /ods/policy/switch；写入；启用或停用当前租户策略，启用前必须通过预检；策略状态接口。


// 接口明确解析启停意图；仓储最终强制执行“预检通过才可启用”。
var tenantId = tenantRuntime.id()
var policyId = body?.policyId == null ? '' : ('' + body.policyId).trim()
if (policyId == '') { exit 400, '策略ID不能为空' }
var enabled = body?.enabled == true || body?.enabled == 1 || ('' + body?.enabled).toLowerCase() == 'true' || body?.enabled == '1'
return reconcile.setPolicyEnabled(policyId, enabled)
