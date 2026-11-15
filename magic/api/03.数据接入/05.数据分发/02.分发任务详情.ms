{
  "properties": {},
  "id": "d3e27504359a499e8514f9728abacc33",
  "groupId": "dba95bdfae554a9f99abdef9da136a25",
  "name": "02.分发任务详情",
  "path": "/task/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tid\":\"分发任务ID\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/distribution/task/detail；读取当前租户分发任务元数据与持久化执行状态，不伪造 NiFi 运行历史。",
  "script": null
}
================================
import tenantRuntime

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后查看分发任务'
}
var tid = body?.tid == null ? '' : ('' + body.tid).trim()
if (!tid.matches('^[A-Za-z0-9_-]{1,64}$')) {
    exit 400, '分发任务ID无效'
}
var row = db.table('data_distribution_task_t').where()
    .eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
if (row == null) {
    exit 404, '分发任务不存在或无权访问'
}
var field = (snake, camel) => row[camel] == null ? (row[snake] == null ? row[snake.toUpperCase()] : row[snake]) : row[camel]
return {
    tid: tid,
    applyFormId: field('apply_form_id', 'applyFormId'),
    catalogId: field('catalog_id', 'catalogId'),
    catalogName: field('catalog_name', 'catalogName'),
    sourceTableId: field('source_table_id', 'sourceTableId'),
    sourceTableName: field('source_table_name', 'sourceTableName'),
    taskCode: field('task_code', 'taskCode'),
    taskName: field('task_name', 'taskName'),
    taskStatus: field('task_status', 'taskStatus'),
    progress: field('progress', 'progress'),
    executionEngine: field('execution_engine', 'executionEngine'),
    executionMode: field('execution_mode', 'executionMode'),
    statusMessage: field('status_message', 'statusMessage'),
    startedTime: field('started_time', 'startedTime'),
    finishedTime: field('finished_time', 'finishedTime'),
    createdTime: field('created_time', 'createdTime'),
    updatedTime: field('updated_time', 'updatedTime')
}
