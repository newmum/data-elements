{
  "properties": {},
  "id": "7d2f4a9c1e8b4c6d0a3f5e7b9c1d2a84",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "01.定时任务列表",
  "createTime": 1789000000301,
  "updateTime": 1789000000301,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "GET",
  "parameters": [
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
    },
    {
      "name": "keyword",
      "value": "",
      "description": "任务编码或名称关键字",
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
      "name": "status",
      "value": "",
      "description": "状态过滤",
      "required": false,
      "dataType": "String",
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
  "description": "当前租户定时任务分页查询，一次计数、一次分页，不按行补查。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead

// 接口：01.定时任务列表
// 路由：GET /dws/timer/page
// 业务说明：按当前租户分页查询定时任务；过滤条件只缩小范围，不改变租户边界。

var tenantId = tenantRuntime.id()
var pageNoValue = pageNo == null ? 1 : (pageNo + '')::int(1)
var pageSizeValue = pageSize == null ? 20 : (pageSize + '')::int(1)
if (pageNoValue < 1) { pageNoValue = 1 }
if (pageSizeValue < 1) { pageSizeValue = 20 }
if (pageSizeValue > 100) { pageSizeValue = 100 }
var keywordValue = keyword == null ? '' : (keyword + '').trim()
var statusValue = status == null ? '' : (status + '').trim().toUpperCase()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var timerView = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        timerCode: text(first(row, ['timerCode', 'timercode', 'timer_code'])),
        timerName: text(first(row, ['timerName', 'timername', 'timer_name'])),
        cron: text(first(row, ['cron', 'CRON'])),
        handler: text(first(row, ['handler', 'HANDLER'])),
        paramsJson: text(first(row, ['paramsJson', 'paramsjson', 'params_json'])),
        status: text(first(row, ['status', 'STATUS'])),
        lastFireAt: text(first(row, ['lastFireAt', 'lastfireat', 'last_fire_at'])),
        nextFireAt: text(first(row, ['nextFireAt', 'nextfireat', 'next_fire_at'])),
        lockTtlSeconds: num(first(row, ['lockTtlSeconds', 'lockttlseconds', 'lock_ttl_seconds'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: text(first(row, ['createdTime', 'createdtime', 'created_time'])),
        updatedTime: text(first(row, ['updatedTime', 'updatedtime', 'updated_time']))
    }
}

// 分页和总数读取使用同一租户的相同过滤条件。
var column = (name, type) => tableRead.column('timer', name, type)
var query = tableRead.from('sys_timer_t', 'timer', tenantId)
query.where(tableRead.equal(column('is_del', 'NUMBER'), 0))
if (statusValue != '') {
    if (!['ENABLED', 'DISABLED'].contains(statusValue)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    query.where(tableRead.equal(column('status', 'TEXT'), statusValue))
}
if (keywordValue != '') {
    var pattern = '%' + keywordValue + '%'
    query.where(tableRead.any([tableRead.like(column('timer_code', 'TEXT'), pattern),
                               tableRead.like(column('timer_name', 'TEXT'), pattern)]))
}
var page = query.page({
    tid: column('tid', 'TEXT'),
    timer_code: column('timer_code', 'TEXT'),
    timer_name: column('timer_name', 'TEXT'),
    cron: column('cron', 'TEXT'),
    handler: column('handler', 'TEXT'),
    params_json: column('params_json', 'TEXT'),
    status: column('status', 'TEXT'),
    last_fire_at: column('last_fire_at', 'TIME'),
    next_fire_at: column('next_fire_at', 'TIME'),
    lock_ttl_seconds: column('lock_ttl_seconds', 'NUMBER'),
    description: column('description', 'TEXT'),
    created_time: column('created_time', 'TIME'),
    updated_time: column('updated_time', 'TIME')
}, column('created_time', 'TIME'), 'desc', 'tid', 'asc', pageNoValue, pageSizeValue, true)
return { list: page.rows.map(row => timerView(row)), total: page.total, pageNo: pageNoValue, pageSize: pageSizeValue }
