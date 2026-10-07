{
  "properties": {},
  "id": "1cc3266227a24804b9ba7c3a9e00d724",
  "script": null,
  "groupId": "45b082c7d94f4ff08e3b9bf367521723",
  "name": "02.服务详情",
  "createTime": 1785025400000,
  "updateTime": 1787153758165,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"apiId\": \"2436540554061808637\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"2436540554061808637\",\n  \"name\": \"数据组织\",\n  \"publishParams\": []\n}",
  "description": "服务详情（POST /dws/flowserve/gateway/api/detail）。只读 api_info_t 服务主表、api_param_t 参数表、api_file_t 脚本关联和 sym_dict_t 分组字典；用于服务编排管理详情页按 api_id 回填登记/构建表单。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.服务详情
// 路由：POST /dws/flowserve/gateway/api/detail
// 业务说明：服务详情（POST /dws/flowserve/gateway/api/detail）。只读 api_info_t 服务主表、api_param_t 参数表、api_file_t 脚本关联和 sym_dict_t 分组字典；用于服务编排管理详情页按 api_id 回填登记/构建表单。
/*
 * 服务详情接口。
 * 以 api_info_t 为主表读取服务登记信息，api_param_t 回填请求参数，
 * api_file_t 仅统计关联脚本数量，接口不写入业务数据。
 */
import magicResourceLink
import cn.hutool.json.JSONUtil

var blank = value => value == null || (value + '').trim() == ''
var sqlValue = value => "'" + ((value + '').replace("'", "''")) + "'"
var asObj = value => {
    if (blank(value)) {
        return {}
    }
    try {
        return JSONUtil.parseObj(value)
    } catch (e) {
        return {}
    }
}

var apiId = body?.apiId || body?.api_id || body?.id || body?.tid || parameter?.apiId || parameter?.id
if (blank(apiId)) {
    exit 400, 'apiId不能为空'
}

// 基础信息来自服务主表，兼容早期迁移数据中 method/status 为空的情况。
var rows = db.select("""
select
  tid as id,
  tid,
  service_name as serviceName,
  service_code as serviceCode,
  resource_id as resourceId,
  coalesce(group_id, 'api_service_group_default') as groupId,
  service_type as serviceType,
  coalesce(service_create_type, 'api') as serviceCreateType,
  coalesce(status, case when coalesce(is_use, 1) = 1 then 'published' else 'offline' end) as status,
  coalesce(protocol, 'HTTP') as protocol,
  coalesce(uri, '') as uri,
  upper(coalesce(method, 'GET')) as method,
  coalesce(address, uri, '') as address,
  coalesce(publish_address, uri, '') as publishAddress,
  coalesce(publish_address_full, publish_address, uri, '') as publishAddressFull,
  service_desc as serviceDesc,
  version,
  labels,
  catalog_id as catalogId,
  org_id as orgId,
  provider_config as providerConfig,
  publish_config as publishConfig,
  flow_id as flowId,
  return_type as returnType,
  share_type as shareType,
  data_domain as dataDomain,
  data_category as dataCategory,
  data_level as dataLevel,
  source_business_name as sourceBusinessName,
  source_business_item as sourceBusinessItem,
  request_example as requestExample,
  response_example as responseExample,
  coalesce(call_count, 0) as requestCount,
  coalesce(success_percent, 0) as successPercent,
  service_sql as serviceSql,
  created_time as createdTime,
  updated_time as updatedTime
from api_info_t
where coalesce(is_del, 0) = 0
  and tid = """ + sqlValue(apiId) + """
limit 1
""")
if (rows == null || rows.size() == 0) {
    exit 404, '服务不存在或已删除'
}
var row = rows[0]

// 参数表按 sort_no 回填为登记表单使用的 publishParams 结构。
var paramRows = db.select("""
select
  tid,
  param_name as paramName,
  param_desc as description,
  param_type as paramType,
  param_position as paramPosition,
  required_flag as required,
  default_value as defaultValue,
  showcase_value as showcaseValue,
  param_scope as paramScope,
  sort_no as sortNo
from api_param_t
where coalesce(is_del, 0) = 0
  and api_id = """ + sqlValue(apiId) + """
order by coalesce(sort_no, 9999), created_time
""")
var publishParams = []
for (p in paramRows) {
    publishParams.add({
        id: p.tid,
        paramName: p.paramName || p.paramname || '',
        description: p.description || '',
        paramType: p.paramType || p.paramtype || '',
        paramPosition: p.paramPosition || p.paramposition || '',
        required: p.required || '',
        defaultValue: p.defaultValue || p.defaultvalue || '',
        showcaseValue: p.showcaseValue || p.showcasevalue || '',
        paramScope: p.paramScope || p.paramscope || 'request',
        sortNo: p.sortNo || p.sortno
    })
}

var provider = asObj(row.providerConfig || row.providerconfig || row.provider_config)
var publish = asObj(row.publishConfig || row.publishconfig || row.publish_config)
var ext = {}
var extRows = db.table('da_prop_t').where().eq('parent_id', apiId).eq('data_type', 'api').eq('is_del', 0).select()
for (prop in extRows) {
    var propName = prop.propName || prop.prop_name
    if (!blank(propName)) {
        ext[propName] = prop.propValue == null ? prop.prop_value : prop.propValue
    }
}

// 老数据可能只写了物理字段，没有写 JSON 配置；这里补齐表单需要的配置键。
provider.protocol = provider.protocol || row.protocol || 'HTTP'
provider.address = provider.address || row.address || row.uri || ''
provider.httpMethod = provider.httpMethod || provider.httpmethod || row.method || 'GET'
publish.publishAddress = publish.publishAddress || publish.publishaddress || row.publishAddress || row.publishaddress || row.uri || ''
publish.publishMethod = publish.publishMethod || publish.publishmethod || row.method || 'GET'
ext.catalogId = ext.catalogId || ext.catalogid || row.catalogId || row.catalogid || ''
ext.orgId = ext.orgId || ext.orgid || row.orgId || row.orgid || ''
ext.dataDomain = ext.dataDomain || ext.datadomain || row.dataDomain || row.datadomain || ''
ext.dataCategory = ext.dataCategory || ext.datacategory || row.dataCategory || row.datacategory || ''
ext.dataLevel = ext.dataLevel || ext.datalevel || row.dataLevel || row.datalevel || ''
ext.sourceBusinessName = ext.sourceBusinessName || row.sourceBusinessName || row.sourcebusinessname || ''
ext.sourceBusinessItem = ext.sourceBusinessItem || row.sourceBusinessItem || row.sourcebusinessitem || ''
ext.returnType = ext.returnType || row.returnType || row.returntype || ''
ext.shareType = ext.shareType || row.shareType || row.sharetype || ''
ext.requestExample = ext.requestExample || row.requestExample || row.requestexample || ''
ext.responseExample = ext.responseExample || row.responseExample || row.responseexample || ''

var groupRows = db.select("""
select dict_name as name
from sym_dict_t
where coalesce(is_del, 0) = 0
  and biz_type = 'apiServiceGroup'
  and tid = """ + sqlValue(row.groupId || row.groupid || 'api_service_group_default') + """
limit 1
""")
var apiFileCount = magicResourceLink.countByApiId(apiId + '')

return {
    id: row.id || row.tid,
    tid: row.tid || row.id,
    name: row.serviceName || row.servicename || '',
    serviceName: row.serviceName || row.servicename || '',
    serviceCode: row.serviceCode || row.servicecode || '',
    code: row.serviceCode || row.servicecode || '',
    resourceId: row.resourceId || row.resourceid,
    groupId: row.groupId || row.groupid || 'api_service_group_default',
    groupName: groupRows == null || groupRows.size() == 0 ? '默认分组' : groupRows[0].name,
    serviceType: row.serviceType || row.servicetype || '数据服务',
    serviceCreateType: row.serviceCreateType || row.servicecreatetype || 'api',
    status: row.status || 'draft',
    protocol: row.protocol || 'HTTP',
    uri: row.uri || '',
    method: row.method || 'GET',
    address: row.address || '',
    publishAddress: row.publishAddress || row.publishaddress || '',
    publishAddressFull: row.publishAddressFull || row.publishaddressfull || '',
    description: row.serviceDesc || row.servicedesc || '',
    serviceDesc: row.serviceDesc || row.servicedesc || '',
    version: row.version || '1.0',
    labels: row.labels || '',
    providerConfig: provider,
    publishConfig: publish,
    extendInfo: ext,
    flowId: row.flowId || row.flowid || row.flow_id || provider.flowId || provider.flowid || '',
    publishParams: publishParams,
    requestExample: ext.requestExample || '',
    responseExample: ext.responseExample || '',
    requestCount: row.requestCount || row.requestcount || 0,
    successPercent: row.successPercent || row.successpercent || 0,
    apiFileCount: apiFileCount,
    createdTime: row.createdTime || row.createdtime,
    updatedTime: row.updatedTime || row.updatedtime
}
