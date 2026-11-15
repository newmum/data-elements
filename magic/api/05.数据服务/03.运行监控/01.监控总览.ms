{
  "properties": {},
  "id": "7f3c1a9e4b2d6c8f0a5e3d7b9c1f2a64",
  "script": null,
  "groupId": "8c4d2f6a1b7e4c3d9a0f5e2b6d8c1a37",
  "name": "01.监控总览",
  "createTime": 1793900000000,
  "updateTime": 1793900000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitor/overview",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"data\": {\n        \"ruleTotal\": 1,\n        \"ruleEnabled\": 1,\n        \"eventTotal\": 0,\n        \"eventPending\": 0,\n        \"callTotal\": 0,\n        \"callFailed\": 0,\n        \"avgDurationMs\": 0,\n        \"maxDurationMs\": 0\n    }\n}",
  "description": "按当前登录租户汇总告警规则、告警事件和服务调用日志的数量与耗时指标。",
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

var ruleRow = db.selectOne("""
select count(1) total,
       coalesce(sum(case when enabled = 1 then 1 else 0 end), 0) enabledCount
from fs_alert_rule
where tenant_id = #{tenantId}
""")
var eventRow = db.selectOne("""
select count(1) total,
       coalesce(sum(case when status = 'triggered' then 1 else 0 end), 0) pendingCount
from fs_alert_event
where tenant_id = #{tenantId}
""")
var callRow = db.selectOne("""
select count(1) total,
       coalesce(sum(case when success = 0 then 1 else 0 end), 0) failedCount,
       coalesce(round(avg(duration_ms)), 0) avgDurationMs,
       coalesce(max(duration_ms), 0) maxDurationMs
from fs_call_log
where tenant_id = #{tenantId}
""")

return {
  ruleTotal: toLong(rowValue(ruleRow, ['total', 'TOTAL'])),
  ruleEnabled: toLong(rowValue(ruleRow, ['enabledCount', 'ENABLEDCOUNT'])),
  eventTotal: toLong(rowValue(eventRow, ['total', 'TOTAL'])),
  eventPending: toLong(rowValue(eventRow, ['pendingCount', 'PENDINGCOUNT'])),
  callTotal: toLong(rowValue(callRow, ['total', 'TOTAL'])),
  callFailed: toLong(rowValue(callRow, ['failedCount', 'FAILEDCOUNT'])),
  avgDurationMs: toLong(rowValue(callRow, ['avgDurationMs', 'AVGDURATIONMS'])),
  maxDurationMs: toLong(rowValue(callRow, ['maxDurationMs', 'MAXDURATIONMS']))
}
