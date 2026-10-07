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
var keywordPattern = '%' + keyword + '%'
var result = db.page("""
    SELECT r.tid,
           r.external_flow_id AS externalFlowId,
           r.pipeline_id AS pipelineId,
           p.name AS pipelineName,
           p.status AS pipelineStatus,
           p.nifi_process_group_id AS processGroupId,
           r.source_format AS sourceFormat,
           r.created_time AS createdTime
      FROM nifi_pipeline_external_ref_t r
      JOIN nifi_pipeline_t p ON p.id = r.pipeline_id AND p.tenant_id = r.tenant_id AND p.is_del = 0
     WHERE r.tenant_id = #{tenantId}
       AND (#{keyword} = '' OR r.external_flow_id LIKE #{keywordPattern}
            OR p.name LIKE #{keywordPattern})
     ORDER BY r.created_time DESC, r.tid DESC
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
        externalFlowId: value(row, 'externalFlowId'),
        pipelineId: value(row, 'pipelineId'),
        pipelineName: value(row, 'pipelineName'),
        pipelineStatus: value(row, 'pipelineStatus'),
        processGroupId: value(row, 'processGroupId'),
        sourceFormat: value(row, 'sourceFormat'),
        createdTime: value(row, 'createdTime')
    })
}
return { pageNum: pageNum, pageSize: pageSize, total: result.total, list: items }
