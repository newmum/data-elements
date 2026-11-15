{
  "properties": {},
  "id": "793387e9e9394d2f9e39ac8aaa696505",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "08.对象生命周期",
  "createTime": 1789000000008,
  "updateTime": 1789000000008,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lake/objects/{id}/lifecycle",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"lifecycleStatus\": \"ONLINE\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"...\",\n  \"lifecycleStatus\": \"ONLINE\"\n}",
  "description": "对象生命周期流转（POST /dws/service/lake/objects/{id}/lifecycle）。只允许 REGISTERED→ONLINE/OFFLINE/ARCHIVED、ONLINE→OFFLINE、OFFLINE→ONLINE/ARCHIVED，不做物理删除。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.对象生命周期
// 路由：POST /dws/service/lake/objects/{id}/lifecycle
// 业务说明：只做生命周期状态流转，保留对象与登记记录；非法流转直接拒绝。
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var id = path?.id == null ? '' : (path.id + '').trim()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

var target = body?.lifecycleStatus == null ? '' : (body.lifecycleStatus + '').trim().toUpperCase()
if (id == '') { exit 400, '对象标识不能为空' }
if (target == '') { exit 400, 'lifecycleStatus 不能为空' }
var allowed = ['REGISTERED', 'ONLINE', 'OFFLINE', 'ARCHIVED']
if (!allowed.contains(target)) { exit 400, '生命周期状态必须是 REGISTERED/ONLINE/OFFLINE/ARCHIVED' }

var rows = db.select("select tid, lifecycle_status as lifecycleStatus from lake_zone_object_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
if (rows == null || rows.size() == 0) { exit 404, '对象不存在或不在当前租户' }
var current = text(first(rows[0], ['lifecycleStatus', 'lifecyclestatus', 'lifecycle_status'])).toUpperCase()
if (current == target) { return { id: id, lifecycleStatus: target, changed: false } }

var transitions = {
    REGISTERED: ['ONLINE', 'OFFLINE', 'ARCHIVED'],
    ONLINE: ['OFFLINE'],
    OFFLINE: ['ONLINE', 'ARCHIVED'],
    ARCHIVED: []
}
var next = transitions[current]
if (next == null || !next.contains(target)) {
    exit 409, '不允许从 ' + current + ' 流转到 ' + target
}

if (target == 'ONLINE') {
    db.update("update lake_zone_object_t set lifecycle_status = #{target}, last_sync_at = current_timestamp, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { target: target, id: id, tenantId: tenantId })
} else {
    db.update("update lake_zone_object_t set lifecycle_status = #{target}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { target: target, id: id, tenantId: tenantId })
}
return { id: id, lifecycleStatus: target, changed: true }
