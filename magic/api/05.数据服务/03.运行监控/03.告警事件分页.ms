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
  "path": "/monitor/alert/events",
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
var status = body == null || body.status == null ? '' : (body.status + '').trim()
var offset = (pageNum - 1) * pageSize
var where = " where tenant_id = #{tenantId}"
if (status == 'triggered' || status == 'handled') {
  where = where + " and status = #{status}"
}
if (keyword != '') {
  where = where + " and instr(lower(ifnull(rule_name, '')), lower(#{keyword})) > 0"
}

var totalRow = db.selectOne("select count(1) total from fs_alert_event" + where)
var rows = db.select("select id, rule_id ruleId, rule_name ruleName, metric, actual_value actualValue, threshold_value thresholdValue, notify_target notifyTarget, status, triggered_at triggeredAt from fs_alert_event" + where + " order by triggered_at desc, id desc limit #{pageSize} offset #{offset}")

var list = []
for (row in rows) {
  list.add({
    id: text(rowValue(row, ['id', 'ID'])),
    ruleId: text(rowValue(row, ['ruleId', 'RULEID', 'rule_id', 'RULE_ID'])),
    ruleName: text(rowValue(row, ['ruleName', 'RULENAME', 'rule_name', 'RULE_NAME'])),
    metric: text(rowValue(row, ['metric', 'METRIC'])),
    actualValue: rowValue(row, ['actualValue', 'ACTUALVALUE', 'actual_value', 'ACTUAL_VALUE']),
    thresholdValue: rowValue(row, ['thresholdValue', 'THRESHOLDVALUE', 'threshold_value', 'THRESHOLD_VALUE']),
    notifyTarget: text(rowValue(row, ['notifyTarget', 'NOTIFYTARGET', 'notify_target', 'NOTIFY_TARGET'])),
    status: text(rowValue(row, ['status', 'STATUS'])),
    triggeredAt: text(rowValue(row, ['triggeredAt', 'TRIGGEREDAT', 'triggered_at', 'TRIGGERED_AT']))
  })
}

return { total: toLong(rowValue(totalRow, ['total', 'TOTAL'])), pageNum: pageNum, pageSize: pageSize, list: list }
