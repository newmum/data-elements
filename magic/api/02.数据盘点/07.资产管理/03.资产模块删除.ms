{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c42",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "03.资产模块删除",
  "createTime": 1791030855465,
  "updateTime": 1791030855465,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"module\":\"plan\",\"id\":\"\",\"dryRun\":false}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"id\":\"\",\"deleted\":true}",
  "description": "按当前租户软删除资产模块记录；记录不存在或跨租户时拒绝。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import "@post:/sym/user/me" as me

// POST /dst/asset/delete 资产模块删除
// 业务说明：按当前租户软删除资产模块记录；记录不存在或跨租户时拒绝。

var tenantId = tenantRuntime.id()
var user = me()
var userId = user == null ? '' : (user.id == null ? '' : user.id + '')
var text = (value) => value == null ? '' : value + ''
var modules = {
    plan: 'asset_plan_t', discovery: 'asset_discovery_t', disposal: 'asset_disposal_t',
    security: 'asset_security_t', operation: 'asset_operation_t', evaluation: 'asset_evaluation_t', inventory: 'asset_inventory_t'
}
var module = text(body?.module).trim().toLowerCase()
if (modules[module] == null) { exit 400, '资产模块无效' }
var id = text(body?.id).trim()
if (id == '' || !id.matches('[A-Za-z0-9_-]+')) { exit 400, '记录标识不能为空且格式正确' }
var existing = db.select("SELECT tid FROM " + modules[module] + " WHERE tenant_id = #{tenantId} AND tid = #{id} AND is_del = 0")
if (existing == null || existing.size() == 0) { exit 404, '记录不存在或不在当前租户' }
if (body?.dryRun == true) { return { dryRun: true, id: id, valid: true } }
var changed = db.update("UPDATE " + modules[module] + " SET is_del = 1, updated_by = #{userId}, updated_time = current_timestamp WHERE tenant_id = #{tenantId} AND tid = #{id} AND is_del = 0")
if (changed != 1) { exit 409, '记录已被修改，请刷新后重试' }
return { id: id, deleted: true }
