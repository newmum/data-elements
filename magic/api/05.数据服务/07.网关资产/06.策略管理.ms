{
  "properties": {},
  "id": "7b2d4e3c0e8f4b6d9c1a2f3e4d5b6c71",
  "script": null,
  "groupId": "9a427a26bc984532b8595da7142765d2",
  "name": "06.策略管理",
  "createTime": 1789000000102,
  "updateTime": 1789000000102,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/policies",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\",\n  \"routeId\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务网关治理策略：action=list 查询路由策略，action=save 整体替换路由策略。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import tableWrite
import "@post:/sym/user/me" as me

// 接口：06.策略管理
// 路由：POST /dws/gateway/policies
// 业务说明：按当前租户校验路由归属；策略整体替换，避免同一路由残留旧规则。

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var routeId = body?.routeId == null ? '' : (body.routeId + '').trim()
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
var policyView = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        routeId: text(first(row, ['routeId', 'routeid', 'route_id'])),
        policyCode: text(first(row, ['policyCode', 'policycode', 'policy_code'])),
        policyName: text(first(row, ['policyName', 'policyname', 'policy_name'])),
        priority: num(first(row, ['priority', 'PRIORITY'])),
        authMode: text(first(row, ['authMode', 'authmode', 'auth_mode'])),
        ipAllow: text(first(row, ['ipAllow', 'ipallow', 'ip_allow'])),
        ipDeny: text(first(row, ['ipDeny', 'ipdeny', 'ip_deny'])),
        qps: num(first(row, ['qps', 'QPS'])),
        burst: num(first(row, ['burst', 'BURST'])),
        circuitThreshold: num(first(row, ['circuitThreshold', 'circuitthreshold', 'circuit_threshold'])),
        circuitWindowMs: num(first(row, ['circuitWindowMs', 'circuitwindowms', 'circuit_window_ms'])),
        retryBackoffMs: num(first(row, ['retryBackoffMs', 'retrybackoffms', 'retry_backoff_ms'])),
        paramRulesJson: text(first(row, ['paramRulesJson', 'paramrulesjson', 'param_rules_json'])),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION']))
    }
}

if (routeId == '') { exit 400, '路由标识不能为空' }
if (action != 'save') {
    var route = db.table('svc_gateway_route_t').where().eq('tenant_id', tenantId).eq('tid', routeId).eq('is_del', 0).selectOne()
    if (route == null) { exit 404, '路由不存在或不在当前租户' }
}

if (action == 'list') {
    var rows = db.select("select tid, route_id, policy_code, policy_name, priority, auth_mode, ip_allow, ip_deny, qps, burst, circuit_threshold, circuit_window_ms, retry_backoff_ms, param_rules_json, status, description from svc_gateway_policy_t where tenant_id = #{tenantId} and route_id = #{routeId} and is_del = 0 order by priority desc, policy_code asc", { tenantId: tenantId, routeId: routeId })
    var list = []
    for (row in rows) { list.add(policyView(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    return db.transaction(() => {
        // 使用路由父行串行化同一路由的替换，校验全批后才删除旧策略。
        var route = tableWrite.lockRow('svc_gateway_route_t', tenantId, 'tid', routeId, ['tid'], {is_del: 0})
        if (route == null) { exit 404, '路由不存在或不在当前租户' }
        var items = body?.policies == null ? [] : body.policies
        if (!(items instanceof List) || items.size() > 200) { exit 400, '策略必须为最多 200 项的数组' }
        var rows = []
        var codes = {}
        var now = new Date()
        for (item in items) {
            var code = item?.policyCode == null ? '' : (item.policyCode + '').trim()
            var name = item?.policyName == null ? code : (item.policyName + '').trim()
            if (code == '') { exit 400, '策略编码不能为空' }
            var authMode = item?.authMode == null ? 'TOKEN' : (item.authMode + '').trim().toUpperCase()
            if (!['NONE', 'TOKEN', 'HMAC', 'OAUTH2'].contains(authMode)) { exit 400, '鉴权方式必须是 NONE/TOKEN/HMAC/OAUTH2' }
            var qps = item?.qps == null ? 0 : (item.qps + '')::int(0)
            var burst = item?.burst == null ? 0 : (item.burst + '')::int(0)
            if (qps < 0 || burst < 0) { exit 400, 'QPS 与突发容量不能为负数' }
            var status = item?.status == null ? 'ENABLED' : (item.status + '').trim().toUpperCase()
            if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '策略状态必须是 ENABLED/DISABLED' }
            if (codes[code] == true) { exit 400, '策略编码不能重复：' + code }
            codes[code] = true
            rows.add({
                tid: IdUtil.simpleUUID(), tenant_id: tenantId, route_id: routeId, policy_code: code, policy_name: name,
                priority: item?.priority == null ? 0 : (item.priority + '')::int(0), auth_mode: authMode,
                ip_allow: item?.ipAllow == null ? '' : (item.ipAllow + '').trim(),
                ip_deny: item?.ipDeny == null ? '' : (item.ipDeny + '').trim(),
                qps: qps, burst: burst,
                circuit_threshold: item?.circuitThreshold == null ? 0 : (item.circuitThreshold + '')::int(0),
                circuit_window_ms: item?.circuitWindowMs == null ? 60000 : (item.circuitWindowMs + '')::int(60000),
                retry_backoff_ms: item?.retryBackoffMs == null ? 200 : (item.retryBackoffMs + '')::int(200),
                param_rules_json: item?.paramRulesJson == null ? '' : (item.paramRulesJson + '').trim(),
                status: status, description: item?.description == null ? '' : (item.description + '').trim(),
                created_by: userId, updated_by: userId, created_time: now, updated_time: now, is_del: 0
            })
        }
        var filter = db.table('svc_gateway_policy_t').where().eq('tenant_id', tenantId).eq('route_id', routeId)
        filter.delete()
        if (rows.size() > 0) { db.table('svc_gateway_policy_t').batchInsert(rows, 200) }
        var saved = db.table('svc_gateway_policy_t').where().eq('tenant_id', tenantId).eq('route_id', routeId)
            .eq('is_del', 0).orderBy('priority', 'desc').orderBy('policy_code', 'asc').select()
        var result = saved.map(row => policyView(row))
        return { list: result, total: result.size() }
    })
}

exit 400, '不支持的 action：' + action
