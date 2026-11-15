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
  "path": "/overview",
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
import java.math.BigDecimal
import java.math.RoundingMode
import tableRead
import tenantRuntime

// POST /dws/monitor/overview 监控总览。按当前登录租户汇总告警规则、告警事件和服务调用日志的数量与耗时指标。

var tenantId = tenantRuntime.id()
var toLong = value => value == null ? 0L : (value + '')::long

// 三类指标分别从认证租户的规则、事件和调用日志聚合，避免逐行统计。
var rules = tableRead.from('fs_alert_rule', 'ruleset', tenantId)
var enabled = tableRead.column('ruleset', 'enabled', 'NUMBER')
var ruleSummary = rules.grouped({
    total: tableRead.count(),
    enabled: tableRead.countIf(tableRead.equal(enabled, 1))
}, [])[0]

var events = tableRead.from('fs_alert_event', 'alert', tenantId)
var status = tableRead.column('alert', 'status', 'TEXT')
var eventSummary = events.grouped({
    total: tableRead.count(),
    pending: tableRead.countIf(tableRead.equal(status, 'triggered'))
}, [])[0]

var calls = tableRead.from('fs_call_log', 'calls', tenantId)
var duration = tableRead.column('calls', 'duration_ms', 'NUMBER')
var success = tableRead.column('calls', 'success', 'NUMBER')
var callSummary = calls.grouped({
    total: tableRead.count(),
    failed: tableRead.countIf(tableRead.equal(success, 0)),
    durationTotal: tableRead.sum(duration),
    durationCount: tableRead.count(duration),
    maximum: tableRead.max(duration)
}, [])[0]

// 跳过 NULL 耗时，半毫秒按原有四舍五入规则处理。
var average = 0L
if (callSummary.durationCount > 0) {
    average = new BigDecimal(callSummary.durationTotal + '')
        .divide(new BigDecimal(callSummary.durationCount + ''), 0, RoundingMode.HALF_UP).longValue()
}
return {
    ruleTotal: toLong(ruleSummary.total),
    ruleEnabled: toLong(ruleSummary.enabled),
    eventTotal: toLong(eventSummary.total),
    eventPending: toLong(eventSummary.pending),
    callTotal: toLong(callSummary.total),
    callFailed: toLong(callSummary.failed),
    avgDurationMs: average,
    maxDurationMs: toLong(callSummary.maximum)
}
