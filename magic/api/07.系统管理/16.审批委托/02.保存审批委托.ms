{
  "properties": {},
  "id": "b81216bf81ee41d8ad5d09526bf07702",
  "script": null,
  "groupId": "c1237871230f4f478b31fc9b2d00b13c",
  "name": "02.保存审批委托",
  "createTime": 1786757000000,
  "updateTime": 1786951315863,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"delegateUserId\": \"受托人ID\",\n  \"startTime\": \"2026-08-14 09:00:00\",\n  \"endTime\": \"2026-08-15 18:00:00\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"委托规则ID\"\n}",
  "description": "校验受托人租户归属和有效期，保存本人审批委托。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import approvalRuntime
import tenantAccounts
import cn.hutool.core.util.IdUtil

// 校验受托人租户归属和有效期，保存本人审批委托。
if (body?.delegateUserId == null || body.delegateUserId == '') { exit -1, '请选择受托人' }
if (body?.startTime == null || body?.endTime == null) { exit -1, '请选择委托有效期' }
var principal = approvalRuntime.principal()
var tenantId = tenantRuntime.id()
if (body.delegateUserId == principal.userId) { exit -1, '不能委托给自己' }

// Membership is stored only in the control schema, never in the tenant database.
if (!tenantAccounts.hasMembership(tenantId, body.delegateUserId + '')) {
    exit -1, '受托人不属于当前租户'
}

var overlap = db.selectInt("""
    select count(*) from wf_approval_delegation_t
     where tenant_id = #{tenantId}
       and delegator_user_id = #{principal.userId}
       and delegate_user_id = #{body.delegateUserId}
       and is_del = 0 and status = 1
       and start_time <= #{body.endTime} and end_time >= #{body.startTime}
       <if test="body.flowCode != null and body.flowCode != ''">and (flow_code = #{body.flowCode} or flow_code is null)</if>
""")
if (overlap > 0) { exit -1, '该时间段已存在重叠委托' }

var now = new Date()
var id = IdUtil.getSnowflakeNextIdStr()
db.table('wf_approval_delegation_t').insert({
    tid: id,
    tenant_id: tenantId,
    delegator_user_id: principal.userId,
    delegate_user_id: body.delegateUserId,
    flow_code: body?.flowCode,
    start_time: body.startTime,
    end_time: body.endTime,
    reason: body?.reason,
    status: 1,
    created_by: principal.userId,
    updated_by: principal.userId,
    created_time: now,
    updated_time: now,
    is_del: 0
})
return { tid: id }
