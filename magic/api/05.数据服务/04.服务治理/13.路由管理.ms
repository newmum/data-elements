{
  "properties": {},
  "id": "6a1c3f2b9d7e4a5c8b0f1e2d3c4a5b60",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "13.路由管理",
  "createTime": 1789000000101,
  "updateTime": 1789000000101,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/routes",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\",\n  \"pageNo\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务网关路由管理：action=list 分页查询，action=save 新增或更新，action=status 发布/停用。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.路由管理
// 路由：POST /dws/service/gateway/routes
// 业务说明：当前租户内路由分页、保存与状态变更；一次计数、一次分页，不按行补查。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
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
var routeView = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        tid: text(first(row, ['tid', 'id'])),
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        version: text(first(row, ['version', 'VERSION'])),
        pathPrefix: text(first(row, ['pathPrefix', 'pathprefix', 'path_prefix'])),
        targetType: text(first(row, ['targetType', 'targettype', 'target_type'])),
        targetRef: text(first(row, ['targetRef', 'targetref', 'target_ref'])),
        regionCode: text(first(row, ['regionCode', 'regioncode', 'region_code'])),
        timeoutMs: num(first(row, ['timeoutMs', 'timeoutms', 'timeout_ms'])),
        retryTimes: num(first(row, ['retryTimes', 'retrytimes', 'retry_times'])),
        lbStrategy: text(first(row, ['lbStrategy', 'lbstrategy', 'lb_strategy'])),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time']),
        updatedTime: first(row, ['updatedTime', 'updatedtime', 'updated_time'])
    }
}

