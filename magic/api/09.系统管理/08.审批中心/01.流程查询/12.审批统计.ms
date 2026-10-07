{
  "properties": {},
  "id": "a75efba824d64e6f9a3f12561f0a9f10",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "12.审批统计",
  "createTime": 1785673588384,
  "updateTime": 1786191951453,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "summary",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "审批中心统计（GET /sym/approval/query/summary）。只读当前租户流程任务、实例和历史任务。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.审批统计
// 路由：GET /sym/approval/query/summary
// 业务说明：审批中心统计（GET /sym/approval/query/summary）。只读当前租户流程任务、实例和历史任务。
// 审批统计：处理 GET summary 的核心查询和组装返回数据逻辑。
import approvalRuntime
import tenantRuntime
var principal = approvalRuntime.principal()
var tenantId = tenantRuntime.id()
var currentUserId = principal.userId
var permissionList = principal.permissionKeys

var todo = db.selectInt("""
    select count(distinct t.id)
      from flow_task t join flow_user u on u.associated = t.id
     where t.tenant_id = #{tenantId} and u.tenant_id = #{tenantId}
       and t.del_flag = '0' and u.del_flag = '0' and t.node_type = 1
       and t.flow_status not in ('4', '6', '8')
       and u.processed_by in
       <foreach item="permission" collection="permissionList" open="(" separator="," close=")">#{permission}</foreach>
""")
var process = db.selectInt("select count(*) from flow_instance where tenant_id = #{tenantId} and create_by = #{currentUserId} and del_flag = '0' and flow_status in ('1','9')")
var done = db.selectInt("select count(distinct instance_id) from flow_his_task where tenant_id = #{tenantId} and approver = #{currentUserId} and del_flag = '0'")
var archive = db.selectInt("select count(*) from flow_instance where tenant_id = #{tenantId} and create_by = #{currentUserId} and del_flag = '0' and flow_status in ('2','4','6','8')")
return { todo, process, done, archive }
