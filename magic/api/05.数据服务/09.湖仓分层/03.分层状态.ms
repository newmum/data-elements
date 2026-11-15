{
  "properties": {},
  "id": "79c6a95bdabb4192a5cfdd676140c80a",
  "script": null,
  "groupId": "b5badef9297e4dbb8a6028e72a393d4e",
  "name": "03.分层状态",
  "createTime": 1789000000003,
  "updateTime": 1789000000003,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/state",
  "method": "PUT",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的湖仓分层或对象标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "requestBody": "{\n  \"status\": \"DISABLED\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"...\",\n  \"status\": \"DISABLED\"\n}",
  "description": "启用或停用湖仓分层（PUT /dws/lake/state?id={id}）。只更新当前租户记录的 status，不做物理删除。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import "@post:/sym/user/me" as me

// 接口：03.分层状态
// 路由：PUT /dws/lake/state?id={id}
// 业务说明：启用/停用分层；租户取自认证上下文，只能修改当前租户且未删除的记录。

var tenantId = tenantRuntime.id()
var id = param?.id == null ? '' : (param.id + '').trim()
var status = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
var userId = me()?.id

if (id == '') { exit 400, '分层标识不能为空' }
if (status != 'ENABLED' && status != 'DISABLED') { exit 400, '状态必须是 ENABLED 或 DISABLED' }

var changed = db.update("update lake_zone_t set status = #{status}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { status: status, userId: userId, id: id, tenantId: tenantId })
if (changed == 0) { exit 404, '分层不存在或不在当前租户' }
return { id: id, status: status }
