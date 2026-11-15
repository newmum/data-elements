{
  "properties": {},
  "id": "bf6b8c7a4c2d5f0b3a5e6d7c8b9f0a15",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "18.灰度发布",
  "createTime": 1789000000106,
  "updateTime": 1789000000106,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/gray-releases",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务灰度发布：action=list 查询，action=save 新增或更新；支持权重、Header、IP 三种策略。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.灰度发布
// 路由：POST /dws/service/gateway/gray-releases
// 业务说明：按当前租户维护灰度规则；网关匹配时灰度规则优先于固定版本。
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
        fromVersion: text(first(row, ['fromVersion', 'fromversion', 'from_version'])),
        toVersion: text(first(row, ['toVersion', 'toversion', 'to_version'])),
        strategy: text(first(row, ['strategy', 'STRATEGY'])),
        matchValue: text(first(row, ['matchValue', 'matchvalue', 'match_value'])),
        weight: num(first(row, ['weight', 'WEIGHT'])),
        status: text(first(row, ['status', 'STATUS'])),
        startTime: first(row, ['startTime', 'starttime', 'start_time']),
        endTime: first(row, ['endTime', 'endtime', 'end_time']),
        description: text(first(row, ['description', 'DESCRIPTION']))
    }
}

if (action == 'list') {
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    var rows = db.select("select tid, service_code, from_version, to_version, strategy, match_value, weight, status, start_time, end_time, description from svc_gray_release_t " + whereSql + " order by created_time desc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var fromVersion = body?.fromVersion == null ? '' : (body.fromVersion + '').trim()
    var toVersion = body?.toVersion == null ? '' : (body.toVersion + '').trim()
    var strategy = body?.strategy == null ? 'WEIGHT' : (body.strategy + '').trim().toUpperCase()
    var matchValue = body?.matchValue == null ? '' : (body.matchValue + '').trim()
    var weight = body?.weight == null ? 0 : (body.weight + '')::int(0)
    var status = body?.status == null ? 'DRAFT' : (body.status + '').trim().toUpperCase()
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (serviceCode == '' || fromVersion == '' || toVersion == '') { exit 400, '服务编码、原版本和目标版本不能为空' }
    if (!['WEIGHT', 'HEADER', 'IP'].contains(strategy)) { exit 400, '灰度策略必须是 WEIGHT/HEADER/IP' }
    if (!['DRAFT', 'RUNNING', 'STOPPED'].contains(status)) { exit 400, '状态必须是 DRAFT/RUNNING/STOPPED' }
    if (weight < 0 || weight > 100) { exit 400, '灰度权重必须在 0-100 之间' }
    if (strategy != 'WEIGHT' && matchValue == '') { exit 400, 'Header/IP 灰度必须填写匹配值' }
    if (id == '') {
        var duplicate = db.select("select count(1) as n from svc_gray_release_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and from_version = #{fromVersion} and to_version = #{toVersion} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, fromVersion: fromVersion, toVersion: toVersion })
        if (duplicate[0].n > 0) { exit 409, '该服务版本灰度规则已存在' }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into svc_gray_release_t (tid, tenant_id, service_code, from_version, to_version, strategy, match_value, weight, status, start_time, end_time, description, created_by, updated_by, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{serviceCode}, #{fromVersion}, #{toVersion}, #{strategy}, #{matchValue}, #{weight}, #{status}, current_timestamp, null, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, serviceCode: serviceCode, fromVersion: fromVersion, toVersion: toVersion, strategy: strategy, matchValue: matchValue, weight: weight, status: status, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from svc_gray_release_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '灰度规则不存在或不在当前租户' }
        db.update("update svc_gray_release_t set service_code = #{serviceCode}, from_version = #{fromVersion}, to_version = #{toVersion}, strategy = #{strategy}, match_value = #{matchValue}, weight = #{weight}, status = #{status}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { serviceCode: serviceCode, fromVersion: fromVersion, toVersion: toVersion, strategy: strategy, matchValue: matchValue, weight: weight, status: status, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, service_code, from_version, to_version, strategy, match_value, weight, status, start_time, end_time, description from svc_gray_release_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

exit 400, '不支持的 action：' + action
