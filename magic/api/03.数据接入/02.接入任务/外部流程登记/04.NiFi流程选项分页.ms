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
var keywordPattern = '%' + keyword + '%'
// 单次 db.page 完成 count 与有界当前页查询；没有逐流程读取或 DSL 大字段反序列化。
// NiFi 流程当前的数据范围是租户级；租户 ID 仅从服务端会话获得，忽略任何前端 tenantId。
var result = db.page("""
    SELECT id,
           name,
           status,
           nifi_process_group_id AS processGroupId
      FROM nifi_pipeline_t
     WHERE tenant_id = #{tenantId}
       AND is_del = 0
       AND dsl_version = 1
       AND dsl_hash IS NOT NULL
       AND dsl_hash <> ''
       AND (#{keyword} = '' OR name LIKE #{keywordPattern} OR id LIKE #{keywordPattern})
     ORDER BY updated_at DESC, id DESC
""", pageSize, (pageNum - 1) * pageSize)
var value = (row, key) => {
    if (row[key] != null) { return row[key] }
    if (row[key.toLowerCase()] != null) { return row[key.toLowerCase()] }
    return row[key.toUpperCase()]
}
var items = []
for (row in (result.list == null ? [] : result.list)) {
    items.add({
        id: value(row, 'id'),
        name: value(row, 'name'),
        status: value(row, 'status'),
        processGroupId: value(row, 'processGroupId')
    })
}
return { pageNum: pageNum, pageSize: pageSize, total: result.total, list: items }
