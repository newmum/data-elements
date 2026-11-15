{
  "properties": {},
  "id": "55e367e198734f66bb37efe17a7fc9b0",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "07.对象批量纳管",
  "createTime": 1789000000007,
  "updateTime": 1789000000007,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lake/zones/{id}/objects/batch",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"objects\": [\n    {\n      \"objectType\": \"TABLE\",\n      \"objectRef\": \"db_table_t\",\n      \"objectName\": \"数据表登记\",\n      \"sourceId\": \"\"\n    }\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"inserted\": 1,\n  \"skipped\": 0,\n  \"total\": 1\n}",
  "description": "批量纳管对象（POST /dws/service/lake/zones/{id}/objects/batch）。一次查询已有对象、一次多行插入；20/100 个对象请求数与 SQL 数不随对象数增长。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.对象批量纳管
// 路由：POST /dws/service/lake/zones/{id}/objects/batch
// 业务说明：批量登记对象；先一次查出分层已有对象，再过滤本批重复，最后一条多行插入。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var zoneId = path?.id == null ? '' : (path.id + '').trim()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
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

var objects = body?.objects
if (!(objects instanceof List) || objects.size() == 0) { exit 400, 'objects 必须为非空数组' }
if (objects.size() > 200) { exit 400, '单次批量纳管最多 200 个对象' }

var allowedTypes = ['TABLE', 'VIEW', 'FILE', 'API']
var candidates = []
var seen = {}
for (item in objects) {
    if (!(item instanceof Map)) { exit 400, '每个对象必须为对象结构' }
    var objectType = item.objectType == null ? (item.object_type == null ? '' : (item.object_type + '')) : (item.objectType + '')
    objectType = objectType.trim().toUpperCase()
    var objectRef = item.objectRef == null ? (item.object_ref == null ? '' : (item.object_ref + '')) : (item.objectRef + '')
    objectRef = objectRef.trim()
    var objectName = item.objectName == null ? (item.object_name == null ? '' : (item.object_name + '')) : (item.objectName + '')
    var sourceId = item.sourceId == null ? (item.source_id == null ? '' : (item.source_id + '')) : (item.sourceId + '')
    if (!allowedTypes.contains(objectType)) { exit 400, '对象类型必须是 TABLE/VIEW/FILE/API' }
    if (objectRef == '') { exit 400, '对象引用不能为空' }
    if (objectRef.length() > 128) { exit 400, '对象引用不能超过128个字符' }
    var key = objectType + '|' + objectRef
    if (seen[key] == true) { continue }
    seen[key] = true
    candidates.add({ objectType: objectType, objectRef: objectRef, objectName: objectName.trim(), sourceId: sourceId.trim() })
}

var existingRows = db.select("select object_type as objectType, object_ref as objectRef from lake_zone_object_t where tenant_id = #{tenantId} and zone_id = #{zoneId} and is_del = 0", { tenantId: tenantId, zoneId: zoneId })
var existing = {}
for (row in existingRows) {
    var type = text(first(row, ['objectType', 'objecttype', 'object_type'])).toUpperCase()
    var ref = text(first(row, ['objectRef', 'objectref', 'object_ref']))
    existing[type + '|' + ref] = true
}

var created = []
var values = []
var params = { tenantId: tenantId, zoneId: zoneId, userId: userId }
var index = 0
for (item in candidates) {
    var key = item.objectType + '|' + item.objectRef
    if (existing[key] == true) { continue }
    var id = IdUtil.simpleUUID()
    var objectName = item.objectName == '' ? item.objectRef : item.objectName
    values.add("(#{tid" + index + "}, #{tenantId}, #{zoneId}, #{objectType" + index + "}, #{objectRef" + index + "}, #{objectName" + index + "}, #{sourceId" + index + "}, 'REGISTERED', #{userId}, current_timestamp, current_timestamp, 0)")
    params["tid" + index] = id
    params["objectType" + index] = item.objectType
    params["objectRef" + index] = item.objectRef
    params["objectName" + index] = objectName
    params["sourceId" + index] = item.sourceId
    created.add({ id: id, objectType: item.objectType, objectRef: item.objectRef })
    index = index + 1
}
if (values.size() > 0) {
    db.update("insert into lake_zone_object_t (tid, tenant_id, zone_id, object_type, object_ref, object_name, source_id, lifecycle_status, registered_by, created_time, updated_time, is_del) values " + values.join(','), params)
}
return { inserted: created.size(), skipped: objects.size() - created.size(), total: objects.size(), objects: created }
