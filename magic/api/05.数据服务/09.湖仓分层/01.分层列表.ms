{
  "properties": {},
  "id": "3c03093a32844f6b81e42c2d2619d645",
  "script": null,
  "groupId": "b5badef9297e4dbb8a6028e72a393d4e",
  "name": "01.分层列表",
  "createTime": 1789000000001,
  "updateTime": 1789000000001,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/zones",
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
    },
    {
      "name": "zoneType",
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
      "name": "status",
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
  "description": "湖仓分层分页列表（GET /dws/lake/zones）。按当前租户查询 lake_zone_t，支持 zoneType、status、keyword 过滤；一次计数、一次分页查询，不按行补查。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// 接口：01.分层列表
// 路由：GET /dws/lake/zones
// 业务说明：按当前租户分页查询湖仓分层；过滤条件只缩小范围，不改变租户边界。

var tenantId = tenantRuntime.id()
var pageNoValue = pageNo == null ? 1 : (pageNo + '')::int(1)
var pageSizeValue = pageSize == null ? 20 : (pageSize + '')::int(1)
if (pageNoValue < 1) { pageNoValue = 1 }
if (pageSizeValue < 1) { pageSizeValue = 20 }
if (pageSizeValue > 100) { pageSizeValue = 100 }
var keywordValue = keyword == null ? '' : (keyword + '').trim()
var zoneTypeValue = zoneType == null ? '' : (zoneType + '').trim()
var statusValue = status == null ? '' : (status + '').trim()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

var whereSql = " where z.is_del = 0 AND z.tenant_id = #{tenantId} "
if (keywordValue != '') {
    var kw = "'%" + (keywordValue.replace("'", "''")) + "%'"
    whereSql += " and (z.zone_code like " + kw + " or z.zone_name like " + kw + " or z.schema_name like " + kw + ") "
}
if (zoneTypeValue != '') {
    whereSql += " and z.zone_type = '" + (zoneTypeValue.replace("'", "''")) + "' "
}
if (statusValue != '') {
    whereSql += " and z.status = '" + (statusValue.replace("'", "''")) + "' "
}

var totalRows = db.select("select count(1) as total from lake_zone_t z " + whereSql)
var total = totalRows == null || totalRows.size() == 0 ? 0 : num(first(totalRows[0], ['total', 'TOTAL']))
var offset = (pageNoValue - 1) * pageSizeValue
var list = db.select("""
select
  z.tid as id,
  z.tid as tid,
  z.zone_code as zoneCode,
  z.zone_name as zoneName,
  z.zone_type as zoneType,
  z.datasource_id as datasourceId,
  z.schema_name as schemaName,
  z.status as status,
  z.sort_no as sortNo,
  z.description as description,
  z.created_time as createdTime,
  z.updated_time as updatedTime
from lake_zone_t z
""" + whereSql + " order by z.sort_no asc, z.zone_code asc LIMIT " + pageSizeValue + " OFFSET " + offset)

var viewList = []
for (row in list) {
    viewList.add({
        id: text(first(row, ['id', 'tid'])),
        tid: text(first(row, ['tid', 'id'])),
        zoneCode: text(first(row, ['zoneCode', 'zonecode', 'zone_code'])),
        zoneName: text(first(row, ['zoneName', 'zonename', 'zone_name'])),
        zoneType: text(first(row, ['zoneType', 'zonetype', 'zone_type'])),
        datasourceId: text(first(row, ['datasourceId', 'datasourceid', 'datasource_id'])),
        schemaName: text(first(row, ['schemaName', 'schemaname', 'schema_name'])),
        status: text(first(row, ['status', 'STATUS'])),
        sortNo: num(first(row, ['sortNo', 'sortno', 'sort_no'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time']),
        updatedTime: first(row, ['updatedTime', 'updatedtime', 'updated_time'])
    })
}
return { list: viewList, total: total, pageNo: pageNoValue, pageSize: pageSizeValue }
