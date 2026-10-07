{
  "properties": {},
  "id": "wx_quality_work_create_01",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "19.创建质量工单",
  "createTime": 1789337365872,
  "updateTime": 1790498524575,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/workorders/create",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行创建质量工单，返回保存或配置结果；使用请求参数 issueId、assigneeOrgId、remark；涉及 asset_workbench_issue_t、rm_org_t、dq_quality_issue_sample_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：19.创建质量工单
// 路由：POST /dwm/quality/workorders/create
// 业务说明：校验请求资料后执行创建质量工单，返回保存或配置结果；使用请求参数 issueId、assigneeOrgId、remark；涉及 asset_workbench_issue_t、rm_org_t、dq_quality_issue_sample_t 等业务表。
import tenantRuntime
import approvalRuntime
import cn.hutool.core.util.IdUtil
// Create a remediation case from an actual bounded issue sample; idempotent under the issue lock.
var tenantId=tenantRuntime.id()
var issueId=body?.issueId
if(issueId==null || issueId=='') {exit 400,'质量问题ID不能为空'}
return db.transaction(()=>{
    var issue=db.selectOne('SELECT * FROM dq_quality_issue_sample_t WHERE tenant_id=#{tenantId} AND tid=#{issueId} FOR UPDATE')
    if(issue==null) {exit 404,'质量问题不存在或无权访问'}
    var existing=db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('asset_type','quality_issue').eq('asset_id',issueId).eq('issue_type','QUALITY_REMEDIATION').eq('is_del',0).selectOne()
    if(existing!=null) {return existing}
    var assignee=body?.assigneeOrgId
    if(assignee!=null && assignee!='' && db.table('rm_org_t').where().eq('tenant_id',tenantId).eq('id',assignee).eq('deleted',0).count()!=1) {exit 400,'责任组织不存在或不属于当前租户'}
    var principal=approvalRuntime.principal()
    var now=new Date()
    var row={tid:IdUtil.getSnowflakeNextIdStr(),tenant_id:tenantId,asset_type:'quality_issue',asset_id:issueId,issue_type:'QUALITY_REMEDIATION',issue_code:issue.issueHash,title:'质量整改：'+(issue.columnName||'字段异常'),detail:body?.remark||'由真实质检问题样例创建',status:'OPEN',reporter_id:principal.userId,assignee_org_id:assignee,created_time:now,updated_time:now,is_del:0}
    db.table('asset_workbench_issue_t').primary('tid').insert(row)
    db.table('dq_quality_issue_sample_t').where().eq('tenant_id',tenantId).eq('tid',issueId).update({issue_status:'ASSIGNED',assigned_org_id:assignee,updated_time:now})
    return row
})
