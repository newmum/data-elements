{
  "properties": {},
  "id": "9a2f6c4e1d8b3a7f5e0c9b2d4a6f8c31",
  "script": null,
  "groupId": "8c4d2f6a1b7e4c3d9a0f5e2b6d8c1a37",
  "name": "04.调用日志分页",
  "createTime": 1793900000000,
  "updateTime": 1793900000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/calls",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 10,\n    \"success\": null,\n    \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"data\": {\n        \"total\": 0,\n        \"pageNum\": 1,\n        \"pageSize\": 10,\n        \"list\": []\n    }\n}",
  "description": "按当前登录租户分页查询已发布数据服务的调用日志，支持成功状态与服务名称筛选。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// POST /dws/monitor/calls 调用日志分页。按当前登录租户分页查询已发布数据服务的调用日志，支持成功状态与服务名称筛选。
var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : (value + '').trim()
var pageNum = body?.pageNum == null ? 1 : (body.pageNum + '')::int
var pageSize = body?.pageSize == null ? 10 : (body.pageSize + '')::int
pageNum = Math.max(1, pageNum)
pageSize = pageSize < 1 ? 10 : Math.min(100, pageSize)
var keyword = text(body?.keyword)

// 当前认证行业租户作为查询范围，请求中的租户字段不参与授权。
var query = tableRead.from('fs_call_log', 'calls', tenantId)
var field = (name, kind) => tableRead.column('calls', name, kind)
var success = body?.success == null ? null : (body.success + '')::int
if (success != null && (success == 0 || success == 1)) {
    query.where(tableRead.equal(field('success', 'NUMBER'), success))
}
if (keyword != '') {
    query.where(tableRead.any([
        tableRead.contains(field('service_name', 'TEXT'), keyword),
        tableRead.contains(field('path', 'TEXT'), keyword)
    ]))
}

// 计数和数据页沿用同一筛选，按原有时间和 ID 顺序读取。
var page = query.page({
    id: field('id', 'NUMBER'),
    flowId: field('flow_id', 'TEXT'),
    serviceName: field('service_name', 'TEXT'),
    method: field('method', 'TEXT'),
    path: field('path', 'TEXT'),
    status: field('status', 'NUMBER'),
    success: field('success', 'NUMBER'),
    durationMs: field('duration_ms', 'NUMBER'),
    createdAt: field('created_at', 'TIME')
}, field('created_at', 'TIME'), 'desc', 'id', 'desc', pageNum, pageSize, true)
var list = []
for (row in page.rows) {
    list.add({
        id: text(row.id),
        flowId: text(row.flowId),
        serviceName: text(row.serviceName),
        method: text(row.method),
        path: text(row.path),
        status: (row.status == null ? 0 : row.status)::int,
        success: (row.success == null ? 0 : row.success)::int,
        durationMs: (row.durationMs == null ? 0 : row.durationMs)::long,
        createdAt: text(row.createdAt)
    })
}
return {total: page.total, pageNum: pageNum, pageSize: pageSize, list: list}
