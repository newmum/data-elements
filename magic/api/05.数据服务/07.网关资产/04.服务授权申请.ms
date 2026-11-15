{
  "properties": {},
  "id": "860805c3bf744fb18e2dce79d4d1739d",
  "script": null,
  "groupId": "9a427a26bc984532b8595da7142765d2",
  "name": "04.服务授权申请",
  "createTime": 1793903068311,
  "updateTime": 1793903068311,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/apply",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"apiId\": \"fl_b50218412a\",\n  \"applyName\": \"广电应标验证-节目推荐服务 API 授权\",\n  \"useScene\": \"节目推荐应用调用服务数据\",\n  \"useDays\": 365\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"applyFormId\": \"2119000000000000001\",\n  \"authorizationId\": \"2119000000000000002\",\n  \"apiId\": \"fl_b50218412a\",\n  \"authorizationStatus\": \"AUTHORIZED\",\n  \"gatewaySyncStatus\": \"PENDING\",\n  \"expireTime\": \"2027-11-06 10:00:00\"\n}",
  "description": "服务管理页发起 API 授权申请（POST /dws/gateway/apply）。校验服务属于当前租户且在当前数据范围内，创建 data_apply_form_t 申请单，写入 da_prop_t 使用场景/有效期属性，并登记 data_api_authorization_t 授权台账；平台现行规则申请即时生效，网关同步状态保留 PENDING 供后续推送。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import log
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import "@post:/sym/user/me" as me
import tenantRuntime
import dataScope

// 接口：07.服务授权申请
// 路由：POST /dws/gateway/apply
// 业务说明：服务管理页发起 API 授权申请；创建 data_apply_form_t 申请单并写入
// data_api_authorization_t 授权台账。平台已取消审批，申请单按现行规则直接生效；
// 网关同步状态保留 PENDING，不伪造“网关已同步”。

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var now = new Date()
var tenantId = tenantRuntime.id()
var user = me()
var userId = text(user?.userId || user?.id || user?.tid)
var userOrgId = text(user?.orgId)
var userName = text(user?.userName || user?.realName || user?.name || userId)
if (blank(userId)) {
    exit 401, '登录状态无效，请重新登录'
}

var apiId = text(body?.apiId).trim()
var applyName = text(body?.applyName).trim()
var useScene = text(body?.useScene).trim()
var useDays = body?.useDays == null ? 365 : body.useDays::int(365)
if (blank(apiId)) {
    exit 400, '服务标识不能为空'
}
if (apiId.length() > 32) {
    exit 400, '服务标识格式不正确'
}
if (blank(useScene)) {
    exit 400, '请填写使用场景'
}
if (useScene.length() > 500) {
    exit 400, '使用场景不能超过500字'
}
if (useDays < 1 || useDays > 3650) {
    exit 400, '授权有效期需在1到3650天之间'
}
if (blank(applyName)) {
    applyName = 'API授权申请-' + apiId
}
if (applyName.length() > 120) {
    applyName = applyName.substring(0, 120)
}

// 服务必须属于当前租户，并落在当前数据范围内；范围判定与列表接口一致。
var api = db.table('api_info_t').where()
    .eq('tid', apiId)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .selectOne()
if (api == null) {
    exit 404, '服务不存在或不属于当前租户'
}
var scope = dataScope.dataSource()
if (scope.scope == 'OWNER') {
    if (text(api.createdBy || api.created_by) != text(scope.userId)) {
        exit 404, '服务不在当前数据范围内'
    }
} else if (scope.scope == 'ORG') {
    var allowed = false
    for (orgId in scope.organizationIds) {
        if (text(orgId) == text(api.orgId || api.org_id)) {
            allowed = true
        }
    }
    if (!allowed) {
        exit 404, '服务不在当前数据范围内'
    }
} else if (scope.scope != 'ALL') {
    exit 403, '数据范围无效'
}

// 同一服务存在未过期授权时不再重复申请。
var existed = db.table('data_api_authorization_t').where()
    .eq('api_id', apiId)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .eq('authorization_status', 'AUTHORIZED')
    .select()
for (item in existed) {
    if (item.expireTime == null || item.expireTime.getTime() > now.getTime()) {
        exit 409, '该服务已授权，无需重复申请'
    }
}

var formId = IdUtil.getSnowflakeNextIdStr()
var authId = IdUtil.getSnowflakeNextIdStr()
var expireTime = new Date(now.getTime() + useDays * 86400000)
var apiOrgId = text(api.orgId || api.org_id)
var apiCatalogId = text(api.catalogId || api.catalog_id)
var apiName = text(api.serviceName || api.service_name || api.name)

// ========== 1. 申请单主表 ==========
db.table('data_apply_form_t').primary('tid').insert({
    tid: formId,
    apply_user_id: userId,
    apply_user_name: userName,
    apply_org_id: userOrgId,
    apply_name: applyName,
    catalog_ids: apiId,
    type: 'apiAuthorization',
    flow_status: 2,
    tenant_id: tenantId,
    created_by: userId,
    created_time: now,
    updated_by: userId,
    updated_time: now,
    is_del: 0
})

// ========== 2. 申请单动态属性 ==========
var propList = [
    {
        tid: IdUtil.getSnowflakeNextIdStr(),
        tenant_id: tenantId,
        parent_id: formId,
        prop_name: 'useScene',
        prop_value: useScene,
        prop_type: '0',
        data_type: 'apply_form',
        is_del: 0,
        created_by: userId,
        created_time: now,
        updated_by: userId,
        updated_time: now
    },
    {
        tid: IdUtil.getSnowflakeNextIdStr(),
        tenant_id: tenantId,
        parent_id: formId,
        prop_name: 'useDays',
        prop_value: text(useDays),
        prop_type: '0',
        data_type: 'apply_form',
        is_del: 0,
        created_by: userId,
        created_time: now,
        updated_by: userId,
        updated_time: now
    },
    {
        tid: IdUtil.getSnowflakeNextIdStr(),
        tenant_id: tenantId,
        parent_id: formId,
        prop_name: 'serviceName',
        prop_value: apiName,
        prop_type: '0',
        data_type: 'apply_form',
        is_del: 0,
        created_by: userId,
        created_time: now,
        updated_by: userId,
        updated_time: now
    }
]
db.table('da_prop_t').batchInsert(propList)

// ========== 3. API 授权台账 ==========
db.table('data_api_authorization_t').primary('tid').insert({
    tid: authId,
    apply_form_id: formId,
    catalog_id: apiCatalogId,
    api_id: apiId,
    application_id: null,
    apply_user_id: userId,
    authorization_status: 'AUTHORIZED',
    gateway_sync_status: 'PENDING',
    authorization_scope: 'API_INVOKE',
    gateway_message: '平台申请已生效，待推送服务网关授权',
    authorized_time: now,
    expire_time: expireTime,
    tenant_id: tenantId,
    created_by: userId,
    created_time: now,
    updated_by: userId,
    updated_time: now,
    is_del: 0
})

// ========== 4. 申请单检索投影，供“我的申请”列表读取 ==========
try {
    var esDoc = {
        tid: formId,
        applyUserId: userId,
        applyUserName: userName,
        applyOrgId: userOrgId,
        applyName: applyName,
        type: 'apiAuthorization',
        catalogIds: apiId,
        flowStatus: 2,
        tenantId: tenantId,
        createdTime: now,
        updatedTime: now,
        applyResource: [apiId],
        useScene: useScene,
        useDays: text(useDays),
        serviceName: apiName
    }
    esCommonService.saveOrUpdate('dataassets_apply_form', formId, jsons.toJSONStr(esDoc), false)
} catch (e) {
    log.warn('同步 API 授权申请单 ES 失败, formId: {}, error: {}', formId, e.message)
}

log.info('API 授权申请成功, apiId: {}, formId: {}, authId: {}', apiId, formId, authId)

return {
    applyFormId: formId,
    authorizationId: authId,
    apiId: apiId,
    serviceName: apiName,
    applyName: applyName,
    authorizationStatus: 'AUTHORIZED',
    gatewaySyncStatus: 'PENDING',
    useDays: useDays,
    expireTime: expireTime
}
