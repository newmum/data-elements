{
  "properties": {},
  "id": "94c9cc1afcd742a6a2486fce18adaa77",
  "groupId": "a6b70761965047169f1ce26f877ebac7",
  "name": "03.登记记录分页",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/task/registry/page；当前租户真实NiFi流程的外部ID登记历史，不包含本地IndexedDB演示登记。",
  "script": null
}
================================
import tenantRuntime
import tableSort
import tableRead

/**
 * 外部流程登记历史。按当前租户分页读取仍关联有效 NiFi 流程的登记记录。
 * 支持关键字及外部流程 ID、流程名称和登记时间排序，返回总数与当页登记信息。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后查看登记记录'
}
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : (pageSize > 100 ? 100 : pageSize)
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
if (keyword.length() > 100) {
    exit 400, '搜索词过长'
}
var sortFields = {externalFlowId: 'externalFlowId', pipelineName: 'pipelineName', createdTime: 'createdTime'}
var sortField = tableSort.field(body, sortFields, 'createdTime')
var direction = tableSort.direction(body)
var projection = {
    tid: tableRead.column('r', 'tid', 'TEXT'),
    externalFlowId: tableRead.column('r', 'external_flow_id', 'TEXT'),
    pipelineId: tableRead.column('r', 'pipeline_id', 'TEXT'),
    pipelineName: tableRead.column('p', 'name', 'TEXT'),
    pipelineStatus: tableRead.column('p', 'status', 'TEXT'),
    processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
    sourceFormat: tableRead.column('r', 'source_format', 'TEXT'),
    createdTime: tableRead.column('r', 'created_time', 'TIME')
}
var registrations = tableRead.from('nifi_pipeline_external_ref_t', 'r', tenantId)
// 只保留与当前租户有效流程关联的登记，两次分页读取使用相同条件。
var activePipeline = tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0)
registrations.leftJoin('nifi_pipeline_t', 'p', 'id', 'pipeline_id', [activePipeline])
registrations.where(tableRead.not(tableRead.isNull(tableRead.column('p', 'id', 'TEXT'))))
if (keyword != '') {
    var pattern = '%' + keyword + '%'
    registrations.where(tableRead.any([
        tableRead.like(projection.externalFlowId, pattern),
        tableRead.like(projection.pipelineName, pattern)
    ]))
}

// 可选流程名称的空值排在末尾，同值按登记 ID 升序。
var order = sortField == 'pipelineName' ? tableRead.blankAsNull(projection.pipelineName) : projection[sortField]
var page = registrations.page(projection, order, direction, 'tid', pageNum, pageSize)
return {pageNum: pageNum, pageSize: pageSize, total: page.total, list: page.rows}
