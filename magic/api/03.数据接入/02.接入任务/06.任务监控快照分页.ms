{
  "properties": {},
  "id": "f541570df3bc4bf3a38e9466d2e16cd7",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "06.任务监控快照分页",
  "createTime": 1784439136875,
  "updateTime": 1784439136875,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitorSnapPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"taskId\": \"测试\",\n    \"pipelineId\": \"测试\",\n    \"processGroupId\": \"测试\",\n    \"delayLevel\": \"测试\",\n    \"monitorStatus\": 2,\n    \"beginTime\": \"2026-07-20 10:00:00\",\n    \"endTime\": \"2026-07-20 10:00:00\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤；使用请求参数 pageNum、pageSize、taskId、pipelineId、processGroupId、delayLevel、monitorStatus、beginTime；涉及 data_access_task_monitor_snap_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.任务监控快照分页
// 路由：POST /ods/task/monitorSnapPage
// 业务说明：支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤；使用请求参数 pageNum、pageSize、taskId、pipelineId、processGroupId、delayLevel、monitorStatus、beginTime；涉及 data_access_task_monitor_snap_t 等业务表。
/*
 * 数据接入任务运行快照分页
 *
 * 支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤。
 * 本接口只读持久化快照，不触发 NiFi 运行时调用。
 */
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : pageSize

// 从 MySQL 业务表读取当前有效数据。
var query = db.table('data_access_task_monitor_snap_t').where().eq('is_del', 0)
if (body?.taskId != null && body.taskId != '') {
    query.eq('task_id', body.taskId)
}
if (body?.pipelineId != null && body.pipelineId != '') {
    query.eq('pipeline_id', body.pipelineId)
}
if (body?.processGroupId != null && body.processGroupId != '') {
    query.eq('process_group_id', body.processGroupId)
}
if (body?.delayLevel != null && body.delayLevel != '') {
    query.eq('delay_level', body.delayLevel)
}
if (body?.monitorStatus != null && body.monitorStatus != '') {
    query.eq('monitor_status', body.monitorStatus)
}
if (body?.beginTime != null && body.beginTime != '') {
    query.ge('monitor_time', body.beginTime)
}
if (body?.endTime != null && body.endTime != '') {
    query.le('monitor_time', body.endTime)
}

var result = query.orderBy('monitor_time', 'desc')
    .orderBy('created_time', 'desc')
    .page(pageSize, (pageNum - 1) * pageSize)
result.list = result.list || []
// 返回当前业务分支的标准结果。
return result
