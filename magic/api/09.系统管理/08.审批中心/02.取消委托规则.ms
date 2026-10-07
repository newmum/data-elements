{
  "properties": {},
  "id": "b81216bf81ee41d8ad5d09526bf07703",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "02.取消委托规则",
  "createTime": 1786654822605,
  "updateTime": 1786951315423,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delegation/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "取消委托规则（POST /sym/approval/delegation/delete）。仅软删除当前租户当前用户自己的规则；使用请求参数 tid；涉及 wf_approval_delegation_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.取消委托规则
// 路由：POST /sym/approval/delegation/delete
// 业务说明：取消委托规则（POST /sym/approval/delegation/delete）。仅软删除当前租户当前用户自己的规则；使用请求参数 tid；涉及 wf_approval_delegation_t 等业务表。
import tenantRuntime
import approvalRuntime
if (body?.tid == null || body.tid == '') { exit -1, '委托规则ID不能为空' }
var principal = approvalRuntime.principal()
var tenantId = tenantRuntime.id()
var updated = db.update("""
    update wf_approval_delegation_t
       set is_del = 1, status = 0, updated_by = #{principal.userId}, updated_time = #{new Date()}
     where tid = #{body.tid} and tenant_id = #{tenantId}
       and delegator_user_id = #{principal.userId} and is_del = 0
""")
if (updated == 0) { exit -1, '委托规则不存在或无权取消' }
return true
