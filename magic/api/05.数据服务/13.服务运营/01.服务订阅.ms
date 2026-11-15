{
  "properties": {},
  "id": "e29e1f0d7f5a8c3e6d8b9a0f1e2c3d48",
  "script": null,
  "groupId": "094cd528dceb45278146eb6f68aa9c2b",
  "name": "01.服务订阅",
  "createTime": 1789000000109,
  "updateTime": 1789000000109,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/subscriptions",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务订阅：action=list 查询，action=save 新增或更新，action=revoke 撤销；审批通过后状态置 ACTIVE。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil;
import tenantRuntime

// 接口：01.服务订阅
// 路由：POST /dws/servicemarket/subscriptions
// 业务说明：订阅按应用与服务版本唯一；状态机 PENDING/ACTIVE/EXPIRED/REVOKED，撤销仅改状态。

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var userId = StpUtil.getLoginId() + ''
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
        appId: text(first(row, ['appId', 'appid', 'app_id'])),
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        version: text(first(row, ['version', 'VERSION'])),
        status: text(first(row, ['status', 'STATUS'])),
        validFrom: first(row, ['validFrom', 'validfrom', 'valid_from']),
        validUntil: first(row, ['validUntil', 'validuntil', 'valid_until']),
        quota: num(first(row, ['quota', 'QUOTA'])),
        applyId: text(first(row, ['applyId', 'applyid', 'apply_id'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var appId = body?.appId == null ? '' : (body.appId + '').trim()
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (appId != '') { whereSql += " and app_id = '" + appId.replace("'", "''") + "' " }
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    var rows = db.select("select tid, app_id, service_code, version, status, valid_from, valid_until, quota, apply_id, description, created_time from svc_subscription_t " + whereSql + " order by created_time desc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var appId = body?.appId == null ? '' : (body.appId + '').trim()
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var version = body?.version == null ? 'v1' : (body.version + '').trim()
    var status = body?.status == null ? 'PENDING' : (body.status + '').trim().toUpperCase()
    var validFrom = body?.validFrom == null ? '' : (body.validFrom + '').trim()
    var validUntil = body?.validUntil == null ? '' : (body.validUntil + '').trim()
    var quota = body?.quota == null ? 0 : (body.quota + '')::int(0)
    var applyId = body?.applyId == null ? '' : (body.applyId + '').trim()
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (appId == '' || serviceCode == '') { exit 400, '应用标识和服务编码不能为空' }
    if (!['PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED'].contains(status)) { exit 400, '状态必须是 PENDING/ACTIVE/EXPIRED/REVOKED' }
    if (validFrom == '' || validUntil == '') { exit 400, '生效时间和失效时间不能为空' }
    if (validFrom >= validUntil) { exit 400, '生效时间必须早于失效时间' }
    var app = db.select("select count(1) as n from svc_gateway_app_t where tid = #{appId} and tenant_id = #{tenantId} and is_del = 0", { appId: appId, tenantId: tenantId })
    if (app[0].n == 0) { exit 400, '订阅应用不存在或不在当前租户' }
    var route = db.select("select count(1) as n from svc_gateway_route_t where tenant_id = #{tenantId} and service_code = #{serviceCode} and version = #{version} and is_del = 0", { tenantId: tenantId, serviceCode: serviceCode, version: version })
    if (route[0].n == 0) { exit 400, '服务路由不存在：' + serviceCode + '/' + version }
    if (id == '') {
        var duplicate = db.select("select count(1) as n from svc_subscription_t where tenant_id = #{tenantId} and app_id = #{appId} and service_code = #{serviceCode} and version = #{version} and is_del = 0", { tenantId: tenantId, appId: appId, serviceCode: serviceCode, version: version })
        if (duplicate[0].n > 0) { exit 409, '该应用已订阅此服务版本' }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into svc_subscription_t (tid, tenant_id, app_id, service_code, version, status, valid_from, valid_until, quota, apply_id, description, created_by, updated_by, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{appId}, #{serviceCode}, #{version}, #{status}, #{validFrom}, #{validUntil}, #{quota}, #{applyId}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, appId: appId, serviceCode: serviceCode, version: version, status: status, validFrom: validFrom, validUntil: validUntil, quota: quota, applyId: applyId, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from svc_subscription_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '订阅不存在或不在当前租户' }
        db.update("update svc_subscription_t set app_id = #{appId}, service_code = #{serviceCode}, version = #{version}, status = #{status}, valid_from = #{validFrom}, valid_until = #{validUntil}, quota = #{quota}, apply_id = #{applyId}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { appId: appId, serviceCode: serviceCode, version: version, status: status, validFrom: validFrom, validUntil: validUntil, quota: quota, applyId: applyId, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, app_id, service_code, version, status, valid_from, valid_until, quota, apply_id, description, created_time from svc_subscription_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

if (action == 'revoke') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    if (id == '') { exit 400, '订阅标识不能为空' }
    var existing = db.select("select tid from svc_subscription_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '订阅不存在或不在当前租户' }
    db.update("update svc_subscription_t set status = 'REVOKED', updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { userId: userId, id: id, tenantId: tenantId })
    return { id: id, status: 'REVOKED' }
}

exit 400, '不支持的 action：' + action
