{
  "properties" : { },
  "id" : "33cd261177224b5f9ddb886d5df1b1e4",
  "script" : null,
  "groupId" : "1f4028f20aaa4f379b5e37a343b99fa9",
  "name" : "22.登记表接入任务启停",
  "createTime" : 1792474546594,
  "updateTime" : null,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/registrationAccessControl",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "登记第五步单项启停；启动按需自动部署已保存配置，部署失败不启动，保留租户授权、版本核验及清空确认边界。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
/*
 * 功能：部门用户启动或停止当前登记表由管理员配置的一个直接任务。
 * 输入：datasourceId、tableId、taskId、action=start|stop；dryRun只验证、不调用NiFi。
 * 输出：accepted、taskId、action及刷新后的当前表摘要；指令接受不伪造运行成功。
 * 权限：当前租户、登记源角色范围及精确任务关联；不接受外部pipeline/PG标识。
 * 数据：Magic只读业务记录；既有生命周期组件执行单项启停并维护运行状态。
 * 约束：启动按需自动部署当前保存配置；不强制重建、不确认清空数据、不批量启停。
 * 并发：先批量装载全部授权前提，再调用一次技术组件；无循环内查询。
 * 异常：部署失败即中止，不再调用启动；配置、队列、版本和NiFi错误如实报告。
 */
import '@/common/registrationAccessSummary' as registrationAccessSummary
import taskLifecycle
import com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController as pipelineDeployment
var action = body?.action == null ? '' : body.action + ''
if (!['start','stop'].contains(action)) { exit 400, '请选择启动或停止任务' }
var result = registrationAccessSummary({datasourceId:body?.datasourceId,tableIds:[body?.tableId]})
var row = result.list[0]
var matches = row.tasks.filter(task => task.tid + '' == body?.taskId + '')
if (matches.size() != 1) { exit 403, '任务不属于当前登记表或已不可操作' }
var task = matches[0]
if (action == 'start' && task.canStart != true) { exit 409, task.actionHint || '当前任务不能启动' }
if (action == 'stop' && task.canStop != true) { exit 409, task.actionHint || '当前任务不能停止' }
if (body?.dryRun == true) { return {valid:true,taskId:task.tid,action:action,autoDeploy:action == 'start',deploymentRequired:task.requiresDeployment} }
var outcome = action == 'start' ? taskLifecycle.start(task.pipelineId) : taskLifecycle.stop(task.pipelineId)
var deploymentPerformed = false
// 普通启动保留既有已部署图；只有版本核验明确要求部署时，复用同一生命周期的显式部署能力。
// 不传force或confirmCleanup，配置了清空策略的任务仍会被现有启动检查拒绝。
if (action == 'start' && outcome.accepted != true && outcome.status == 409 && outcome.body?.error == 'deployment-required') {
    var deployment = pipelineDeployment.deploy(task.pipelineId, null)
    if (!deployment.statusCode.is2xxSuccessful()) {
        exit deployment.statusCode.value(), '部署未完成：' + (deployment.body?.message || deployment.body?.error || '请检查任务配置后重试')
    }
    deploymentPerformed = true
    outcome = taskLifecycle.start(task.pipelineId)
}
if (outcome.accepted != true) {
    exit outcome.status, outcome.body?.message || outcome.body?.error || '任务操作失败，请查看任务运行详情'
}
return {accepted:true,taskId:task.tid,action:action,deploymentPerformed:deploymentPerformed,
    summary:registrationAccessSummary({datasourceId:body.datasourceId,tableIds:[body.tableId]})}
