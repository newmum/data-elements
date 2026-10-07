{
  "properties": {},
  "id": "wx_std_review_submit_01",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "06.提交数据元审核",
  "createTime": 1789337365703,
  "updateTime": 1789337949970,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/review/submit",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按请求目标与业务状态执行提交数据元审核，返回本次操作结果；使用请求参数 metaId、remark；涉及 da_metadata_t、asset_workbench_issue_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.提交数据元审核
// 路由：POST /dwm/standard/element/review/submit
// 业务说明：按请求目标与业务状态执行提交数据元审核，返回本次操作结果；使用请求参数 metaId、remark；涉及 da_metadata_t、asset_workbench_issue_t 等业务表。
import tenantRuntime
import approvalRuntime
import cn.hutool.core.util.IdUtil

var tenantId = tenantRuntime.id()
var metaId = body?.metaId
if(metaId == null || metaId == '') { exit 400,'数据元ID不能为空' }
var element = db.table('da_metadata_t').where().eq('tenant_id',tenantId).eq('tid',metaId).eq('is_del',0).selectOne()
if(element == null) { exit 404,'数据元不存在' }
var principal = approvalRuntime.principal()
var now = new Date()
var existing = db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('asset_type','standard_element').eq('asset_id',metaId).eq('issue_type','STANDARD_REVIEW').eq('status','PENDING').eq('is_del',0).selectOne()
if(existing != null) { return existing }
var row = {tid:IdUtil.getSnowflakeNextIdStr(),tenant_id:tenantId,asset_type:'standard_element',asset_id:metaId,issue_type:'STANDARD_REVIEW',issue_code:element.metaCode,title:'数据元审核：'+element.metaName,detail:body?.remark,status:'PENDING',reporter_id:principal.userId,created_time:now,updated_time:now,is_del:0}
db.table('asset_workbench_issue_t').primary('tid').insert(row)
db.table('da_metadata_t').where().eq('tenant_id',tenantId).eq('tid',metaId).eq('is_del',0).update({publish_status:0,updated_time:now})
return row
