{
  "properties": {},
  "id": "wx_quality_work_transition_01",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "20.推进质量工单",
  "createTime": 1789337365935,
  "updateTime": 1790498524318,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/workorders/transition",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "核验当前租户的质量整改工单与动作；支持开始处理、提交复检及在新质检结果满足要求后关闭，并记录意见和状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：20.推进质量工单
// 路由：POST /dwm/quality/workorders/transition
// 业务说明：核验当前租户的质量整改工单与动作；支持开始处理、提交复检及在新质检结果满足要求后关闭，并记录意见和状态。
import tenantRuntime
import approvalRuntime
// Shared remediation state machine. Current tenant only; closing requires a newer real rule result.
var tenantId=tenantRuntime.id()
var orderId=body?.orderId
var action=body?.action
if(orderId==null || orderId=='') {exit 400,'工单ID不能为空'}
if(action!='START' && action!='SUBMIT_RECHECK' && action!='CLOSE') {exit 400,'工单动作不合法'}
var expected=action=='START'?'OPEN':action=='SUBMIT_RECHECK'?'IN_PROGRESS':'PENDING_RECHECK'
var next=action=='START'?'IN_PROGRESS':action=='SUBMIT_RECHECK'?'PENDING_RECHECK':'CLOSED'
var opinion=body?.opinion
if(action!='START' && (opinion==null || (opinion+'').trim()=='')) {exit 400,'请填写整改或复检说明'}
var principal=approvalRuntime.principal()
return db.transaction(()=>{
    var row=db.selectOne('SELECT * FROM asset_workbench_issue_t WHERE tenant_id=#{tenantId} AND tid=#{orderId} AND asset_type=\'quality_issue\' AND issue_type=\'QUALITY_REMEDIATION\' AND is_del=0 FOR UPDATE')
    if(row==null) {exit 404,'工单不存在或无权访问'}
    if(row.status!=expected) {exit 409,'工单状态已变化或操作顺序不合法，请刷新后重试'}
    var assignee=body?.assigneeOrgId || row.assigneeOrgId
    if(action=='START') {
        if(assignee==null || assignee=='') {exit 400,'请选择责任组织'}
        if(db.table('rm_org_t').where().eq('tenant_id',tenantId).eq('id',assignee).eq('deleted',0).count()!=1) {exit 400,'责任组织不存在或不属于当前租户'}
    }
    if(action=='CLOSE') {
        var origin=db.selectOne('SELECT i.column_name,i.task_rule_id,m.rule_type,r.task_id,qr.parameters_json FROM dq_quality_issue_sample_t i JOIN dq_quality_metric_t m ON m.run_id=i.run_id AND m.task_rule_id=i.task_rule_id AND m.tenant_id=#{tenantId} JOIN dq_quality_task_rule_t qr ON qr.tid=i.task_rule_id AND qr.tenant_id=#{tenantId} JOIN dq_quality_run_t r ON r.tid=i.run_id AND r.tenant_id=#{tenantId} WHERE i.tid=#{row.assetId} AND i.tenant_id=#{tenantId}')
        if(origin==null) {exit 409,'原始质量问题不存在，不能关闭'}
        // Edits create new rule IDs. Match the original rule meaning, not an obsolete
        // ID/version; an unrelated passing rule or an empty source cannot close a case.
        var checks=db.select('SELECT r.tid,m.violation_count,m.checked_count,qr.parameters_json FROM dq_quality_run_t r JOIN dq_quality_metric_t m ON m.run_id=r.tid AND m.tenant_id=#{tenantId} AND m.column_name=#{origin.columnName} AND m.rule_type=#{origin.ruleType} JOIN dq_quality_task_rule_t qr ON qr.tid=m.task_rule_id AND qr.tenant_id=#{tenantId} WHERE r.tenant_id=#{tenantId} AND r.task_id=#{origin.taskId} AND r.status IN (\'COMPLETED\',\'COMPLETED_WITH_ISSUES\') AND r.created_time>=#{row.updatedTime} AND r.is_del=0 ORDER BY r.finished_at DESC LIMIT 200')
        var passed=false
        if(checks.size()>0){var latestRunId=checks[0].tid;for(check in checks){if(check.tid==latestRunId && (check.parametersJson+'')==(origin.parametersJson+'') && check.checkedCount>0 && check.violationCount==0){passed=true}}}
        if(!passed) {exit 409,'尚无提交复检后的有效通过结果，请先修复源数据并重新执行原任务；需保持原字段、规则类型和参数，空表或仍有异常时不能关闭'}
    }
    var now=new Date()
    db.table('asset_workbench_issue_t').where().eq('tenant_id',tenantId).eq('tid',orderId).eq('status',expected).update({status:next,assignee_org_id:assignee,resolution:opinion,updated_time:now,resolved_time:next=='CLOSED'?now:null},true)
    db.table('dq_quality_issue_sample_t').where().eq('tenant_id',tenantId).eq('tid',row.assetId).update({issue_status:next,assigned_org_id:assignee,handled_by:principal.userId,handled_at:next=='CLOSED'?now:null,handle_remark:opinion,updated_time:now},true)
    return true
})
