{
  "properties": {},
  "id": "d18d0e9c6e4f7b2d5c7a8f9e0d1b2c37",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "20.小时指标",
  "createTime": 1789000000108,
  "updateTime": 1789000000108,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/metrics",
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
// 接口：08.小时指标
// 路由：POST /dws/service/gateway/metrics
// 业务说明：按小时从网关日志聚合调用量、成功率与 P95；先删同小时旧指标再一次分组查询后写入。
import cn.hutool.core.util.IdUtil
import tenantRuntime

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
    db.update("delete from svc_metric_hour_t where tenant_id = #{tenantId} and stat_hour = #{statHour}", { tenantId: tenantId, statHour: statHour })
    var written = 0
    for (row in groups) {
        var callCount = num(first(row, ['call_count', 'callCount', 'CALL_COUNT']))
        var successCount = num(first(row, ['success_count', 'successCount', 'SUCCESS_COUNT']))
        var failCount = num(first(row, ['fail_count', 'failCount', 'FAIL_COUNT']))
        db.update("""
            insert into svc_metric_hour_t (tid, tenant_id, stat_hour, service_code, app_id, call_count, success_count, fail_count, p95_ms, active_users, created_by, updated_by, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{statHour}, #{serviceCode}, #{appId}, #{callCount}, #{successCount}, #{failCount}, #{p95}, #{activeUsers}, null, null, current_timestamp, current_timestamp, 0)
        """, { id: IdUtil.simpleUUID(), tenantId: tenantId, statHour: statHour, serviceCode: text(first(row, ['service_code', 'serviceCode'])), appId: text(first(row, ['app_id', 'appId'])), callCount: callCount, successCount: successCount, failCount: failCount, p95: num(first(row, ['p95', 'P95'])), activeUsers: num(first(row, ['active_users', 'activeUsers', 'ACTIVE_USERS'])) })
        written = written + 1
    }
    return { statHour: statHour, groups: groups.size(), written: written }
}

exit 400, '不支持的 action：' + action
