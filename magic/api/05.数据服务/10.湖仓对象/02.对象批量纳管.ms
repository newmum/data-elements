{
  "properties": {},
  "id": "55e367e198734f66bb37efe17a7fc9b0",
  "script": null,
  "groupId": "11f572cb355b47ec94d4da477e16dc26",
  "name": "02.对象批量纳管",
  "createTime": 1789000000007,
  "updateTime": 1789000000007,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/register",
  "method": "POST",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的湖仓分层或对象标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "requestBody": "{\n  \"objects\": [\n    {\n      \"objectType\": \"TABLE\",\n      \"objectRef\": \"db_table_t\",\n      \"objectName\": \"数据表登记\",\n      \"sourceId\": \"\"\n    }\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"inserted\": 1,\n  \"skipped\": 0,\n  \"total\": 1\n}",
  "description": "批量纳管对象（POST /dws/lakeobject/register?id={id}）。一次查询已有对象、一次多行插入；20/100 个对象请求数与 SQL 数不随对象数增长。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import tableWrite
import "@post:/sym/user/me" as me

// 接口：02.对象批量纳管
// 路由：POST /dws/lakeobject/register?id={id}
// 业务说明：批量登记对象；先一次查出分层已有对象，再过滤本批重复，最后一条多行插入。

var tenantId = tenantRuntime.id()
var zoneId = param?.id == null ? '' : (param.id + '').trim()
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
var objects = body?.objects
var created = []
db.transaction(() => {
    // 同一分层的纳管串行读取与写入，避免并发检查把同一对象当作新对象。
    var zone = tableWrite.lockRow('lake_zone_t', tenantId, 'tid', zoneId, ['tid'], {is_del: 0})
    if (zone == null) { exit 404, '分层不存在或不在当前租户' }
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

    var existingRows = db.table('lake_zone_object_t').where().eq('tenant_id', tenantId)
        .eq('zone_id', zoneId).eq('is_del', 0).in('object_ref', candidates.map(item => item.objectRef)).select()
    var existing = {}
    for (row in existingRows) {
        var type = text(first(row, ['objectType', 'objecttype', 'object_type', 'OBJECT_TYPE'])).toUpperCase()
        var ref = text(first(row, ['objectRef', 'objectref', 'object_ref', 'OBJECT_REF']))
        existing[type + '|' + ref] = true
    }
    var rows = []
    var now = new Date()
    for (item in candidates) {
        if (existing[item.objectType + '|' + item.objectRef] == true) { continue }
        var id = IdUtil.simpleUUID()
        rows.add({
            tid: id, tenant_id: tenantId, zone_id: zoneId, object_type: item.objectType,
            object_ref: item.objectRef, object_name: item.objectName == '' ? item.objectRef : item.objectName,
            source_id: item.sourceId, lifecycle_status: 'REGISTERED', registered_by: userId,
            created_time: now, updated_time: now, is_del: 0
        })
        created.add({ id: id, objectType: item.objectType, objectRef: item.objectRef })
    }
    if (rows.size() > 0) { db.table('lake_zone_object_t').batchInsert(rows, 200) }
})
return { inserted: created.size(), skipped: objects.size() - created.size(), total: objects.size(), objects: created }
