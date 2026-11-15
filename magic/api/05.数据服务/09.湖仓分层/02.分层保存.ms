{
  "properties": {},
  "id": "e2a5ae446aea42e3a29ba388ddd41de9",
  "script": null,
  "groupId": "b5badef9297e4dbb8a6028e72a393d4e",
  "name": "02.分层保存",
  "createTime": 1789000000002,
  "updateTime": 1789000000002,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/zones",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"zoneCode\": \"ODS\",\n  \"zoneName\": \"原始库\",\n  \"zoneType\": \"ORIGIN\",\n  \"schemaName\": \"ods\",\n  \"sortNo\": 10\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"...\",\n  \"zoneCode\": \"ODS\"\n}",
  "description": "新建或修改湖仓分层（POST /dws/lake/zones）。校验 zoneCode 在当前租户唯一、zoneType 合法；带 id 时只更新当前租户记录。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

// 接口：02.分层保存
// 路由：POST /dws/lake/zones
// 业务说明：新建或修改湖仓分层；zoneCode 在当前租户唯一，租户来自认证上下文。

var tenantId = tenantRuntime.id()
var id = body?.id == null ? '' : (body.id + '').trim()
var zoneCode = body?.zoneCode == null ? '' : (body.zoneCode + '').trim().toUpperCase()
var zoneName = body?.zoneName == null ? '' : (body.zoneName + '').trim()
var zoneType = body?.zoneType == null ? '' : (body.zoneType + '').trim().toUpperCase()
var datasourceId = body?.datasourceId == null ? '' : (body.datasourceId + '').trim()
var schemaName = body?.schemaName == null ? '' : (body.schemaName + '').trim()
var status = body?.status == null ? 'ENABLED' : (body.status + '').trim().toUpperCase()
var sortNo = body?.sortNo == null ? 0 : (body.sortNo + '')::int(0)
var description = body?.description == null ? '' : (body.description + '').trim()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

if (zoneCode == '' || zoneName == '') { exit 400, '分层编码和分层名称不能为空' }
if (zoneCode.length() > 32) { exit 400, '分层编码不能超过32个字符' }
if (!zoneCode.matches('[A-Z0-9_]+')) { exit 400, '分层编码只能包含大写字母、数字和下划线' }
var allowedTypes = ['ORIGIN', 'BASE', 'SUBJECT', 'TOPIC', 'LAKE']
if (!allowedTypes.contains(zoneType)) { exit 400, '分层类型必须是 ORIGIN/BASE/SUBJECT/TOPIC/LAKE' }
if (status != 'ENABLED' && status != 'DISABLED') { exit 400, '状态必须是 ENABLED 或 DISABLED' }
if (datasourceId != '') {
    var sourceCount = db.select("select count(1) as n from db_datasource_t where tid = #{datasourceId} and tenant_id = #{tenantId} and is_del = 0", { datasourceId: datasourceId, tenantId: tenantId })
    if (sourceCount[0].n == 0) { exit 400, '承载数据源不存在或不在当前租户' }
}

if (id == '') {
    var duplicate = db.select("select count(1) as n from lake_zone_t where tenant_id = #{tenantId} and zone_code = #{zoneCode} and is_del = 0", { tenantId: tenantId, zoneCode: zoneCode })
    if (duplicate[0].n > 0) { exit 409, '分层编码已存在：' + zoneCode }
    id = IdUtil.simpleUUID()
    db.update("""
        insert into lake_zone_t
          (tid, tenant_id, zone_code, zone_name, zone_type, datasource_id, schema_name, status, sort_no, description, created_by, updated_by, created_time, updated_time, is_del)
        values
          (#{id}, #{tenantId}, #{zoneCode}, #{zoneName}, #{zoneType}, #{datasourceId}, #{schemaName}, #{status}, #{sortNo}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
    """, { id: id, tenantId: tenantId, zoneCode: zoneCode, zoneName: zoneName, zoneType: zoneType, datasourceId: datasourceId, schemaName: schemaName, status: status, sortNo: sortNo, description: description, userId: userId })
} else {
    var existing = db.select("select tid from lake_zone_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '分层不存在或不在当前租户' }
    var duplicate = db.select("select count(1) as n from lake_zone_t where tenant_id = #{tenantId} and zone_code = #{zoneCode} and tid <> #{id} and is_del = 0", { tenantId: tenantId, zoneCode: zoneCode, id: id })
    if (duplicate[0].n > 0) { exit 409, '分层编码已存在：' + zoneCode }
    db.update("""
        update lake_zone_t set
          zone_code = #{zoneCode}, zone_name = #{zoneName}, zone_type = #{zoneType},
          datasource_id = #{datasourceId}, schema_name = #{schemaName}, status = #{status},
          sort_no = #{sortNo}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp
        where tid = #{id} and tenant_id = #{tenantId} and is_del = 0
    """, { zoneCode: zoneCode, zoneName: zoneName, zoneType: zoneType, datasourceId: datasourceId, schemaName: schemaName, status: status, sortNo: sortNo, description: description, userId: userId, id: id, tenantId: tenantId })
}
var saved = db.select("""
    select tid as id, tid, zone_code as zoneCode, zone_name as zoneName, zone_type as zoneType,
           datasource_id as datasourceId, schema_name as schemaName, status, sort_no as sortNo,
           description, created_time as createdTime, updated_time as updatedTime
      from lake_zone_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0
""", { id: id, tenantId: tenantId })
var row = saved[0]
return {
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
}
