{
  "properties": {},
  "id": "d2e6b6a85fd549718eace4d68b108af6",
  "script": null,
  "groupId": "11f572cb355b47ec94d4da477e16dc26",
  "name": "01.对象列表",
  "createTime": 1789000000006,
  "updateTime": 1789000000006,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "GET",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的湖仓分层或对象标识"
    },
    {
      "name": "pageNo",
      "value": "1",
      "description": "查询参数",
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
      "description": "查询参数",
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
      "name": "lifecycleStatus",
      "value": "",
      "description": "查询参数",
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
      "name": "objectType",
      "value": "",
      "description": "查询参数",
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
      "name": "keyword",
      "value": "",
      "description": "查询参数",
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
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0,\n  \"pageNo\": 1,\n  \"pageSize\": 20\n}",
  "description": "分层对象分页列表（GET /dws/lakeobject/page?id={id}）。先校验分层属于当前租户，再一次计数、一次分页查询。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead

// 接口：01.对象列表
// 路由：GET /dws/lakeobject/page?id={id}
// 业务说明：分页查询分层下已纳管对象；分层归属先校验，查询固定两条 SQL。

var tenantId = tenantRuntime.id()
var zoneId = param?.id == null ? '' : (param.id + '').trim()
var pageNoValue = param.pageNo == null ? 1 : (param.pageNo + '')::int(1)
var pageSizeValue = param.pageSize == null ? 20 : (param.pageSize + '')::int(1)
if (pageNoValue < 1) { pageNoValue = 1 }
if (pageSizeValue < 1) { pageSizeValue = 20 }
if (pageSizeValue > 100) { pageSizeValue = 100 }
var lifecycleStatusValue = param.lifecycleStatus == null ? '' : (param.lifecycleStatus + '').trim().toUpperCase()
var objectTypeValue = param.objectType == null ? '' : (param.objectType + '').trim().toUpperCase()
var keywordValue = param.keyword == null ? '' : (param.keyword + '').trim()

var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

if (zoneId == '') { exit 400, '分层标识不能为空' }
var zone = db.table('lake_zone_t').where().eq('tid', zoneId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (zone == null) { exit 404, '分层不存在或不在当前租户' }

// 分页和计数使用同一租户、分层和活动状态条件。通配符沿用原搜索语义。
var column = (name, type) => tableRead.column('object', name, type)
var query = tableRead.from('lake_zone_object_t', 'object', tenantId)
query.where(tableRead.equal(column('zone_id', 'TEXT'), zoneId))
query.where(tableRead.equal(column('is_del', 'NUMBER'), 0))
if (lifecycleStatusValue != '') {
    query.where(tableRead.equal(column('lifecycle_status', 'TEXT'), lifecycleStatusValue))
}
if (objectTypeValue != '') {
    query.where(tableRead.equal(column('object_type', 'TEXT'), objectTypeValue))
}
var objectRef = column('object_ref', 'TEXT')
var objectName = column('object_name', 'TEXT')
if (keywordValue != '') {
    var pattern = '%' + keywordValue + '%'
    query.where(tableRead.any([tableRead.like(objectRef, pattern), tableRead.like(objectName, pattern)]))
}
var createdTime = column('created_time', 'TIME')
var page = query.page({
    tid: column('tid', 'TEXT'), zone_id: column('zone_id', 'TEXT'),
    object_type: column('object_type', 'TEXT'), object_ref: objectRef, object_name: objectName,
    source_id: column('source_id', 'TEXT'), lifecycle_status: column('lifecycle_status', 'TEXT'),
    last_sync_at: column('last_sync_at', 'TIME'), registered_by: column('registered_by', 'TEXT'),
    created_time: createdTime, updated_time: column('updated_time', 'TIME')
}, createdTime, 'desc', 'object_ref', 'asc', pageNoValue, pageSizeValue, true)
var total = page.total
var rows = page.rows

var list = []
for (row in rows) {
    list.add({
        id: text(first(row, ['id', 'tid'])),
        zoneId: text(first(row, ['zoneId', 'zoneid', 'zone_id'])),
        objectType: text(first(row, ['objectType', 'objecttype', 'object_type'])),
        objectRef: text(first(row, ['objectRef', 'objectref', 'object_ref'])),
        objectName: text(first(row, ['objectName', 'objectname', 'object_name'])),
        sourceId: text(first(row, ['sourceId', 'sourceid', 'source_id'])),
        lifecycleStatus: text(first(row, ['lifecycleStatus', 'lifecyclestatus', 'lifecycle_status'])),
        lastSyncAt: first(row, ['lastSyncAt', 'lastsyncat', 'last_sync_at']),
        registeredBy: text(first(row, ['registeredBy', 'registeredby', 'registered_by'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time']),
        updatedTime: first(row, ['updatedTime', 'updatedtime', 'updated_time'])
    })
}
return { list: list, total: total, pageNo: pageNoValue, pageSize: pageSizeValue }
