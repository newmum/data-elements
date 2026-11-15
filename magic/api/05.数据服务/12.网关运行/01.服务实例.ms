{
  "properties": {},
  "id": "ae5a7b6f3b1c4e9a2f4d5c6b7a8e9f04",
  "script": null,
  "groupId": "115458a314ce4956b65d27d47afd61ce",
  "name": "01.服务实例",
  "createTime": 1789000000105,
  "updateTime": 1789000000105,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/instances",
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
import cn.hutool.core.util.IdUtil
import tenantRuntime
import tableRead
import tableWrite
import cn.hutool.json.JSONUtil
import "@post:/sym/user/me" as me

// 接口：01.服务实例
// 路由：POST /dws/gaterun/instances
// 业务说明：实例批量登记与心跳更新；批量避免逐实例查询，健康状态由心跳结果直接写入。

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

if (action == 'save' || action == 'heartbeat') {
    var items = body?.instances == null ? [] : body.instances
    var heartbeat = action == 'heartbeat'
    if (!(items instanceof List) || items.size() == 0 || items.size() > 200) {
        exit 400, '实例列表必须为 1 到 200 项的数组'
    }
    var requested = []
    var ids = []
    var natural = []
    var keys = {}
    var keyOf = (serviceCode, version, url) => JSONUtil.toJsonStr([serviceCode, version, url])
    for (item in items) {
        if (!(item instanceof Map)) { exit 400, '实例必须为对象' }
        var id = text(item.id).trim()
        var health = item.healthStatus == null ? (heartbeat ? 'UP' : 'UNKNOWN') : text(item.healthStatus).trim().toUpperCase()
        if (heartbeat) {
            if (!['UP', 'DOWN'].contains(health)) { exit 400, '心跳健康状态必须是 UP/DOWN' }
            if (id == '') { continue }
        } else if (!['UNKNOWN', 'UP', 'DOWN'].contains(health)) { exit 400, '健康状态必须是 UNKNOWN/UP/DOWN' }
        if (id != '') {
            if (ids.contains(id)) { exit 400, '实例标识不能重复：' + id }
            ids.add(id)
        }
        if (heartbeat) {
            requested.add({id: id, health: health})
            continue
        }
        var serviceCode = text(item.serviceCode).trim()
        var version = item.version == null ? 'v1' : text(item.version).trim()
        var url = text(item.instanceUrl).trim()
        if (serviceCode == '' || url == '') { exit 400, '服务编码和实例地址不能为空' }
        var weight = item.weight == null ? 100 : text(item.weight)::int(100)
        if (weight < 1 || weight > 10000) { exit 400, '实例权重必须在 1-10000 之间' }
        var status = item.status == null ? 'ENABLED' : text(item.status).trim().toUpperCase()
        if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
        var key = keyOf(serviceCode, version, url)
        if (keys[key] == true) { exit 400, '实例服务、版本与地址组合不能重复' }
        keys[key] = true
        var request = {id: id, key: key, serviceCode: serviceCode, version: version, url: url,
                       weight: weight, health: health, status: status, description: text(item.description).trim()}
        requested.add(request)
        if (id == '') { natural.add(request) }
    }

    return db.transaction(() => {
        var byKey = {}
        if (natural.size() > 0) {
            var field = (name) => tableRead.column('instance', name, 'TEXT')
            var query = tableRead.from('svc_gateway_instance_t', 'instance', tenantId)
            query.where(tableRead.equal(tableRead.column('instance', 'is_del', 'NUMBER'), 0))
            query.where(tableRead.any(natural.map(item => tableRead.all([
                tableRead.equal(field('service_code'), item.serviceCode),
                tableRead.equal(field('version'), item.version),
                tableRead.equal(field('instance_url'), item.url)
            ]))))
            // 按本批服务、版本和地址的完整组合一次预取，结果仍需按租户加锁。
            for (row in query.rows({tid: field('tid'), service_code: field('service_code'),
                                    version: field('version'), instance_url: field('instance_url')}, 200)) {
                var key = keyOf(row.service_code, row.version, row.instance_url)
                byKey[key] = row.tid
                if (!ids.contains(row.tid)) { ids.add(row.tid) }
            }
        }
        var locked = tableWrite.lockRows('svc_gateway_instance_t', tenantId, 'tid', ids,
            ['tid','service_code','version','instance_url'], {is_del: 0})
        var owned = {}
        for (row in locked) { owned[row.tid] = row }
        for (item in requested) {
            if (item.id != '' && owned[item.id] == null) { exit 404, '实例不存在或不在当前租户：' + item.id }
        }

        var now = new Date()
        if (heartbeat) {
            var up = []
            var down = []
            for (item in requested) {
                if (item.health == 'UP') { up.add(item.id) } else { down.add(item.id) }
            }
            var saveHeartbeat = (targetIds, health) => {
                if (targetIds.size() > 0) {
                    db.table('svc_gateway_instance_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
                        .in('tid', targetIds).update({health_status: health, last_heartbeat: now, updated_time: now})
                }
            }
            saveHeartbeat(up, 'UP')
            saveHeartbeat(down, 'DOWN')
            return {updated: ids.size()}
        }

        var insertion = []
        var explicitUpdates = []
        var matchedUpdates = []
        var saved = []
        for (item in requested) {
            var id = item.id
            if (id == '') {
                id = byKey[item.key]
                if (id == null) {
                    id = IdUtil.simpleUUID()
                    insertion.add({tid: id, tenant_id: tenantId, service_code: item.serviceCode, version: item.version,
                                   instance_url: item.url, weight: item.weight, health_status: item.health, status: item.status,
                                   description: item.description, last_heartbeat: now, created_by: userId, updated_by: userId,
                                   created_time: now, updated_time: now, is_del: 0})
                } else {
                    var row = owned[id]
                    if (row == null || keyOf(row.service_code, row.version, row.instance_url) != item.key) {
                        exit 409, '实例已变更，请刷新后重试'
                    }
                    matchedUpdates.add({tid: id, weight: item.weight, health_status: item.health,
                                        last_heartbeat: now, status: item.status, updated_by: userId, updated_time: now})
                }
            } else {
                explicitUpdates.add({tid: id, service_code: item.serviceCode, version: item.version, instance_url: item.url,
                                     weight: item.weight, health_status: item.health, status: item.status,
                                     description: item.description, updated_by: userId, updated_time: now})
            }
            if (saved.contains(id)) { exit 400, '同一实例不能同时按标识和地址更新' }
            saved.add(id)
        }
        // 全部标识通过归属检查后才写入。唯一键冲突会回滚整批。
        if (explicitUpdates.size() > 0) {
            tableWrite.batchUpdate('svc_gateway_instance_t', tenantId,
                ['service_code','version','instance_url','weight','health_status','status','description','updated_by','updated_time'],
                ['tid'], {is_del: 0}, explicitUpdates)
        }
        if (matchedUpdates.size() > 0) {
            tableWrite.batchUpdate('svc_gateway_instance_t', tenantId,
                ['weight','health_status','last_heartbeat','status','updated_by','updated_time'],
                ['tid'], {is_del: 0}, matchedUpdates)
        }
        if (insertion.size() > 0) { db.table('svc_gateway_instance_t').batchInsert(insertion, 200) }
        return {ids: saved, total: saved.size()}
    })
}

exit 400, '不支持的 action：' + action
