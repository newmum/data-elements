{
  "properties": {},
  "id": "4d6a9c2e8f1b3d7a5c0e9b4f6a2d8c13",
  "script": null,
  "groupId": "8c4d2f6a1b7e4c3d9a0f5e2b6d8c1a37",
  "name": "03.告警事件分页",
  "createTime": 1793900000000,
  "updateTime": 1793900000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/events",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 10,\n    \"status\": \"\",\n    \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"data\": {\n        \"total\": 0,\n        \"pageNum\": 1,\n        \"pageSize\": 10,\n        \"list\": []\n    }\n}",
  "description": "按当前登录租户分页查询告警事件，支持状态与规则名称筛选。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// POST /dws/monitor/events 告警事件分页。按当前登录租户分页查询告警事件，支持状态与规则名称筛选。
var tenantId = tenantRuntime.id()
var text = value => value == null ? '' : (value + '').trim()
var pageNum = body?.pageNum == null ? 1 : (body.pageNum + '')::int
var pageSize = body?.pageSize == null ? 10 : (body.pageSize + '')::int
pageNum = Math.max(1, pageNum)
pageSize = pageSize < 1 ? 10 : Math.min(100, pageSize)
var keyword = text(body?.keyword)

// 当前认证行业租户作为查询范围，请求中的租户字段不参与授权。
var query = tableRead.from('fs_alert_event', 'alert', tenantId)
var field = (name, kind) => tableRead.column('alert', name, kind)
var status = text(body?.status)
if (['triggered', 'handled'].contains(status)) {
    query.where(tableRead.equal(field('status', 'TEXT'), status))
}
if (keyword != '') {
    query.where(tableRead.contains(field('rule_name', 'TEXT'), keyword))
}

// 计数和数据页沿用同一筛选，按原有时间和 ID 顺序读取。
var page = query.page({
    id: field('id', 'NUMBER'),
    ruleId: field('rule_id', 'NUMBER'),
    ruleName: field('rule_name', 'TEXT'),
    metric: field('metric', 'TEXT'),
    actualValue: field('actual_value', 'NUMBER'),
    thresholdValue: field('threshold_value', 'NUMBER'),
    notifyTarget: field('notify_target', 'TEXT'),
    status: field('status', 'TEXT'),
    triggeredAt: field('triggered_at', 'TIME')
}, field('triggered_at', 'TIME'), 'desc', 'id', 'desc', pageNum, pageSize, true)
var list = []
for (row in page.rows) {
    list.add({
        id: text(row.id),
        ruleId: text(row.ruleId),
        ruleName: text(row.ruleName),
        metric: text(row.metric),
        actualValue: row.actualValue,
        thresholdValue: row.thresholdValue,
        notifyTarget: text(row.notifyTarget),
        status: text(row.status),
        triggeredAt: text(row.triggeredAt)
    })
}
return {total: page.total, pageNum: pageNum, pageSize: pageSize, list: list}
