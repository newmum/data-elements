{
  "properties": {},
  "id": "b51c4c3a0c8d1f6b9a1e2d3c4b5f6a7b",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "24.网关调用",
  "createTime": 1789000000112,
  "updateTime": 1789000000112,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/invoke",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"serviceCode\": \"demoService\",\n  \"version\": \"v1\",\n  \"clientId\": \"demo-client\",\n  \"params\": {}\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "网关调用入口：按 13 步过滤器链完成 traceId、租户、应用鉴权、IP、路由、参数、协议、限流、熔断、负载与日志，返回转发目标。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.网关调用
// 路由：POST /dws/service/gateway/invoke
// 业务说明：13 步过滤器链的网关入口；返回转发目标与统一响应包，实际转发由运行时代理执行。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import jsons
import java.lang.System

var tenantId = tenantRuntime.id()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

var traceId = body?.traceId == null ? IdUtil.simpleUUID() : (body.traceId + '').trim()
var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
var version = body?.version == null ? 'v1' : (body.version + '').trim()
var clientId = body?.clientId == null ? '' : (body.clientId + '').trim()
var appCode = body?.appCode == null ? '' : (body.appCode + '').trim()
var clientIp = body?.clientIp == null ? '' : (body.clientIp + '').trim()
var method = body?.method == null ? 'POST' : (body.method + '').trim().toUpperCase()
var uri = body?.uri == null ? '' : (body.uri + '').trim()
var regionCode = body?.regionCode == null ? '' : (body.regionCode + '').trim()
var params = body?.params == null ? {} : body.params
var started = System.currentTimeMillis()

var logEvent = (logType, statusCode, errorCode) => {
    try {
        db.update("""
            insert into svc_gateway_log_t (tid, tenant_id, trace_id, service_code, app_id, log_type, uri, method, status_code, cost_ms, error_code, client_ip, region_code, request_hash, response_size, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{traceId}, #{serviceCode}, #{appId}, #{logType}, #{uri}, #{method}, #{statusCode}, #{costMs}, #{errorCode}, #{clientIp}, #{regionCode}, #{requestHash}, 0, current_timestamp, current_timestamp, 0)
        """, { id: IdUtil.simpleUUID(), tenantId: tenantId, traceId: traceId, serviceCode: serviceCode, appId: appCode, logType: logType, uri: uri, method: method, statusCode: statusCode, costMs: System.currentTimeMillis() - started, errorCode: errorCode, clientIp: clientIp, regionCode: regionCode, requestHash: '' })
    } catch (ignored) { }
}
var fail = (statusCode, errorCode, message) => {
    logEvent('ERROR', statusCode, errorCode)
    exit statusCode, message
}
// IP 规则：支持精确、逗号分隔、前缀（10.0.0.）、通配（10.0.0.*）与 CIDR（10.0.0.0/24）。
var ipInCidr = (ip, cidr) => {
    var slash = cidr.indexOf('/')
    if (slash <= 0) { return false }
    var base = cidr.substring(0, slash)
    var bits = cidr.substring(slash + 1)::int(0)
    var ipParts = ip.split('.')
    var baseParts = base.split('.')
    if (ipParts.length != 4 || baseParts.length != 4 || bits < 0 || bits > 32) { return false }
    var full = (bits / 8)::int(0)
    var rem = bits % 8
    var i = 0
    while (i < full) {
        if (ipParts[i]::int(0) != baseParts[i]::int(0)) { return false }
        i = i + 1
    }
    if (rem > 0) {
        var span = 1
        var k = 0
        while (k < 8 - rem) { span = span * 2; k = k + 1 }
        if ((ipParts[full]::int(0) / span)::int(0) != (baseParts[full]::int(0) / span)::int(0)) { return false }
    }
    return true
}
var ipHit = (ruleText, ip) => {
    if (ruleText == '' || ip == '') { return false }
    var items = ruleText.split(',')
    for (item in items) {
        var rule = text(item).trim()
        if (rule != '') {
            if (ipInCidr(ip, rule)) { return true }
            if (rule.endsWith('*')) {
                if (ip.startsWith(rule.substring(0, rule.length() - 1))) { return true }
            } else if (rule.endsWith('.')) {
                if (ip.startsWith(rule)) { return true }
            } else if (ip == rule) { return true }
        }
    }
    return false
}

if (serviceCode == '') { fail(400, 'GATEWAY_PARAM', '服务编码不能为空') }

// 步骤 1-2：traceId 已注入；租户只从会话解析。
if (tenantId == null || text(tenantId) == '') { fail(401, 'GATEWAY_TENANT', '租户上下文缺失') }

// 步骤 3：应用鉴权。
var appRows = db.select("select tid, app_code, app_name, auth_type, client_id, status from svc_gateway_app_t where tenant_id = #{tenantId} and is_del = 0 and (client_id = #{clientId} or app_code = #{appCode})", { tenantId: tenantId, clientId: clientId, appCode: appCode })
var app = appRows == null || appRows.size() == 0 ? null : appRows[0]
if (app != null) {
    appCode = text(first(app, ['appCode', 'appcode', 'app_code']))
    if (text(first(app, ['status', 'STATUS'])) != 'ENABLED') { fail(403, 'GATEWAY_APP_DISABLED', '接入应用已停用') }
}

// 步骤 5：路由匹配，灰度版本优先。
var routeRows = db.select("select tid, service_code, version, path_prefix, target_type, target_ref, region_code, timeout_ms, retry_times, lb_strategy, status from svc_gateway_route_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, version: version })
if (routeRows == null || routeRows.size() == 0) { fail(404, 'GATEWAY_ROUTE_NOT_FOUND', '服务路由不存在：' + serviceCode + '/' + version) }
var route = routeRows[0]
if (text(first(route, ['status', 'STATUS'])) != 'PUBLISHED') { fail(403, 'GATEWAY_ROUTE_DISABLED', '服务路由未发布') }
var grayRows = db.select("select from_version, to_version, strategy, match_value, weight from svc_gray_release_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and status = 'RUNNING' and is_del = 0 order by created_time desc", { tenantId: tenantId, serviceCode: serviceCode })
var grayVersion = ''
var grayWeight = 0
var grayStrategy = ''
var grayHit = false
if (grayRows != null && grayRows.size() > 0) {
    var gray = grayRows[0]
    grayVersion = text(first(gray, ['toVersion', 'toversion', 'to_version', 'TO_VERSION']))
    grayWeight = num(first(gray, ['weight', 'WEIGHT']))
    grayStrategy = text(first(gray, ['strategy', 'STRATEGY']))
    var grayMatch = text(first(gray, ['matchValue', 'matchvalue', 'match_value', 'MATCH_VALUE']))
    if (grayStrategy == 'WEIGHT' && grayWeight >= 100) { grayHit = true }
    if (grayStrategy == 'HEADER' && grayMatch == text(body?.headerValue)) { grayHit = true }
    if (grayStrategy == 'IP' && clientIp != '' && clientIp.startsWith(grayMatch)) { grayHit = true }
    if (grayHit && grayVersion != '') {
        var grayRoute = db.select("select tid, service_code, version, path_prefix, target_type, target_ref, region_code, timeout_ms, retry_times, lb_strategy, status from svc_gateway_route_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{grayVersion} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, grayVersion: grayVersion })
        if (grayRoute != null && grayRoute.size() > 0) { route = grayRoute[0]; version = grayVersion }
    }
}
var routeId = text(first(route, ['tid', 'id']))
var targetType = text(first(route, ['targetType', 'targettype', 'target_type']))
var targetRef = text(first(route, ['targetRef', 'targetref', 'target_ref']))
var routeRegion = text(first(route, ['regionCode', 'regioncode', 'region_code']))
if (regionCode == '') { regionCode = routeRegion }

// 步骤 4/6/9/10：加载策略，按优先级合并；未配置策略时使用宽松默认值。
var policyRows = db.select("select tid, auth_mode, ip_allow, ip_deny, qps, burst, circuit_threshold, circuit_window_ms, retry_backoff_ms, param_rules_json from svc_gateway_policy_t where tenant_id = #{tenantId} and route_id = #{routeId} and status = 'ENABLED' and is_del = 0 order by priority desc", { tenantId: tenantId, routeId: routeId })
var authMode = 'NONE'
var ipAllow = ''
var ipDeny = ''
var qps = 0
var burst = 0
var circuitThreshold = 0
var circuitWindowMs = 60000
var paramRulesJson = ''
if (policyRows != null && policyRows.size() > 0) {
    var policy = policyRows[0]
    authMode = text(first(policy, ['authMode', 'authmode', 'auth_mode']))
    ipAllow = text(first(policy, ['ipAllow', 'ipallow', 'ip_allow']))
    ipDeny = text(first(policy, ['ipDeny', 'ipdeny', 'ip_deny']))
    qps = num(first(policy, ['qps', 'QPS']))
    burst = num(first(policy, ['burst', 'BURST']))
    circuitThreshold = num(first(policy, ['circuitThreshold', 'circuitthreshold', 'circuit_threshold']))
    circuitWindowMs = num(first(policy, ['circuitWindowMs', 'circuitwindowms', 'circuit_window_ms']))
    paramRulesJson = text(first(policy, ['paramRulesJson', 'paramrulesjson', 'param_rules_json']))
}
if (authMode != 'NONE' && app == null) { fail(401, 'GATEWAY_AUTH', '接入应用未登记或凭证缺失') }

// 步骤 4：IP 限制。
if (clientIp != '' && ipDeny != '' && ipHit(ipDeny, clientIp)) { fail(403, 'GATEWAY_IP_DENIED', '客户端 IP 在黑名单内') }
if (clientIp != '' && ipAllow != '' && !ipHit(ipAllow, clientIp)) { fail(403, 'GATEWAY_IP_DENIED', '客户端 IP 不在白名单内') }

// 步骤 6：参数校验（规则数组：[{name, required, type, maxLength, enum:[...]}]）。
if (paramRulesJson != '') {
    var rules = jsons.toJSONArray(paramRulesJson)
    if (rules != null) {
        for (rule in rules) {
            var name = text(rule['name'])
            var value = params[name]
            var blankValue = value == null || text(value).trim() == ''
            if (rule['required'] == true && blankValue) { fail(400, 'GATEWAY_PARAM_REQUIRED', '缺少必填参数：' + name) }
            if (!blankValue && rule['maxLength'] != null && text(value).length() > num(rule['maxLength'])) { fail(400, 'GATEWAY_PARAM_LENGTH', '参数超长：' + name) }
            if (!blankValue && rule['type'] != null && text(rule['type']) == 'NUMBER' && !text(value).matches('^-?[0-9]+(\\.[0-9]+)?$')) { fail(400, 'GATEWAY_PARAM_TYPE', '参数类型错误：' + name) }
        }
    }
}

// 步骤 7：协议转换与目标检查。
if (!['FLOW', 'HTTP', 'DB', 'KAFKA', 'ES'].contains(targetType)) { fail(501, 'GATEWAY_PROTOCOL', '不支持的目标协议：' + targetType) }
if (targetType != 'FLOW' && targetType != 'HTTP') { fail(501, 'GATEWAY_PROTOCOL', '当前运行时代理暂未启用该协议适配：' + targetType) }

// 步骤 9：令牌桶近似限流（最近 1 秒请求数，按应用与服务）。
if (qps > 0) {
    var limitRows = db.select("select count(1) as n from svc_gateway_log_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and app_id = #{appId} and log_type = 'REQUEST' and created_time >= current_timestamp - interval '1' second and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, appId: appCode })
    var recent = limitRows == null || limitRows.size() == 0 ? 0 : num(first(limitRows[0], ['n', 'N']))
    if (recent >= qps + burst) { fail(429, 'GATEWAY_RATE_LIMIT', '请求过于频繁，请稍后重试') }
}

// 步骤 10：熔断（最近 60 秒 5xx 次数达到阈值即快速失败；窗口滑过后自动半开）。
if (circuitThreshold > 0) {
    var circuitRows = db.select("select count(1) as n from svc_gateway_log_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and log_type = 'ERROR' and status_code >= 500 and created_time >= current_timestamp - interval '60' second and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode })
    var failures = circuitRows == null || circuitRows.size() == 0 ? 0 : num(first(circuitRows[0], ['n', 'N']))
    if (failures >= circuitThreshold) { fail(503, 'GATEWAY_CIRCUIT_OPEN', '服务熔断中，请稍后重试') }
}

// 步骤 11：负载均衡（健康实例按权重降序；无实例时使用路由目标地址）。
var instanceRows = db.select("select instance_url, weight, health_status from svc_gateway_instance_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and status = 'ENABLED' and health_status = 'UP' and is_del = 0 order by weight desc", { tenantId: tenantId, serviceCode: serviceCode, version: version })
var forwardUrl = targetRef
if (instanceRows != null && instanceRows.size() > 0) { forwardUrl = text(first(instanceRows[0], ['instanceUrl', 'instanceurl', 'instance_url'])) }
if (targetType == 'FLOW') { forwardUrl = '/__tenant/' + tenantId + '/services/' + targetRef }

// 步骤 13：成功日志与统一响应包。
logEvent('REQUEST', 200, '')
return {
    code: 0,
    message: 'success',
    traceId: traceId,
    data: {
        serviceCode: serviceCode,
        version: version,
        targetType: targetType,
        targetRef: targetRef,
        forwardUrl: forwardUrl,
        regionCode: regionCode,
        authMode: authMode,
        instanceCount: instanceRows == null ? 0 : instanceRows.size(),
        retryTimes: num(first(route, ['retryTimes', 'retrytimes', 'retry_times'])),
        timeoutMs: num(first(route, ['timeoutMs', 'timeoutms', 'timeout_ms']))
    }
}
