{
  "properties": {},
  "id": "dwm_metadata_governance_delete_relation_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "04.删除逻辑关系",
  "createTime": null,
  "updateTime": 1790614324471,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/relations/delete",
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
// 接口：删除逻辑关系
// 业务说明：只对当前登录租户的指定逻辑关系执行软删除；不存在或已删除时返回 false。
import tenantRuntime

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
