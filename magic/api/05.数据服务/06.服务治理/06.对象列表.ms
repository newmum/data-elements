{
  "properties": {},
  "id": "d2e6b6a85fd549718eace4d68b108af6",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "06.对象列表",
  "createTime": 1789000000006,
  "updateTime": 1789000000006,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lake/zones/{id}/objects",
  "method": "GET",
  "parameters": [
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
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0,\n  \"pageNo\": 1,\n  \"pageSize\": 20\n}",
  "description": "分层对象分页列表（GET /dws/service/lake/zones/{id}/objects）。先校验分层属于当前租户，再一次计数、一次分页查询。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.对象列表
// 路由：GET /dws/service/lake/zones/{id}/objects
// 业务说明：分页查询分层下已纳管对象；分层归属先校验，查询固定两条 SQL。
import tenantRuntime

var tenantId = tenantRuntime.id()
var zoneId = path?.id == null ? '' : (path.id + '').trim()
var pageNoValue = pageNo == null ? 1 : (pageNo + '')::int(1)
var pageSizeValue = pageSize == null ? 20 : (pageSize + '')::int(1)
if (pageNoValue < 1) { pageNoValue = 1 }
if (pageSizeValue < 1) { pageSizeValue = 20 }
if (pageSizeValue > 100) { pageSizeValue = 100 }
var lifecycleStatusValue = lifecycleStatus == null ? '' : (lifecycleStatus + '').trim().toUpperCase()
var objectTypeValue = objectType == null ? '' : (objectType + '').trim().toUpperCase()
var keywordValue = keyword == null ? '' : (keyword + '').trim()

var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

if (zoneId == '') { exit 400, '分层标识不能为空' }
var zoneRows = db.select("select tid from lake_zone_t where tid = #{zoneId} and tenant_id = #{tenantId} and is_del = 0", { zoneId: zoneId, tenantId: tenantId })
if (zoneRows == null || zoneRows.size() == 0) { exit 404, '分层不存在或不在当前租户' }

var whereSql = " where tenant_id = #{tenantId} and zone_id = #{zoneId} and is_del = 0 "
if (lifecycleStatusValue != '') {
    whereSql += " and lifecycle_status = '" + lifecycleStatusValue.replace("'", "''") + "' "
}
if (objectTypeValue != '') {
    whereSql += " and object_type = '" + objectTypeValue.replace("'", "''") + "' "
}
if (keywordValue != '') {
    var kw = "'%" + keywordValue.replace("'", "''") + "%'"
    whereSql += " and (object_ref like " + kw + " or object_name like " + kw + ") "
}
var countRows = db.select("select count(1) as total from lake_zone_object_t " + whereSql, { tenantId: tenantId, zoneId: zoneId })
var total = countRows == null || countRows.size() == 0 ? 0 : num(first(countRows[0], ['total', 'TOTAL']))
var offset = (pageNoValue - 1) * pageSizeValue
var rows = db.select("""
  select tid as id, tid, zone_id as zoneId, object_type as objectType, object_ref as objectRef,
         object_name as objectName, source_id as sourceId, lifecycle_status as lifecycleStatus,
         last_sync_at as lastSyncAt, registered_by as registeredBy, created_time as createdTime, updated_time as updatedTime
    from lake_zone_object_t
""" + whereSql + " order by created_time desc, object_ref asc LIMIT " + pageSizeValue + " OFFSET " + offset, { tenantId: tenantId, zoneId: zoneId })

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
