{
  "properties": {},
  "id": "8e3a5b0d2f1c4d7e9a6b0c2f4d8e1b73",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "26.定时任务管理",
  "createTime": 1789000000302,
  "updateTime": 1789000000302,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sys/timers",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"save\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "定时任务保存、启停、删除与立即执行；任务编码租户内唯一，Cron 由后端校验。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.定时任务管理
// 路由：POST /dws/service/sys/timers
// 业务说明：保存、启停、删除和立即执行定时任务；调度由后端按租户扫描并加分布式锁。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import timerRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'save' : (body.action + '').trim().toLowerCase()
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

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var timerCode = body?.timerCode == null ? '' : (body.timerCode + '').trim()
    var timerName = body?.timerName == null ? '' : (body.timerName + '').trim()
    var cron = body?.cron == null ? '' : (body.cron + '').trim()
    var handler = body?.handler == null ? '' : (body.handler + '').trim()
    var paramsJson = body?.paramsJson == null ? '' : (body.paramsJson + '').trim()
    var status = body?.status == null ? 'ENABLED' : (body.status + '').trim().toUpperCase()
    var lockTtlSeconds = body?.lockTtlSeconds == null ? 60 : (body.lockTtlSeconds + '')::int(60)
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (timerCode == '' || timerName == '' || cron == '' || handler == '') {
        exit 400, '任务编码、任务名称、Cron 和处理处理器不能为空'
    }
    if (timerCode.length() > 64) { exit 400, '任务编码不能超过 64 个字符' }
    if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    if (lockTtlSeconds < 5 || lockTtlSeconds > 3600) { exit 400, '锁租期必须在 5-3600 秒之间' }
    if (!timerRuntime.validateCron(cron)) { exit 400, 'Cron 表达式不合法：' + cron }
    if (paramsJson != '' && !paramsJson.startsWith('{') && !paramsJson.startsWith('[')) {
        exit 400, '执行参数必须是合法 JSON'
    }
    if (handler.startsWith('magic:')) {
        var expression = handler.substring(6).trim()
        if (expression.indexOf(' ') <= 0) { exit 400, 'magic 处理器格式应为 magic:METHOD 路径' }
    }
    var nextFireAt = status == 'ENABLED' ? timerRuntime.nextFireAt(cron) : null
    if (id == '') {
        var duplicate = db.select("select count(1) as n from sys_timer_t where tenant_id = #{tenantId} and timer_code = #{timerCode} and is_del = 0", { tenantId: tenantId, timerCode: timerCode })
        if (duplicate[0].n > 0) { exit 409, '定时任务编码已存在：' + timerCode }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into sys_timer_t
              (tid, tenant_id, timer_code, timer_name, cron, handler, params_json, status, next_fire_at, lock_ttl_seconds, description, created_by, updated_by, created_time, updated_time, is_del)
            values
              (#{id}, #{tenantId}, #{timerCode}, #{timerName}, #{cron}, #{handler}, #{paramsJson}, #{status}, #{nextFireAt}, #{lockTtlSeconds}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, timerCode: timerCode, timerName: timerName, cron: cron, handler: handler, paramsJson: paramsJson, status: status, nextFireAt: nextFireAt, lockTtlSeconds: lockTtlSeconds, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from sys_timer_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '定时任务不存在或不在当前租户' }
        var duplicate = db.select("select count(1) as n from sys_timer_t where tenant_id = #{tenantId} and timer_code = #{timerCode} and tid <> #{id} and is_del = 0", { tenantId: tenantId, timerCode: timerCode, id: id })
        if (duplicate[0].n > 0) { exit 409, '定时任务编码已存在：' + timerCode }
        db.update("""
            update sys_timer_t set timer_code = #{timerCode}, timer_name = #{timerName}, cron = #{cron}, handler = #{handler},
              params_json = #{paramsJson}, status = #{status}, next_fire_at = #{nextFireAt}, lock_ttl_seconds = #{lockTtlSeconds},
              description = #{description}, updated_by = #{userId}, updated_time = current_timestamp
            where tid = #{id} and tenant_id = #{tenantId} and is_del = 0
        """, { timerCode: timerCode, timerName: timerName, cron: cron, handler: handler, paramsJson: paramsJson, status: status, nextFireAt: nextFireAt, lockTtlSeconds: lockTtlSeconds, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, timer_code, timer_name, cron, handler, params_json, status, last_fire_at, next_fire_at, lock_ttl_seconds, description, created_time, updated_time from sys_timer_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return timerView(saved[0])
}

if (action == 'toggle') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var status = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    if (id == '') { exit 400, '任务标识不能为空' }
    if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    var existing = db.select("select tid, cron from sys_timer_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '定时任务不存在或不在当前租户' }
    var nextFireAt = status == 'ENABLED' ? timerRuntime.nextFireAt(text(first(existing[0], ['cron', 'CRON']))) : null
    db.update("update sys_timer_t set status = #{status}, next_fire_at = #{nextFireAt}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { status: status, nextFireAt: nextFireAt, userId: userId, id: id, tenantId: tenantId })
    return { id: id, status: status }
}

if (action == 'remove') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    if (id == '') { exit 400, '任务标识不能为空' }
    var existing = db.select("select tid from sys_timer_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '定时任务不存在或不在当前租户' }
    db.update("update sys_timer_t set is_del = 1, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { userId: userId, id: id, tenantId: tenantId })
    return { id: id, removed: true }
}

if (action == 'fire') {
    var timerCode = body?.timerCode == null ? '' : (body.timerCode + '').trim()
    if (timerCode == '') { exit 400, '任务编码不能为空' }
    return timerRuntime.fireNow(timerCode)
}

exit 400, '不支持的 action：' + action
