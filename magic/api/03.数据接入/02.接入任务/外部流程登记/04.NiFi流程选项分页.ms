{
  "properties": {},
  "id": "28d9a96c33d34c84a628add5d44d7b8e",
  "groupId": "a6b70761965047169f1ce26f877ebac7",
  "name": "04.NiFi流程选项分页",
  "path": "/pipeline-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/task/registry/pipeline-options；按当前租户分页搜索可登记的 NiFi Canvas DSL v1 流程，只返回选择框所需元数据。",
  "script": null
}
================================
import tenantRuntime

/**
 * 可登记的 NiFi 流程选项。按当前租户、关键字和分页读取已保存的 Canvas DSL v1 流程。
 * 返回选项需要的 ID、名称、状态和运行组信息。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后选择NiFi流程'
}
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
var keyword = body?.keyword == null ? '' : ('' + body.keyword).trim()
if (pageNum < 1 || pageNum > 1000 || pageSize < 1 || pageSize > 100) {
    exit 400, '页码或每页条数超出限制，请使用搜索缩小范围'
}
if (keyword.length() > 100) {
    exit 400, '搜索词不能超过100字'
}
// 摘要至少有一个字符。用固定匹配模式兼容把空串视为 NULL 的数据库。
var pipelines = db.camel().table('nifi_pipeline_t')
    .columns('id', 'name', 'status', 'nifi_process_group_id')
    .where().eq('tenant_id', tenantId).eq('is_del', 0).eq('dsl_version', 1)
    .isNotNull('dsl_hash').like('dsl_hash', '_%')
if (keyword != '') {
    var pattern = '%' + keyword + '%'
    pipelines.and(match => match.like('name', pattern).or().like('id', pattern))
}

// 沿用数据库的更新时间降序，更新时间相同的流程按 ID 降序。
var page = pipelines.orderByDesc('updated_at').orderByDesc('id')
    .page(pageSize, (pageNum - 1) * pageSize)
var items = []
for (row in (page.list == null ? [] : page.list)) {
    items.add({id: row.id, name: row.name, status: row.status, processGroupId: row.nifiProcessGroupId})
}
return {pageNum: pageNum, pageSize: pageSize, total: page.total, list: items}
