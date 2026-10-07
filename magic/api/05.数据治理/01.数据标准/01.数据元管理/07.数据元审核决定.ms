{
  "properties": {},
  "id": "wx_std_review_decide_01",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "07.数据元审核决定",
  "createTime": 1789337365759,
  "updateTime": 1789337902940,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/review/decide",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验待审数据元工单与 APPROVE/REJECT 动作，记录审核意见，更新工单及数据元发布状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.数据元审核决定
// 路由：POST /dwm/standard/element/review/decide
// 业务说明：核验待审数据元工单与 APPROVE/REJECT 动作，记录审核意见，更新工单及数据元发布状态。
import tenantRuntime
import approvalRuntime

var tenantId = tenantRuntime.id()
var reviewId = body?.reviewId
var action = body?.action
if(reviewId == null || reviewId == '' || (action != 'APPROVE' && action != 'REJECT')) { exit 400,'审核参数不合法' }
var review = db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('tid',reviewId).eq('asset_type','standard_element').eq('issue_type','STANDARD_REVIEW').eq('status','PENDING').eq('is_del',0).selectOne()
if(review == null) { exit 404,'待审核记录不存在' }
if(action == 'REJECT' && (body?.opinion == null || body?.opinion.trim() == '')) { exit 400,'退回时必须填写审核意见' }
var principal = approvalRuntime.principal()
var now = new Date()
var status = action == 'APPROVE' ? 'CLOSED' : 'REJECTED'
db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('tid',reviewId).update({status:status,assignee_org_id:principal.orgId,resolution:body?.opinion,resolved_time:now,updated_time:now})
if(action == 'APPROVE') { db.table('da_metadata_t').where().eq('tenant_id',tenantId).eq('tid',review.assetId).eq('is_del',0).update({publish_status:1,updated_time:now}) }
return true
