{
  "properties": {},
  "id": "c07c9d8b5d3e6a1c4b6f7e8d9c0a1b26",
  "script": null,
  "groupId": "115458a314ce4956b65d27d47afd61ce",
  "name": "03.网关日志",
  "createTime": 1789000000107,
  "updateTime": 1789000000107,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logs",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\",\n  \"pageNo\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "网关日志：action=list 按 traceId/服务/类型/时间分页检索，action=report 写入单条日志。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime

// 接口：03.网关日志
// 路由：POST /dws/gaterun/logs
// 业务说明：9 类日志共用一张表；列表固定一次计数、一次分页，不按行补查。

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
        traceId: text(first(row, ['traceId', 'traceid', 'trace_id'])),
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        appId: text(first(row, ['appId', 'appid', 'app_id'])),
        logType: text(first(row, ['logType', 'logtype', 'log_type'])),
        uri: text(first(row, ['uri', 'URI'])),
        method: text(first(row, ['method', 'METHOD'])),
        statusCode: num(first(row, ['statusCode', 'statuscode', 'status_code'])),
        costMs: num(first(row, ['costMs', 'costms', 'cost_ms'])),
        errorCode: text(first(row, ['errorCode', 'errorcode', 'error_code'])),
        clientIp: text(first(row, ['clientIp', 'clientip', 'client_ip'])),
        regionCode: text(first(row, ['regionCode', 'regioncode', 'region_code'])),
        requestHash: text(first(row, ['requestHash', 'requesthash', 'request_hash'])),
        responseSize: num(first(row, ['responseSize', 'responsesize', 'response_size'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var pageNo = body?.pageNo == null ? 1 : (body.pageNo + '')::int(1)
    var pageSize = body?.pageSize == null ? 20 : (body.pageSize + '')::int(1)
    if (pageNo < 1) { pageNo = 1 }
    if (pageSize < 1) { pageSize = 20 }
    if (pageSize > 100) { pageSize = 100 }
    var traceId = body?.traceId == null ? '' : (body.traceId + '').trim()
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var appId = body?.appId == null ? '' : (body.appId + '').trim()
    var logType = body?.logType == null ? '' : (body.logType + '').trim().toUpperCase()
    var startTime = body?.startTime == null ? '' : (body.startTime + '').trim()
    var endTime = body?.endTime == null ? '' : (body.endTime + '').trim()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (traceId != '') { whereSql += " and trace_id = '" + traceId.replace("'", "''") + "' " }
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (appId != '') { whereSql += " and app_id = '" + appId.replace("'", "''") + "' " }
    if (logType != '') { whereSql += " and log_type = '" + logType.replace("'", "''") + "' " }
    if (startTime != '') { whereSql += " and created_time >= '" + startTime.replace("'", "''") + "' " }
    if (endTime != '') { whereSql += " and created_time <= '" + endTime.replace("'", "''") + "' " }
    var countRows = db.select("select count(1) as total from svc_gateway_log_t " + whereSql, { tenantId: tenantId })
    var total = countRows == null || countRows.size() == 0 ? 0 : num(first(countRows[0], ['total', 'TOTAL']))
    var offset = (pageNo - 1) * pageSize
    var rows = db.select("select tid, trace_id, service_code, app_id, log_type, uri, method, status_code, cost_ms, error_code, client_ip, region_code, request_hash, response_size, created_time from svc_gateway_log_t " + whereSql + " order by created_time desc LIMIT " + pageSize + " OFFSET " + offset, { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: total, pageNo: pageNo, pageSize: pageSize }
}

if (action == 'report') {
    var log = body?.log == null ? body : body
    var traceId = log?.traceId == null ? '' : (log.traceId + '').trim()
    var serviceCode = log?.serviceCode == null ? '' : (log.serviceCode + '').trim()
    var logType = log?.logType == null ? 'EVENT' : (log.logType + '').trim().toUpperCase()
    if (serviceCode == '' || logType == '') { exit 400, '服务编码和日志类型不能为空' }
    var id = IdUtil.simpleUUID()
    db.update("""
        insert into svc_gateway_log_t (tid, tenant_id, trace_id, service_code, app_id, log_type, uri, method, status_code, cost_ms, error_code, client_ip, region_code, request_hash, response_size, created_time, updated_time, is_del)
        values (#{id}, #{tenantId}, #{traceId}, #{serviceCode}, #{appId}, #{logType}, #{uri}, #{method}, #{statusCode}, #{costMs}, #{errorCode}, #{clientIp}, #{regionCode}, #{requestHash}, #{responseSize}, current_timestamp, current_timestamp, 0)
    """, { id: id, tenantId: tenantId, traceId: traceId, serviceCode: serviceCode, appId: log?.appId == null ? '' : (log.appId + '').trim(), logType: logType, uri: log?.uri == null ? '' : (log.uri + '').trim(), method: log?.method == null ? '' : (log.method + '').trim().toUpperCase(), statusCode: log?.statusCode == null ? 0 : (log.statusCode + '')::int(0), costMs: log?.costMs == null ? 0 : (log.costMs + '')::int(0), errorCode: log?.errorCode == null ? '' : (log.errorCode + '').trim(), clientIp: log?.clientIp == null ? '' : (log.clientIp + '').trim(), regionCode: log?.regionCode == null ? '' : (log.regionCode + '').trim(), requestHash: log?.requestHash == null ? '' : (log.requestHash + '').trim(), responseSize: log?.responseSize == null ? 0 : (log.responseSize + '')::int(0) })
    return { id: id, traceId: traceId }
}

exit 400, '不支持的 action：' + action