if (action == 'list') {
    var pageNo = body?.pageNo == null ? 1 : (body.pageNo + '')::int(1)
    var pageSize = body?.pageSize == null ? 20 : (body.pageSize + '')::int(1)
    if (pageNo < 1) { pageNo = 1 }
    if (pageSize < 1) { pageSize = 20 }
    if (pageSize > 100) { pageSize = 100 }
    var keyword = body?.keyword == null ? '' : (body.keyword + '').trim()
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var targetType = body?.targetType == null ? '' : (body.targetType + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    if (targetType != '') { whereSql += " and target_type = '" + targetType.replace("'", "''") + "' " }
    if (keyword != '') {
        var kw = "'%" + keyword.replace("'", "''") + "%'"
        whereSql += " and (service_code like " + kw + " or path_prefix like " + kw + " or target_ref like " + kw + ") "
    }
    var countRows = db.select("select count(1) as total from svc_gateway_route_t " + whereSql, { tenantId: tenantId })
    var total = countRows == null || countRows.size() == 0 ? 0 : num(first(countRows[0], ['total', 'TOTAL']))
    var offset = (pageNo - 1) * pageSize
    var rows = db.select("select tid, service_code, version, path_prefix, target_type, target_ref, region_code, timeout_ms, retry_times, lb_strategy, status, description, created_time, updated_time from svc_gateway_route_t " + whereSql + " order by created_time desc LIMIT " + pageSize + " OFFSET " + offset, { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(routeView(row)) }
    return { list: list, total: total, pageNo: pageNo, pageSize: pageSize }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var version = body?.version == null ? 'v1' : (body.version + '').trim()
    var pathPrefix = body?.pathPrefix == null ? '' : (body.pathPrefix + '').trim()
    var targetType = body?.targetType == null ? 'FLOW' : (body.targetType + '').trim().toUpperCase()
    var targetRef = body?.targetRef == null ? '' : (body.targetRef + '').trim()
    var regionCode = body?.regionCode == null ? '' : (body.regionCode + '').trim()
    var timeoutMs = body?.timeoutMs == null ? 3000 : (body.timeoutMs + '')::int(3000)
    var retryTimes = body?.retryTimes == null ? 0 : (body.retryTimes + '')::int(0)
    var lbStrategy = body?.lbStrategy == null ? 'WEIGHT' : (body.lbStrategy + '').trim().toUpperCase()
    var status = body?.status == null ? 'DRAFT' : (body.status + '').trim().toUpperCase()
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (serviceCode == '' || pathPrefix == '' || targetRef == '') { exit 400, '服务编码、路径前缀和目标引用不能为空' }
    if (!['FLOW', 'HTTP', 'DB', 'KAFKA', 'ES'].contains(targetType)) { exit 400, '目标类型必须是 FLOW/HTTP/DB/KAFKA/ES' }
    if (!['DRAFT', 'PUBLISHED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 DRAFT/PUBLISHED/DISABLED' }
    if (!['WEIGHT', 'ROUND_ROBIN'].contains(lbStrategy)) { exit 400, '负载策略必须是 WEIGHT/ROUND_ROBIN' }
    if (timeoutMs < 100 || timeoutMs > 120000) { exit 400, '超时时间必须在 100-120000 毫秒之间' }
    if (regionCode != '') {
        var region = db.select("select count(1) as n from svc_gateway_region_t where tenant_id = #{tenantId} and region_code = #{regionCode} and is_del = 0", { tenantId: tenantId, regionCode: regionCode })
        if (region[0].n == 0) { exit 400, '网络区域不存在：' + regionCode }
    }
    if (id == '') {
        var duplicate = db.select("select count(1) as n from svc_gateway_route_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, version: version })
        if (duplicate[0].n > 0) { exit 409, '服务路由已存在：' + serviceCode + '/' + version }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into svc_gateway_route_t
              (tid, tenant_id, service_code, version, path_prefix, target_type, target_ref, region_code, timeout_ms, retry_times, lb_strategy, status, description, created_by, updated_by, created_time, updated_time, is_del)
            values
              (#{id}, #{tenantId}, #{serviceCode}, #{version}, #{pathPrefix}, #{targetType}, #{targetRef}, #{regionCode}, #{timeoutMs}, #{retryTimes}, #{lbStrategy}, #{status}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, serviceCode: serviceCode, version: version, pathPrefix: pathPrefix, targetType: targetType, targetRef: targetRef, regionCode: regionCode, timeoutMs: timeoutMs, retryTimes: retryTimes, lbStrategy: lbStrategy, status: status, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from svc_gateway_route_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '路由不存在或不在当前租户' }
        var duplicate = db.select("select count(1) as n from svc_gateway_route_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and tid <> #{id} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, version: version, id: id })
        if (duplicate[0].n > 0) { exit 409, '服务路由已存在：' + serviceCode + '/' + version }
        db.update("""
            update svc_gateway_route_t set service_code = #{serviceCode}, version = #{version}, path_prefix = #{pathPrefix},
              target_type = #{targetType}, target_ref = #{targetRef}, region_code = #{regionCode}, timeout_ms = #{timeoutMs},
              retry_times = #{retryTimes}, lb_strategy = #{lbStrategy}, status = #{status}, description = #{description},
              updated_by = #{userId}, updated_time = current_timestamp
            where tid = #{id} and tenant_id = #{tenantId} and is_del = 0
        """, { serviceCode: serviceCode, version: version, pathPrefix: pathPrefix, targetType: targetType, targetRef: targetRef, regionCode: regionCode, timeoutMs: timeoutMs, retryTimes: retryTimes, lbStrategy: lbStrategy, status: status, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, service_code, version, path_prefix, target_type, target_ref, region_code, timeout_ms, retry_times, lb_strategy, status, description, created_time, updated_time from svc_gateway_route_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return routeView(saved[0])
}

if (action == 'status') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var status = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    if (id == '') { exit 400, '路由标识不能为空' }
    if (!['DRAFT', 'PUBLISHED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 DRAFT/PUBLISHED/DISABLED' }
    var existing = db.select("select tid from svc_gateway_route_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '路由不存在或不在当前租户' }
    db.update("update svc_gateway_route_t set status = #{status}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { status: status, userId: userId, id: id, tenantId: tenantId })
    return { id: id, status: status }
}

exit 400, '不支持的 action：' + action
