{
  "properties": {},
  "id": "b1813cae042f4fa0a5e08ae30fd00a12",
  "groupId": "dba95bdfae554a9f99abdef9da136a25",
  "name": "01.分发任务分页",
  "path": "/task/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\",\"status\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/distribution/task/page；当前租户分发任务分页，只显示持久化任务和真实状态，不合成 NiFi 执行量。",
  "script": null
}
================================
import tenantRuntime

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后查看分发任务'
}
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : (pageSize > 100 ? 100 : pageSize)
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
var status = body?.status == null ? '' : ('' + body.status).trim()
if (keyword.length() > 100 || status.length() > 40) {
    exit 400, '查询条件过长'
}
var keywordPattern = '%' + keyword + '%'
var result = db.page("""
    SELECT tid,
           apply_form_id AS applyFormId,
           catalog_id AS catalogId,
           catalog_name AS catalogName,
           source_table_id AS sourceTableId,
           source_table_name AS sourceTableName,
           task_code AS taskCode,
           task_name AS taskName,
           task_status AS taskStatus,
           progress,
           execution_engine AS executionEngine,
           execution_mode AS executionMode,
           status_message AS statusMessage,
           started_time AS startedTime,
           finished_time AS finishedTime,
           created_time AS createdTime,
           updated_time AS updatedTime
      FROM data_distribution_task_t
     WHERE tenant_id = #{tenantId}
       AND is_del = 0
       AND (#{keyword} = '' OR task_name LIKE #{keywordPattern}
            OR task_code LIKE #{keywordPattern})
       AND (#{status} = '' OR task_status = #{status})
     ORDER BY created_time DESC, tid DESC
""", pageSize, (pageNum - 1) * pageSize)
var items = []
var value = (row, key) => {
    if (row[key] != null) { return row[key] }
    if (row[key.toLowerCase()] != null) { return row[key.toLowerCase()] }
    return row[key.toUpperCase()]
}
for (row in (result.list == null ? [] : result.list)) {
    items.add({
        tid: value(row, 'tid'),
        applyFormId: value(row, 'applyFormId'),
        catalogId: value(row, 'catalogId'),
        catalogName: value(row, 'catalogName'),
        sourceTableId: value(row, 'sourceTableId'),
        sourceTableName: value(row, 'sourceTableName'),
        taskCode: value(row, 'taskCode'),
        taskName: value(row, 'taskName'),
        taskStatus: value(row, 'taskStatus'),
        progress: value(row, 'progress'),
        executionEngine: value(row, 'executionEngine'),
        executionMode: value(row, 'executionMode'),
        statusMessage: value(row, 'statusMessage'),
        startedTime: value(row, 'startedTime'),
        finishedTime: value(row, 'finishedTime'),
        createdTime: value(row, 'createdTime'),
        updatedTime: value(row, 'updatedTime')
    })
}
return { pageNum: pageNum, pageSize: pageSize, total: result.total, list: items }
