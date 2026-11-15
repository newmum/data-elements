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
  "path": "/monitor/alert/rules",
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
import tenantRuntime

var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var expected = (key + '').toLowerCase()
        var found = null
        for (actual in row.keySet()) {
            if ((actual + '').toLowerCase() == expected && row[actual] != null) {
                found = row[actual]
            }
        }
        if (found != null) { return found }
    }
    return null
}
var toLong = value => value == null ? 0 : (value + '')::long
var toInt = value => value == null ? 0 : (value + '')::int

var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '请先登录' }
var pageNum = body == null || body.pageNum == null ? 1 : (body.pageNum + '')::int
var pageSize = body == null || body.pageSize == null ? 10 : (body.pageSize + '')::int
if (pageNum < 1) { pageNum = 1 }
if (pageSize < 1) { pageSize = 10 }
if (pageSize > 100) { pageSize = 100 }
var keyword = body == null || body.keyword == null ? '' : (body.keyword + '').trim()
var offset = (pageNum - 1) * pageSize
var where = " where tenant_id = #{tenantId}"
if (keyword != '') {
  where = where + " and instr(lower(ifnull(name, '')), lower(#{keyword})) > 0"
}

var totalRow = db.selectOne("select count(1) total from fs_alert_rule" + where)
var rows = db.select("select id, name, metric, operator, threshold_value thresholdValue, duration_minutes durationMinutes, notify_target notifyTarget, enabled, created_at createdAt from fs_alert_rule" + where + " order by created_at desc, id asc limit #{pageSize} offset #{offset}")

var list = []
for (row in rows) {
  list.add({
    id: text(rowValue(row, ['id', 'ID'])),
    name: text(rowValue(row, ['name', 'NAME'])),
    metric: text(rowValue(row, ['metric', 'METRIC'])),
    operator: text(rowValue(row, ['operator', 'OPERATOR'])),
    thresholdValue: rowValue(row, ['thresholdValue', 'THRESHOLDVALUE']),
    durationMinutes: toInt(rowValue(row, ['durationMinutes', 'DURATIONMINUTES'])),
    notifyTarget: text(rowValue(row, ['notifyTarget', 'NOTIFYTARGET'])),
    enabled: toInt(rowValue(row, ['enabled', 'ENABLED'])),
    createdAt: text(rowValue(row, ['createdAt', 'CREATEDAT']))
  })
}

return { total: toLong(rowValue(totalRow, ['total', 'TOTAL'])), pageNum: pageNum, pageSize: pageSize, list: list }
