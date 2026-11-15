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
import tableSort
import tableRead
import tenantRuntime

// POST /ods/distribution/task/page 查询当前租户的分发任务。
// 接收分页、任务名称或编码、状态及排序条件，返回持久化任务和同一范围的总数。
// 仅查询当前登录租户的有效记录，保留实际状态与进度，不调用 NiFi 或写入数据。
var sortFields = {
    taskName: 'taskName',
    progress: 'progress',
    createdTime: 'createdTime',
    updatedTime: 'updatedTime',
    startedTime: 'startedTime',
    finishedTime: 'finishedTime'
}
var sortField = tableSort.field(body, sortFields, 'createdTime')
var sortDirection = tableSort.direction(body)

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

var projection = {
    tid: tableRead.column('task', 'tid', 'TEXT'),
    applyFormId: tableRead.column('task', 'apply_form_id', 'TEXT'),
    catalogId: tableRead.column('task', 'catalog_id', 'TEXT'),
    catalogName: tableRead.column('task', 'catalog_name', 'TEXT'),
    sourceTableId: tableRead.column('task', 'source_table_id', 'TEXT'),
    sourceTableName: tableRead.column('task', 'source_table_name', 'TEXT'),
    taskCode: tableRead.column('task', 'task_code', 'TEXT'),
    taskName: tableRead.column('task', 'task_name', 'TEXT'),
    taskStatus: tableRead.column('task', 'task_status', 'TEXT'),
    progress: tableRead.column('task', 'progress', 'NUMBER'),
    executionEngine: tableRead.column('task', 'execution_engine', 'TEXT'),
    executionMode: tableRead.column('task', 'execution_mode', 'TEXT'),
    statusMessage: tableRead.column('task', 'status_message', 'TEXT'),
    startedTime: tableRead.column('task', 'started_time', 'TIME'),
    finishedTime: tableRead.column('task', 'finished_time', 'TIME'),
    createdTime: tableRead.column('task', 'created_time', 'TIME'),
    updatedTime: tableRead.column('task', 'updated_time', 'TIME')
}
var query = tableRead.from('data_distribution_task_t', 'task', tenantId)
query.where(tableRead.equal(tableRead.column('task', 'is_del', 'NUMBER'), 0))

// 有筛选值才加条件，达梦等数据库即使将空字符串视为 NULL，也能正确返回未筛选任务。
if (keyword != '') {
    var pattern = '%' + keyword + '%'
    query.where(tableRead.any([
        tableRead.like(projection.taskName, pattern),
        tableRead.like(projection.taskCode, pattern)
    ]))
}
if (status != '') {
    query.where(tableRead.equal(projection.taskStatus, status))
}

// 数据库端将空值排在末尾，同值按任务 ID 升序，保证翻页顺序稳定。
var page = query.page(projection, projection[sortField], sortDirection, 'tid', pageNum, pageSize)

// 只返回约定的投影字段，保留数值和时间类型，排除方言分页附加的内部行号。
var items = page.rows.map(row => {
    var item = {}
    for (key in projection.keySet()) {
        item[key] = row[key]
    }
    return item
})
return {pageNum: pageNum, pageSize: pageSize, total: page.total, list: items}
