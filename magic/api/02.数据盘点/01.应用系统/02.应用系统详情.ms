{
  "properties": {},
  "id": "datapd_application_detail_01",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "02.应用系统详情",
  "createTime": null,
  "updateTime": 1790434056338,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tid\":\"01910f1ff2ba4933b2cd759057d3fbf4\"}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"contactPhone\": \"87094269\",\n        \"dbTotal\": \"2\",\n        \"contactName\": \"董玉珂\",\n        \"applicationClass\": \"基础类\",\n        \"applicationSystemName\": \"福建省流动人口综合信息服务管理信息平台\",\n        \"assetStatusOther\": \"在用\",\n        \"buildUnit\": null,\n        \"migrationSourceId\": \"01910f1ff2ba4933b2cd759057d3fbf4\",\n        \"onlineTime\": \"2024-04-29 15:09:26\",\n        \"appName\": \"福建省流动人口综合信息服务管理信息平台\",\n        \"provinceUrl\": null,\n        \"offlineTime\": null,\n        \"orgId\": \"1351480093888868353\",\n        \"manageUnit\": \"1351480093888868353\",\n        \"tid\": \"01910f1ff2ba4933b2cd759057d3fbf4\",\n        \"assetDesc\": \"福建省流动人口综合信息服务管理信息平台\",\n        \"migrationSourceTable\": \"asset_application_t\",\n        \"migrationSourceDb\": \"dataassets_registration_ga\",\n        \"id\": \"01910f1ff2ba4933b2cd759057d3fbf4\",\n        \"assetType\": \"app\",\n        \"tenantId\": null,\n        \"createdTime\": \"2024-04-29 15:09:26\",\n        \"updatedTime\": \"2024-05-24 16:34:05\",\n        \"regTime\": \"2024-04-29 15:09:25\",\n        \"assetStatus\": 2,\n        \"_debug\": {\n            \"tid\": \"01910f1ff2ba4933b2cd759057d3fbf4\",\n            \"clientId\": null,\n            \"clientSecret\": null,\n            \"appName\": \"福建省流动人口综合信息服务管理信息平台\",\n            \"appDesc\": \"福建省流动人口综合信息服务管理信息平台\",\n            \"appUrl\": null,\n            \"autoApprove\": null,\n            \"accressValidity\": null,\n            \"refreshValidity\": null,\n            \"notifyUrl\": null,\n            \"returnUrl\": null,\n            \"scope\": null,\n            \"authTypes\": null,\n            \"isUse\": 1,\n            \"seq\": null,\n            \"logo\": null,\n            \"publicKey\": null,\n            \"privateKey\": null,\n            \"autoapprove1\": null,\n            \"assetClass\": \"基础类\",\n            \"sortNo\": null,\n            \"config\": \"{\\\"contactPhone\\\":\\\"87094269\\\",\\\"dbTotal\\\":\\\"2\\\",\\\"contactName\\\":\\\"董玉珂\\\",\\\"applicationClass\\\":\\\"基础类\\\",\\\"applicationSystemName\\\":\\\"福建省流动人口综合信息服务管理信息平台\\\",\\\"assetStatusOther\\\":\\\"在用\\\",\\\"buildUnit\\\":null,\\\"migrationSourceId\\\":\\\"01910f1ff2ba4933b2cd759057d3fbf4\\\",\\\"onlineTime\\\":\\\"2024-04-29 15:09:26\\\",\\\"appName\\\":\\\"福建省流动人口综合信息服务管理信息平台\\\",\\\"provinceUrl\\\":null,\\\"offlineTime\\\":null,\\\"orgId\\\":\\\"1351480093888868353\\\",\\\"manageUnit\\\":\\\"1351480093888868353\\\",\\\"tid\\\":\\\"01910f1ff2ba4933b2cd759057d3fbf4\\\",\\\"assetDesc\\\":\\\"福建省流动人口综合信息服务管理信息平台\\\",\\\"migrationSourceTable\\\":\\\"asset_application_t\\\",\\\"migrationSourceDb\\\":\\\"dataassets_registration_ga\\\"}\",\n            \"departId\": \"1351480093888868353\",\n            \"createdBy\": \"1377560448924127233\",\n            \"createdTime\": \"2024-04-29 15:09:26\",\n            \"updatedBy\": \"1377560448924127233\",\n            \"updatedTime\": \"2024-05-24 16:34:05\",\n            \"isDel\": 0,\n            \"tenantId\": null,\n            \"revision\": null,\n            \"regTime\": \"2024-04-29 15:09:25\",\n            \"assetStatus\": 2,\n            \"flowStatus\": 0,\n            \"flowOrderId\": null,\n            \"dbTotal\": 2\n        }\n    }\n}",
  "description": "根据 tid 从 sym_application_t 与 da_prop_t 合并应用系统详情，不读取 ES。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.应用系统详情
// 路由：POST /dst/application/detail
// 业务说明：根据 tid 从 sym_application_t 与 da_prop_t 合并应用系统详情，不读取 ES。
import java.util.Map
import tenantRuntime
var tenantId = tenantRuntime.id()

var blank = (value) => value == null || (value + '').trim() == ''
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}
var tid = body?.tid
var appName = body?.appName
var orgId = body?.orgId
var silent = body?.silent == true
if (blank(tid) && blank(appName)) {
    if (silent) {
        return { found: false }
    }
    exit 400, 'tid不能为空'
}
var main = blank(tid) ? null : db.table('sym_application_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (main == null && !blank(appName)) {
    var query = db.table('sym_application_t').where().eq('app_name', appName).eq('tenant_id', tenantId).eq('is_del', 0)
    // 按名称兜底查找时必须限定当前登记部门，避免跨部门同名应用系统误关联。
    if (!blank(orgId)) {
        query.eq('org_id', orgId)
    }
    main = query.selectOne()
}
if (main == null) {
    if (silent) {
        return { found: false }
    }
    exit 404, '应用系统不存在'
}
tid = first(main, ['tid'])
var result = {}
for (prop in db.table('da_prop_t').where().eq('parent_id', tid).eq('tenant_id', tenantId).eq('data_type', 'app').eq('is_del', 0).select()) {
    var name = first(prop, ['propName', 'prop_name'])
    if (!blank(name)) {
        result[name] = first(prop, ['propValue', 'prop_value'])
    }
}
result.tid = tid
result.id = tid
result.assetType = 'app'
result.appName = first(main, ['appName', 'app_name'])
result.assetDesc = first(main, ['appDesc', 'app_desc'])
result.appUrl = first(main, ['appUrl', 'app_url'])
result.provinceUrl = result.appUrl
result.applicationClass = first(main, ['assetClass', 'asset_class'])
result.orgId = first(main, ['orgId', 'org_id'])
result.tenantId = first(main, ['tenantId', 'tenant_id'])
result.createdTime = first(main, ['createdTime', 'created_time'])
result.updatedTime = first(main, ['updatedTime', 'updated_time'])
result.regTime = first(main, ['regTime', 'reg_time'])
result.assetStatus = 2
result.found = true
return result
