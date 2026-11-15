{
  "properties": {},
  "id": "8e3a5b0d2f1c4d7e9a6b0c2f4d8e1b73",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "02.定时任务管理",
  "createTime": 1789000000302,
  "updateTime": 1789000000302,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/manage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"save\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "定时任务配置保存、启停与删除；支持 Cron 预检，未接入执行端时返回明确状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import tableWrite
import org.springframework.scheduling.support.CronExpression
import java.time.LocalDateTime
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import "@post:/sym/user/me" as me

// 接口：02.定时任务管理
// 路由：POST /dws/timer/manage
// 业务说明：在当前租户内保存、启停和删除定时配置，预检可在提交前验证参数。

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

var schedule = (expression) => {
    // Spring 以秒开头，兼容已有五段 Cron。
    var cron = expression.trim()
    if (cron.split('\\s+').length == 5) { cron = '0 ' + cron }
    try {
        return {valid: true, next: CronExpression.parse(cron).next(LocalDateTime.now())}
    } catch (error) {
        return {valid: false, next: null}
    }
}
var active = {is_del: 0}
var timers = () => db.table('sys_timer_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
var id = text(body?.id).trim()
var preview = body?.dryRun == true

if (action == 'save') {
    var timerCode = text(body?.timerCode).trim()
    var timerName = text(body?.timerName).trim()
    var cron = text(body?.cron).trim()
    var handler = text(body?.handler).trim()
    var paramsJson = text(body?.paramsJson).trim()
    var status = body?.status == null ? 'ENABLED' : text(body.status).trim().toUpperCase()
    var lockTtlSeconds = body?.lockTtlSeconds == null ? 60 : text(body.lockTtlSeconds)::int(60)
    if (timerCode == '' || timerName == '' || cron == '' || handler == '') {
        exit 400, '任务编码、任务名称、Cron 和处理器不能为空'
    }
    if (timerCode.length() > 64) { exit 400, '任务编码不能超过 64 个字符' }
    if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    if (lockTtlSeconds < 5 || lockTtlSeconds > 3600) { exit 400, '锁租期必须在 5-3600 秒之间' }
    var parsed = schedule(cron)
    if (!parsed.valid || (status == 'ENABLED' && parsed.next == null)) { exit 400, 'Cron 表达式不合法：' + cron }
    if (paramsJson != '') {
        var validParams = false
        try {
            var mapper = new ObjectMapper()
            mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            validParams = mapper.readTree(paramsJson).isContainerNode()
        } catch (error) {}
        if (!validParams) { exit 400, '执行参数必须是合法 JSON' }
    }
    if (handler.startsWith('magic:') && handler.substring(6).trim().indexOf(' ') <= 0) {
        exit 400, 'magic 处理器格式应为 magic:METHOD 路径'
    }
    var next = status == 'ENABLED' ? parsed.next : null
    return db.transaction(() => {
        // 已有配置通过主键锁定后更新，修改与启停不会使用过期 cron。
        if (id != '' && tableWrite.lockRow('sys_timer_t',tenantId,'tid',id,['tid'],active) == null) {
            exit 404, '定时任务不存在或不在当前租户'
        }
        var found = timers().eq('timer_code', timerCode).selectOne()
        if (found != null && text(found.tid) != id) { exit 409, '定时任务编码已存在：' + timerCode }
        if (preview) { return {dryRun: true, nextFireAt: text(next), status: status} }
        var now = new Date()
        var input = {
            timer_code: timerCode, timer_name: timerName, cron: cron, handler: handler, params_json: paramsJson,
            status: status, next_fire_at: next, lock_ttl_seconds: lockTtlSeconds,
            description: text(body?.description).trim(), updated_by: userId
        }
        var savedId = id
        if (savedId == '') {
            savedId = IdUtil.simpleUUID()
            input.tid = savedId
            input.tenant_id = tenantId
            input.created_by = userId
            input.created_time = now
            input.updated_time = now
            input.is_del = 0
            db.table('sys_timer_t').insert(input)
        } else {
            // 显式写入空的下次触发时间，停用后的旧时间不能留在记录中。
            tableWrite.updateWithDatabaseTimestamp('sys_timer_t',tenantId,'tid',savedId,input,active,'updated_time')
        }
        return timerView(timers().eq('tid', savedId).selectOne())
    })
}

if (action == 'toggle' || action == 'remove') {
    if (id == '') { exit 400, '任务标识不能为空' }
    var status = text(body?.status).trim().toUpperCase()
    if (action == 'toggle' && !['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    return db.transaction(() => {
        var current = tableWrite.lockRow('sys_timer_t',tenantId,'tid',id,['tid','cron'],active)
        if (current == null) { exit 404, '定时任务不存在或不在当前租户' }
        if (action == 'remove') {
            timers().eq('tid',id).update({is_del: 1, updated_by: userId, updated_time: new Date()})
            return {id: id, removed: true}
        }
        var next = null
        if (status == 'ENABLED') {
            var parsed = schedule(text(current.cron))
            if (!parsed.valid || parsed.next == null) { exit 400, 'Cron 表达式不合法：' + text(current.cron) }
            next = parsed.next
        }
        tableWrite.updateWithDatabaseTimestamp('sys_timer_t',tenantId,'tid',id,
            {status: status, next_fire_at: next, updated_by: userId},active,'updated_time')
        return {id: id, status: status}
    })
}

if (action == 'fire') {
    var timerCode = text(body?.timerCode).trim()
    if (timerCode == '') { exit 400, '任务编码不能为空' }
    if (timers().eq('timer_code',timerCode).selectOne() == null) { exit 404, '定时任务不存在或不在当前租户' }
    // 当前部署未绑定执行器，保留配置和日志，避免把 handler 字符串当作内部调用权限。
    exit 501, '定时任务执行端尚未绑定'
}

exit 400, '不支持的 action：' + action
