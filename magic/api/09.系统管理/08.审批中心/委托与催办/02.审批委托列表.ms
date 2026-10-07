{
  "properties": {},
  "id": "b81216bf81ee41d8ad5d09526bf07701",
  "script": null,
  "groupId": "approval_delegation_group_01",
  "name": "02.审批委托列表",
  "createTime": 1786757000000,
  "updateTime": 1786951315648,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delegation/list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "查询当前用户的审批委托规则（GET /sym/approval/delegation/list）。委托规则读取当前租户库，受托人姓名由控制库身份服务批量补充；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 wf_approval_delegation_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.审批委托列表
// 路由：GET /sym/approval/delegation/list
// 业务说明：查询当前用户的审批委托规则（GET /sym/approval/delegation/list）。委托规则读取当前租户库，受托人姓名由控制库身份服务批量补充；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；涉及 wf_approval_delegation_t 等业务表。
import tenantRuntime
import approvalRuntime
import tenantAccounts


var principal = approvalRuntime.principal()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = tenantRuntime.id()
var rows = db.select("""
    select d.tid, d.delegate_user_id, d.flow_code, d.start_time, d.end_time,
           d.reason, d.status, d.created_time
      from wf_approval_delegation_t d
     where d.tenant_id = #{tenantId}
       and d.delegator_user_id = #{principal.userId}
       and d.is_del = 0
     order by d.status desc, d.end_time desc
""")
var names = tenantAccounts.names(rows == null ? [] : rows.map(item => item.delegateUserId))
if (rows != null) {
    rows.each(item -> item.delegateUserName = names[item.delegateUserId] || item.delegateUserId)
}
return rows || []
