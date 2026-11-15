{
  "properties": {},
  "id": "9d4f6a5e2a0b4d8f1e3c4b5a6f7d8e93",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "16.接入应用",
  "createTime": 1789000000104,
  "updateTime": 1789000000104,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/apps",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务网关接入应用管理：action=list 查询，action=save 新增或更新；凭证只保存引用，不保存明文。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.接入应用
// 路由：POST /dws/service/gateway/apps
// 业务说明：接入应用与 client_id 管理；密钥由凭证引用解析，禁止在表内保存明文。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
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
        appCode: text(first(row, ['appCode', 'appcode', 'app_code'])),
        appName: text(first(row, ['appName', 'appname', 'app_name'])),
        authType: text(first(row, ['authType', 'authtype', 'auth_type'])),
        clientId: text(first(row, ['clientId', 'clientid', 'client_id'])),
        credentialRef: text(first(row, ['credentialRef', 'credentialref', 'credential_ref'])),
        contact: text(first(row, ['contact', 'CONTACT'])),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var keyword = body?.keyword == null ? '' : (body.keyword + '').trim()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    if (keyword != '') {
        var kw = "'%" + keyword.replace("'", "''") + "%'"
        whereSql += " and (app_code like " + kw + " or app_name like " + kw + " or client_id like " + kw + ") "
    }
    var rows = db.select("select tid, app_code, app_name, auth_type, client_id, credential_ref, contact, status, description, created_time from svc_gateway_app_t " + whereSql + " order by created_time desc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var appCode = body?.appCode == null ? '' : (body.appCode + '').trim().toUpperCase()
    var appName = body?.appName == null ? '' : (body.appName + '').trim()
    var authType = body?.authType == null ? 'API_KEY' : (body.authType + '').trim().toUpperCase()
    var clientId = body?.clientId == null ? '' : (body.clientId + '').trim()
    var credentialRef = body?.credentialRef == null ? '' : (body.credentialRef + '').trim()
    var contact = body?.contact == null ? '' : (body.contact + '').trim()
    var status = body?.status == null ? 'ENABLED' : (body.status + '').trim().toUpperCase()
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (appCode == '' || appName == '') { exit 400, '应用编码和应用名称不能为空' }
    if (!['NONE', 'API_KEY', 'HMAC', 'OAUTH2'].contains(authType)) { exit 400, '鉴权类型必须是 NONE/API_KEY/HMAC/OAUTH2' }
    if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    if (id == '') {
        var duplicate = db.select("select count(1) as n from svc_gateway_app_t where tenant_id = #{tenantId} and app_code = #{appCode} and is_del = 0", { tenantId: tenantId, appCode: appCode })
        if (duplicate[0].n > 0) { exit 409, '应用编码已存在：' + appCode }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into svc_gateway_app_t (tid, tenant_id, app_code, app_name, auth_type, client_id, credential_ref, contact, status, description, created_by, updated_by, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{appCode}, #{appName}, #{authType}, #{clientId}, #{credentialRef}, #{contact}, #{status}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, appCode: appCode, appName: appName, authType: authType, clientId: clientId, credentialRef: credentialRef, contact: contact, status: status, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from svc_gateway_app_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '应用不存在或不在当前租户' }
        var duplicate = db.select("select count(1) as n from svc_gateway_app_t where tenant_id = #{tenantId} and app_code = #{appCode} and tid <> #{id} and is_del = 0", { tenantId: tenantId, appCode: appCode, id: id })
        if (duplicate[0].n > 0) { exit 409, '应用编码已存在：' + appCode }
        db.update("update svc_gateway_app_t set app_code = #{appCode}, app_name = #{appName}, auth_type = #{authType}, client_id = #{clientId}, credential_ref = #{credentialRef}, contact = #{contact}, status = #{status}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { appCode: appCode, appName: appName, authType: authType, clientId: clientId, credentialRef: credentialRef, contact: contact, status: status, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, app_code, app_name, auth_type, client_id, credential_ref, contact, status, description, created_time from svc_gateway_app_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

exit 400, '不支持的 action：' + action
