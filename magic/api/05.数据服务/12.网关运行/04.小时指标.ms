{
  "properties": {},
  "id": "d18d0e9c6e4f7b2d5c7a8f9e0d1b2c37",
  "script": null,
  "groupId": "115458a314ce4956b65d27d47afd61ce",
  "name": "04.小时指标",
  "createTime": 1789000000108,
  "updateTime": 1789000000108,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/metrics",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "网关小时指标：action=list 查询，action=aggregate 从日志聚合并覆盖指定小时指标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime

// 接口：04.小时指标
// 路由：POST /dws/gaterun/metrics
// 业务说明：按小时从网关日志聚合调用量、成功率与 P95；先删同小时旧指标再一次分组查询后写入。

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var view = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        statHour: first(row, ['statHour', 'stathour', 'stat_hour']),
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        appId: text(first(row, ['appId', 'appid', 'app_id'])),
        callCount: num(first(row, ['callCount', 'callcount', 'call_count'])),
        successCount: num(first(row, ['successCount', 'successcount', 'success_count'])),
        failCount: num(first(row, ['failCount', 'failcount', 'fail_count'])),
        p95Ms: num(first(row, ['p95Ms', 'p95ms', 'p95_ms'])),
        activeUsers: num(first(row, ['activeUsers', 'activeusers', 'active_users'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var appId = body?.appId == null ? '' : (body.appId + '').trim()
    var startTime = body?.startTime == null ? '' : (body.startTime + '').trim()
    var endTime = body?.endTime == null ? '' : (body.endTime + '').trim()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (appId != '') { whereSql += " and app_id = '" + appId.replace("'", "''") + "' " }
    if (startTime != '') { whereSql += " and stat_hour >= '" + startTime.replace("'", "''") + "' " }
    if (endTime != '') { whereSql += " and stat_hour <= '" + endTime.replace("'", "''") + "' " }
    var rows = db.select("select tid, stat_hour, service_code, app_id, call_count, success_count, fail_count, p95_ms, active_users, created_time from svc_metric_hour_t " + whereSql + " order by stat_hour desc, service_code asc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'aggregate') {
    var statHour = body?.statHour == null ? '' : (body.statHour + '').trim()
    var startTime = body?.startTime == null ? '' : (body.startTime + '').trim()
    var endTime = body?.endTime == null ? '' : (body.endTime + '').trim()
    if (statHour == '' || startTime == '' || endTime == '') { exit 400, 'statHour、startTime、endTime 不能为空' }
    var groups = db.select("""
        select service_code, coalesce(app_id, '') as app_id,
               count(1) as call_count,
               sum(case when status_code < 400 then 1 else 0 end) as success_count,
               sum(case when status_code >= 400 then 1 else 0 end) as fail_count,
               max(cost_ms) as p95,
               count(distinct app_id) as active_users
          from svc_gateway_log_t
         where tenant_id = #{tenantId} and is_del = 0 and created_time >= #{startTime} and created_time < #{endTime}
         group by service_code, app_id
    """, { tenantId: tenantId, startTime: startTime, endTime: endTime })
    var rows = []
    var now = new Date()
    for (row in groups) {
        rows.add({
            tid: IdUtil.simpleUUID(), tenant_id: tenantId, stat_hour: statHour,
            service_code: text(first(row, ['service_code', 'serviceCode'])), app_id: text(first(row, ['app_id', 'appId'])),
            call_count: num(first(row, ['call_count', 'callCount', 'CALL_COUNT'])),
            success_count: num(first(row, ['success_count', 'successCount', 'SUCCESS_COUNT'])),
            fail_count: num(first(row, ['fail_count', 'failCount', 'FAIL_COUNT'])),
            p95_ms: num(first(row, ['p95', 'P95'])), active_users: num(first(row, ['active_users', 'activeUsers', 'ACTIVE_USERS'])),
            created_time: now, updated_time: now, is_del: 0
        })
    }
    db.transaction(() => {
        // 删除与批量重建同小时结果一起提交，写入失败时保留旧指标。
        db.table('svc_metric_hour_t').where().eq('tenant_id', tenantId).eq('stat_hour', statHour).delete()
        if (rows.size() > 0) { db.table('svc_metric_hour_t').batchInsert(rows, 200) }
    })
    return { statHour: statHour, groups: groups.size(), written: rows.size() }
}

exit 400, '不支持的 action：' + action
