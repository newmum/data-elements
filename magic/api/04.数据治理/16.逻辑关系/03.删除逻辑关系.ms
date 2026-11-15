{
  "properties": {},
  "id": "dwm_metadata_governance_delete_relation_01",
  "script": null,
  "groupId": "335ef5caa54f4c6ca44e616cca6cc00a",
  "name": "03.删除逻辑关系",
  "createTime": null,
  "updateTime": 1790614324471,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "逻辑删除当前租户的 ER 关系记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
// POST /dwm/relation/delete 删除逻辑关系
// 逻辑删除当前租户的 ER 关系记录。
// 先核验关系标识，再从会话读取业务租户标识。

var relationId = body?.tid == null ? '' : ('' + body.tid).trim()
if (relationId == '') { exit 400, '关系ID不能为空' }
var tenantId = tenantRuntime.id()

// 只更新逻辑关系台账，不触碰源数据库的物理外键。
var affected = db.update("""
    UPDATE dwm_metadata_relation_t
       SET is_del = 1,
           updated_time = NOW(6)
     WHERE tenant_id = #{tenantId}
       AND tid = #{relationId}
       AND is_del = 0
""")
return affected > 0
