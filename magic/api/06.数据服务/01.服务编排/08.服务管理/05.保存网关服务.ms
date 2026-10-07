{
  "properties": {},
  "id": "d5a7019f5e4a4a92a34543c1f5290005",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "05.保存网关服务",
  "createTime": 1784450000000,
  "updateTime": 1787153758086,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"id\": \"2361887157562377213\",\n  \"name\": \"获取人员审核列表\",\n  \"version\": \"1.0\",\n  \"providerConfig\": {\"protocol\": \"HTTP\", \"address\": \"/userPro/planList\", \"httpMethod\": \"POST\"},\n  \"publishConfig\": {\"publishAddress\": \"/userPro/planList\"},\n  \"publishParams\": [{\"paramName\": \"userId\", \"paramType\": \"string\", \"paramPosition\": \"query\", \"required\": \"1\"}]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"2361887157562377213\",\n  \"message\": \"保存成功\"\n}",
  "description": "保存服务编排服务（POST /dws/flowserve/gateway/save）。写入 api_info_t 服务主表和 api_param_t 参数表；如请求包含 apiFileId/fileContent，则同步 api_file_t.api_id 关联服务主表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.保存网关服务
// 路由：POST /dws/flowserve/gateway/save
// 业务说明：保存服务编排服务（POST /dws/flowserve/gateway/save）。写入 api_info_t 服务主表和 api_param_t 参数表；如请求包含 apiFileId/fileContent，则同步 api_file_t.api_id 关联服务主表。
/*
 * 保存服务登记信息。
 * api_info_t 是服务主表，api_param_t 保存登记参数，
 * api_file_t.api_id 只在存在脚本文件时建立关联。
 */
import magicResourceLink
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import tenantRuntime

var blank = value => value == null || (value + '').trim() == ''
var asJson = value => value == null ? null : JSONUtil.toJsonStr(value)
var now = new Date()
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再保存服务'
}

if (body == null) {
    exit 400, '请求参数不能为空'
}

var apiId = body?.id || body?.apiId || body?.tid
var serviceName = body?.serviceName || body?.name
if (blank(serviceName)) {
    exit 400, '服务名称不能为空'
}
if (blank(apiId)) {
    apiId = IdUtil.getSnowflakeNextIdStr()
}

var provider = body?.providerConfig == null ? {} : body.providerConfig
var publish = body?.publishConfig == null ? {} : body.publishConfig
var ext = body?.extendInfo == null ? {} : body.extendInfo
var params = body?.publishParams || body?.params || body?.paramList || []
var method = body?.method || body?.httpMethod || provider.httpMethod || publish.publishMethod || 'GET'
method = (method + '').toUpperCase()
var uri = body?.uri || body?.address || provider.address || publish.publishAddress || body?.publishAddress || ''
var publishAddress = body?.publishAddress || publish.publishAddress || uri
var groupId = body?.groupId || ext.groupId || 'api_service_group_default'
var old = db.table('api_info_t').where().eq('tid', apiId).eq('is_del', 0).selectOne()

var row = {
    tid: apiId,
    resource_id: body?.resourceId || body?.resource_id || apiId,
    service_name: serviceName,
    service_code: body?.serviceCode || body?.code || body?.service_code || apiId,
    service_type: body?.serviceType || body?.type || '数据服务',
    service_desc: body?.serviceDesc || body?.description || ext.apiDescription || '',
    uri: uri,
    method: method,
    service_sql: body?.serviceSql || body?.sql || '',
    is_use: body?.isUse == null ? 1 : body.isUse,
    group_id: groupId,
    version: body?.version || '1.0',
    status: body?.status || 'draft',
    protocol: body?.protocol || provider.protocol || 'HTTP',
    address: provider.address || uri,
    publish_address: publishAddress,
    publish_address_full: publishAddress,
    provider_config: asJson(provider),
    publish_config: asJson(publish),
    labels: body?.labels || '',
    return_type: ext.returnType || body?.returnType || '',
    share_type: ext.shareType || body?.shareType || '',
    data_domain: ext.dataDomain || '',
    data_category: ext.dataCategory || '',
    data_level: ext.dataLevel || '',
    catalog_id: ext.catalogId || body?.catalogId || '',
    org_id: ext.orgId || body?.orgId || '',
    source_business_name: ext.sourceBusinessName || '',
    source_business_item: ext.sourceBusinessItem || '',
    request_example: ext.requestExample || '',
    response_example: ext.responseExample || '',
    service_create_type: body?.serviceCreateType || body?.service_create_type || 'api',
    flow_id: body?.flowId || body?.flow_id || old?.flow_id || old?.flowId || '',
    tenant_id: tenantId,
    updated_time: now,
    updated_by: body?.updatedBy || body?.createdBy || ''
}

if (old == null) {
    row.created_time = now
    row.created_by = body?.createdBy || ''
    row.is_del = 0
    db.table('api_info_t').primary('tid').insert(row)
} else {
    db.table('api_info_t').primary('tid').update(row, true)
}

// 参数表采用先软删后重建，保证编辑时删除的参数能准确落库。
// 扩展属性统一写入 da_prop_t；api_info_t 只保存稳定的物理字段。
for (key in ext.keySet()) {
    var value = ext[key]
    var propValue = value == null ? null : ((value instanceof java.util.Map || value instanceof java.util.List) ? asJson(value) : value + '')
    var existingProp = db.table('da_prop_t').where().eq('parent_id', apiId).eq('data_type', 'api').eq('prop_name', key).eq('is_del', 0).selectOne()
    var prop = { parent_id: apiId, tenant_id: tenantId, prop_name: key, prop_value: propValue, prop_type: '0', data_type: 'api', is_del: 0, updated_time: now }
    if (existingProp == null) {
        prop.tid = IdUtil.getSnowflakeNextIdStr()
        prop.created_time = now
        db.table('da_prop_t').primary('tid').insert(prop)
    } else {
        prop.tid = existingProp.tid
        db.table('da_prop_t').primary('tid').update(prop, true)
    }
}

db.table('api_param_t').where().eq('api_id', apiId).eq('is_del', 0).update({
    is_del: 1,
    updated_time: now
})
for (index, item in params) {
    db.table('api_param_t').primary('tid').insert({
        tid: IdUtil.getSnowflakeNextIdStr(),
        api_id: apiId,
        param_name: item.paramName || item.param_name || '',
        param_desc: item.description || item.paramDesc || item.param_desc || '',
        param_type: item.paramType || item.param_type || '',
        param_position: item.paramPosition || item.param_position || '',
        required_flag: item.required || item.requiredFlag || '',
        default_value: item.defaultValue || item.default_value || '',
        showcase_value: item.showcaseValue || item.showcase_value || '',
        param_scope: item.res == 1 ? 'response' : 'request',
        sort_no: index + 1,
        created_time: now,
        tenant_id: tenantId,
    updated_time: now,
        is_del: 0
    })
}

// 若调用方传入脚本文件，则建立 api_file_t.api_id 关联；普通登记服务不会强行创建脚本。
if (!blank(body?.apiFileId)) {
    magicResourceLink.link(body.apiFileId + '', apiId + '')
}

return {
    id: apiId,
    tid: apiId,
    message: '保存成功'
}
