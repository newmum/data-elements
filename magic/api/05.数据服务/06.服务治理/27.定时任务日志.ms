{
  "properties": {},
  "id": "9f4b6c1e3a2d4f8b0c7d1e5a9b3f6c82",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "27.定时任务日志",
  "createTime": 1789000000303,
  "updateTime": 1789000000303,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sys/timers/logs",
  "method": "GET",
  "parameters": [
    {
      "name": "timerId",
      "value": "",
      "description": "定时任务标识，为空时查询全部",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageNo",
      "value": "1",
      "description": "页码",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageSize",
      "value": "20",
      "description": "每页条数",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "当前租户定时任务执行日志分页查询，按触发时间倒序。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.定时任务日志
// 路由：GET /dws/service/sys/timers/logs
// 业务说明：按当前租户查询执行日志；timerId 为空时查询当前租户全部任务日志。
import tenantRuntime

var tenantId = tenantRuntime.id()
var pageNoValue = pageNo == null ? 1 : (pageNo + '')::int(1)
var pageSizeValue = pageSize == null ? 20 : (pageSize + '')::int(1)
if (pageNoValue < 1) { pageNoValue = 1 }
if (pageSizeValue < 1) { pageSizeValue = 20 }
if (pageSizeValue > 100) { pageSizeValue = 100 }
var timerIdValue = timerId == null ? '' : (timerId + '').trim()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var logView = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        timerId: text(first(row, ['timerId', 'timerid', 'timer_id'])),
        timerCode: text(first(row, ['timerCode', 'timercode', 'timer_code'])),
        fireAt: text(first(row, ['fireAt', 'fireat', 'fire_at'])),
        result: text(first(row, ['result', 'RESULT'])),
        costMs: num(first(row, ['costMs', 'costms', 'cost_ms'])),
        errorMsg: text(first(row, ['errorMsg', 'errormsg', 'error_msg'])),
        createdTime: text(first(row, ['createdTime', 'createdtime', 'created_time']))
    }
}

var whereSql = " where tenant_id = #{tenantId} "
if (timerIdValue != '') {
    whereSql += " and timer_id = '" + timerIdValue.replace("'", "''") + "' "
}
var countRows = db.select("select count(1) as total from sys_timer_log_t " + whereSql, { tenantId: tenantId })
var total = countRows == null || countRows.size() == 0 ? 0 : num(first(countRows[0], ['total', 'TOTAL']))
var offset = (pageNoValue - 1) * pageSizeValue
var rows = db.select("select tid, timer_id, timer_code, fire_at, result, cost_ms, error_msg, created_time from sys_timer_log_t " + whereSql + " order by fire_at desc LIMIT " + pageSizeValue + " OFFSET " + offset, { tenantId: tenantId })
var list = []
for (row in rows) { list.add(logView(row)) }
return { list: list, total: total, pageNo: pageNoValue, pageSize: pageSizeValue }
