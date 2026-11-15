{
  "properties": {},
  "id": "ae5a7b6f3b1c4e9a2f4d5c6b7a8e9f04",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "17.服务实例",
  "createTime": 1789000000105,
  "updateTime": 1789000000105,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/instances",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务实例与健康状态：action=list 查询，action=save 批量登记，action=heartbeat 批量心跳。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.服务实例
// 路由：POST /dws/service/gateway/instances
// 业务说明：实例批量登记与心跳更新；批量避免逐实例查询，健康状态由心跳结果直接写入。
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
var view = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        version: text(first(row, ['version', 'VERSION'])),
        instanceUrl: text(first(row, ['instanceUrl', 'instanceurl', 'instance_url'])),
        weight: num(first(row, ['weight', 'WEIGHT'])),
        healthStatus: text(first(row, ['healthStatus', 'healthstatus', 'health_status'])),
        lastHeartbeat: first(row, ['lastHeartbeat', 'lastheartbeat', 'last_heartbeat']),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION']))
    }
}

if (action == 'list') {
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var version = body?.version == null ? '' : (body.version + '').trim()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (version != '') { whereSql += " and version = '" + version.replace("'", "''") + "' " }
    var rows = db.select("select tid, service_code, version, instance_url, weight, health_status, last_heartbeat, status, description from svc_gateway_instance_t " + whereSql + " order by service_code asc, version asc, weight desc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var items = body?.instances == null ? [] : body.instances
    if (items.size() == 0) { exit 400, '实例列表不能为空' }
    var savedIds = []
    for (item in items) {
        var id = item?.id == null ? '' : (item.id + '').trim()
        var serviceCode = item?.serviceCode == null ? '' : (item.serviceCode + '').trim()
        var version = item?.version == null ? 'v1' : (item.version + '').trim()
        var instanceUrl = item?.instanceUrl == null ? '' : (item.instanceUrl + '').trim()
        if (serviceCode == '' || instanceUrl == '') { exit 400, '服务编码和实例地址不能为空' }
        var weight = item?.weight == null ? 100 : (item.weight + '')::int(100)
        if (weight < 1 || weight > 10000) { exit 400, '实例权重必须在 1-10000 之间' }
        var healthStatus = item?.healthStatus == null ? 'UNKNOWN' : (item.healthStatus + '').trim().toUpperCase()
        if (!['UNKNOWN', 'UP', 'DOWN'].contains(healthStatus)) { exit 400, '健康状态必须是 UNKNOWN/UP/DOWN' }
        var status = item?.status == null ? 'ENABLED' : (item.status + '').trim().toUpperCase()
        if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
        if (id == '') {
            var existing = db.select("select tid from svc_gateway_instance_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and instance_url = #{instanceUrl} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, version: version, instanceUrl: instanceUrl })
            if (existing != null && existing.size() > 0) {
                id = existing[0].tid + ''
                db.update("update svc_gateway_instance_t set weight = #{weight}, health_status = #{healthStatus}, last_heartbeat = current_timestamp, status = #{status}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { weight: weight, healthStatus: healthStatus, status: status, userId: userId, id: id, tenantId: tenantId })
            } else {
                id = IdUtil.simpleUUID()
                db.update("""
                    insert into svc_gateway_instance_t (tid, tenant_id, service_code, version, instance_url, weight, health_status, last_heartbeat, status, description, created_by, updated_by, created_time, updated_time, is_del)
                    values (#{id}, #{tenantId}, #{serviceCode}, #{version}, #{instanceUrl}, #{weight}, #{healthStatus}, current_timestamp, #{status}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
                """, { id: id, tenantId: tenantId, serviceCode: serviceCode, version: version, instanceUrl: instanceUrl, weight: weight, healthStatus: healthStatus, status: status, description: item?.description == null ? '' : (item.description + '').trim(), userId: userId })
            }
        } else {
            var owned = db.select("select tid from svc_gateway_instance_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
            if (owned == null || owned.size() == 0) { exit 404, '实例不存在或不在当前租户：' + id }
            db.update("update svc_gateway_instance_t set service_code = #{serviceCode}, version = #{version}, instance_url = #{instanceUrl}, weight = #{weight}, health_status = #{healthStatus}, status = #{status}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { serviceCode: serviceCode, version: version, instanceUrl: instanceUrl, weight: weight, healthStatus: healthStatus, status: status, description: item?.description == null ? '' : (item.description + '').trim(), userId: userId, id: id, tenantId: tenantId })
        }
        savedIds.add(id)
    }
    return { ids: savedIds, total: savedIds.size() }
}

if (action == 'heartbeat') {
    var items = body?.instances == null ? [] : body.instances
    if (items.size() == 0) { exit 400, '心跳实例列表不能为空' }
    var updated = 0
    for (item in items) {
        var id = item?.id == null ? '' : (item.id + '').trim()
        var healthStatus = item?.healthStatus == null ? 'UP' : (item.healthStatus + '').trim().toUpperCase()
        if (!['UP', 'DOWN'].contains(healthStatus)) { exit 400, '心跳健康状态必须是 UP/DOWN' }
        if (id == '') { continue }
        var result = db.update("update svc_gateway_instance_t set health_status = #{healthStatus}, last_heartbeat = current_timestamp, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { healthStatus: healthStatus, id: id, tenantId: tenantId })
        updated = updated + 1
    }
    return { updated: updated }
}

exit 400, '不支持的 action：' + action
