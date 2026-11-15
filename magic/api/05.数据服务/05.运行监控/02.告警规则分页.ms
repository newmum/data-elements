{
  "properties": {},
  "id": "1b8e5d3c7a9f4e2d6c0b8a5f3e7d9c21",
  "script": null,
  "groupId": "8c4d2f6a1b7e4c3d9a0f5e2b6d8c1a37",
  "name": "02.告警规则分页",
  "createTime": 1793900000000,
  "updateTime": 1793900000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/rules",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 10,\n    \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"data\": {\n        \"total\": 1,\n        \"pageNum\": 1,\n        \"pageSize\": 10,\n        \"list\": []\n    }\n}",
  "description": "按当前登录租户分页查询服务告警规则。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// POST /dws/monitor/rules 告警规则分页。按当前登录租户分页查询服务告警规则。
var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : (value + '').trim()
var pageNum = body?.pageNum == null ? 1 : (body.pageNum + '')::int
var pageSize = body?.pageSize == null ? 10 : (body.pageSize + '')::int
pageNum = Math.max(1, pageNum)
pageSize = pageSize < 1 ? 10 : Math.min(100, pageSize)
var keyword = text(body?.keyword)

// 当前认证行业租户作为查询范围，请求中的租户字段不参与授权。
var query = tableRead.from('fs_alert_rule', 'ruleset', tenantId)
var field = (name, kind) => tableRead.column('ruleset', name, kind)
if (keyword != '') {
    query.where(tableRead.contains(field('name', 'TEXT'), keyword))
}

// 计数和数据页沿用同一筛选，按原有时间和 ID 顺序读取。
var page = query.page({
    id: field('id', 'NUMBER'),
    name: field('name', 'TEXT'),
    metric: field('metric', 'TEXT'),
    operator: field('operator', 'TEXT'),
    thresholdValue: field('threshold_value', 'NUMBER'),
    durationMinutes: field('duration_minutes', 'NUMBER'),
    notifyTarget: field('notify_target', 'TEXT'),
    enabled: field('enabled', 'NUMBER'),
    createdAt: field('created_at', 'TIME')
}, field('created_at', 'TIME'), 'desc', 'id', 'asc', pageNum, pageSize, true)
var list = []
for (row in page.rows) {
    list.add({
        id: text(row.id),
        name: text(row.name),
        metric: text(row.metric),
        operator: text(row.operator),
        thresholdValue: row.thresholdValue,
        durationMinutes: (row.durationMinutes == null ? 0 : row.durationMinutes)::int,
        notifyTarget: text(row.notifyTarget),
        enabled: (row.enabled == null ? 0 : row.enabled)::int,
        createdAt: text(row.createdAt)
    })
}
return {total: page.total, pageNum: pageNum, pageSize: pageSize, list: list}
