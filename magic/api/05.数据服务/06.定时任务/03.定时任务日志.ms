{
  "properties": {},
  "id": "9f4b6c1e3a2d4f8b0c7d1e5a9b3f6c82",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "03.定时任务日志",
  "createTime": 1789000000303,
  "updateTime": 1789000000303,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logs",
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
import tenantRuntime
import tableRead

// 接口：03.定时任务日志
// 路由：GET /dws/timer/logs
// 业务说明：按当前租户查询执行日志；timerId 为空时查询当前租户全部任务日志。

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

// 分页和总数读取使用同一租户的相同过滤条件。
var column = (name, type) => tableRead.column('timer', name, type)
var query = tableRead.from('sys_timer_log_t', 'timer', tenantId)
if (timerIdValue != '') {
    // 日志按本租户的历史任务 ID 过滤，任务删除后仍可回读日志。
    query.where(tableRead.equal(column('timer_id', 'TEXT'), timerIdValue))
}
var page = query.page({
    tid: column('tid', 'TEXT'),
    timer_id: column('timer_id', 'TEXT'),
    timer_code: column('timer_code', 'TEXT'),
    fire_at: column('fire_at', 'TIME'),
    result: column('result', 'TEXT'),
    cost_ms: column('cost_ms', 'NUMBER'),
    error_msg: column('error_msg', 'TEXT'),
    created_time: column('created_time', 'TIME')
}, column('fire_at', 'TIME'), 'desc', 'tid', 'asc', pageNoValue, pageSizeValue, true)
return { list: page.rows.map(row => logView(row)), total: page.total, pageNo: pageNoValue, pageSize: pageSizeValue }
