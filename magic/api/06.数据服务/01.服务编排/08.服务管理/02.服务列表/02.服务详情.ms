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
  "description": "按 apiId 读取服务登记详情、分组、参数、扩展配置及关联脚本数量。仅查询当前租户并按角色限定服务范围。传入 datasourceId 时同时校验登记菜单的来源数据源权限及服务归属。标识缺失返回 400，服务或来源不可见返回 404。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.json.JSONUtil
import dataScope
import java.util.Locale
import magicResourceLink
import tableRead
import tenantRuntime

/**
 * POST /dws/flowserve/gateway/api/detail 按 apiId 读取服务登记详情，回填分组、配置和全部参数。
 * 当前登录租户内按 ALL、ORG、OWNER 范围查询。代理登记传入 datasourceId 时，还需通过来源菜单授权。
 * 只读接口，控制库仅提供已授权服务的关联脚本数量。
 */

var blank = value => value == null || (value + '').trim() == ''
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

var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
if (!['ALL', 'ORG', 'OWNER'].contains(scope?.scope)) {
    exit 403, '数据范围无效'
}

var apiId = body?.apiId || body?.api_id || body?.id || body?.tid || parameter?.apiId || parameter?.id
if (blank(apiId)) {
    exit 400, 'apiId不能为空'
}
apiId = apiId + ''

var datasourceId = body?.datasourceId == null ? '' : (body.datasourceId + '').trim()
if (datasourceId.length() > 32) {
    exit 400, '数据源标识格式不正确'
}
if (!blank(datasourceId)) {
    // 来源菜单的权限和服务行的角色范围分别校验，两项都通过才能读取代理登记详情。
    var source = db.table('db_datasource_t').columns('tid', 'tenant_id', 'org_id', 'created_by').where()
        .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (source == null || !dataScope.canReadDataSourceFor('MENU:2081000000000000002', source)) {
        exit 404, '数据源不存在或不在当前数据范围内'
    }
}

var textColumn = (alias, column) => tableRead.column(alias, column, 'TEXT')
var numberColumn = (alias, column) => tableRead.column(alias, column, 'NUMBER')
var longTextColumn = (alias, column) => tableRead.column(alias, column, 'LOB')
var timeColumn = (alias, column) => tableRead.column(alias, column, 'TIME')
var active = alias => tableRead.equal(tableRead.coalesce([numberColumn(alias, 'is_del'), 0]), 0)

// 服务行通过可信租户和角色范围后，再读取参数、扩展配置和关联资源。
var services = tableRead.from('api_info_t', 'a', tenantId)
var serviceId = textColumn('a', 'tid')
services.where(active('a'))
services.where(tableRead.equal(serviceId, apiId))
if (scope.scope == 'OWNER') {
    if (blank(scope.userId)) { exit 404, '服务不存在或已删除' }
    services.where(tableRead.equal(textColumn('a', 'created_by'), scope.userId))
} else if (scope.scope == 'ORG') {
    services.where(tableRead.in(textColumn('a', 'org_id'), scope.organizationIds || []))
}
if (!blank(datasourceId)) {
    services.where(tableRead.equal(textColumn('a', 'datasource_id'), datasourceId))
}
var rows = services.rows({
    tid: serviceId,
    serviceName: textColumn('a', 'service_name'),
    serviceCode: textColumn('a', 'service_code'),
    resourceId: textColumn('a', 'resource_id'),
    datasourceId: textColumn('a', 'datasource_id'),
    groupId: textColumn('a', 'group_id'),
    serviceType: textColumn('a', 'service_type'),
    serviceCreateType: textColumn('a', 'service_create_type'),
    status: textColumn('a', 'status'),
    isUse: numberColumn('a', 'is_use'),
    protocol: textColumn('a', 'protocol'),
    uri: textColumn('a', 'uri'),
    method: textColumn('a', 'method'),
    address: textColumn('a', 'address'),
    publishAddress: textColumn('a', 'publish_address'),
    publishAddressFull: textColumn('a', 'publish_address_full'),
    serviceDesc: longTextColumn('a', 'service_desc'),
    version: textColumn('a', 'version'),
    labels: textColumn('a', 'labels'),
    catalogId: textColumn('a', 'catalog_id'),
    orgId: textColumn('a', 'org_id'),
    providerConfig: longTextColumn('a', 'provider_config'),
    publishConfig: longTextColumn('a', 'publish_config'),
    flowId: textColumn('a', 'flow_id'),
    returnType: textColumn('a', 'return_type'),
    shareType: textColumn('a', 'share_type'),
    dataDomain: textColumn('a', 'data_domain'),
    dataCategory: textColumn('a', 'data_category'),
    dataLevel: textColumn('a', 'data_level'),
    sourceBusinessName: textColumn('a', 'source_business_name'),
    sourceBusinessItem: textColumn('a', 'source_business_item'),
    requestExample: longTextColumn('a', 'request_example'),
    responseExample: longTextColumn('a', 'response_example'),
    requestCount: numberColumn('a', 'call_count'),
    successPercent: numberColumn('a', 'success_percent'),
    createdTime: timeColumn('a', 'created_time'),
    updatedTime: timeColumn('a', 'updated_time')
}, serviceId, 'asc', 1)
if (rows.isEmpty()) {
    exit 404, '服务不存在或已删除'
}
var row = rows[0]

// 一次读取本服务的完整参数，按历史默认序号稳定排序，同序号保留数据库的创建时间顺序。
var paramRows = db.camel().table('api_param_t').columns(
    'tid', 'param_name', 'param_desc', 'param_type', 'param_position',
    'required_flag', 'default_value', 'showcase_value', 'param_scope', 'sort_no'
).where().eq('tenant_id', tenantId).eq('api_id', apiId)
    .and(filter => filter.eq('is_del', 0).or().isNull('is_del'))
    .orderBy('created_time', 'asc').orderBy('tid', 'asc').select()
paramRows = paramRows.sort((left, right) => {
    var leftSort = left.sortNo == null ? 9999 : left.sortNo
    var rightSort = right.sortNo == null ? 9999 : right.sortNo
    return leftSort == rightSort ? 0 : leftSort < rightSort ? -1 : 1
})
var publishParams = []
for (p in paramRows) {
    publishParams.add({
        id: p.tid,
        paramName: p.paramName || '',
        description: p.paramDesc || '',
        paramType: p.paramType || '',
        paramPosition: p.paramPosition || '',
        required: p.requiredFlag || '',
        defaultValue: p.defaultValue || '',
        showcaseValue: p.showcaseValue || '',
        paramScope: p.paramScope || 'request',
        sortNo: p.sortNo
    })
}

var provider = asObj(row.providerConfig)
var publish = asObj(row.publishConfig)
var ext = {}
var extRows = db.camel().table('da_prop_t').columns('prop_name', 'prop_value').where()
    .eq('tenant_id', tenantId).eq('parent_id', apiId).eq('data_type', 'api').eq('is_del', 0).select()
for (prop in extRows) {
    if (!blank(prop.propName)) {
        ext[prop.propName] = prop.propValue
    }
}

// 主表兼容旧状态和地址，显式空地址仍保留在主响应中。仅在表单配置缺项时补齐显示值。
var uri = row.uri || ''
var address = row.address == null ? uri : row.address
var publishAddress = row.publishAddress == null ? uri : row.publishAddress
var publishAddressFull = row.publishAddressFull == null ? publishAddress : row.publishAddressFull
var method = (row.method || 'GET').toUpperCase(Locale.ROOT)
var protocol = row.protocol || 'HTTP'
var status = row.status
if (status == null) {
    status = row.isUse == null || row.isUse == 1 ? 'published' : 'offline'
}
provider.protocol = provider.protocol || protocol
provider.address = provider.address || address || uri
provider.httpMethod = provider.httpMethod || provider.httpmethod || method
publish.publishAddress = publish.publishAddress || publish.publishaddress || publishAddress || uri
publish.publishMethod = publish.publishMethod || publish.publishmethod || method
ext.catalogId = ext.catalogId || ext.catalogid || row.catalogId || ''
ext.orgId = ext.orgId || ext.orgid || row.orgId || ''
ext.dataDomain = ext.dataDomain || ext.datadomain || row.dataDomain || ''
ext.dataCategory = ext.dataCategory || ext.datacategory || row.dataCategory || ''
ext.dataLevel = ext.dataLevel || ext.datalevel || row.dataLevel || ''
ext.sourceBusinessName = ext.sourceBusinessName || row.sourceBusinessName || ''
ext.sourceBusinessItem = ext.sourceBusinessItem || row.sourceBusinessItem || ''
ext.returnType = ext.returnType || row.returnType || ''
ext.shareType = ext.shareType || row.shareType || ''
ext.requestExample = ext.requestExample || row.requestExample || ''
ext.responseExample = ext.responseExample || row.responseExample || ''

// 分组只从当前租户的服务字典取值，控制库脚本计数只使用已通过权限校验的服务 ID。
var groupId = row.groupId || 'api_service_group_default'
var groups = tableRead.from('sym_dict_t', 'g', tenantId)
var dictionaryId = textColumn('g', 'tid')
groups.where(active('g'))
groups.where(tableRead.equal(textColumn('g', 'biz_type'), 'apiServiceGroup'))
groups.where(tableRead.equal(dictionaryId, groupId))
var groupRows = groups.rows({name: textColumn('g', 'dict_name')}, dictionaryId, 'asc', 1)
var apiFileCount = magicResourceLink.countByApiId(apiId)

return {
    id: row.tid,
    tid: row.tid,
    name: row.serviceName || '',
    serviceName: row.serviceName || '',
    serviceCode: row.serviceCode || '',
    code: row.serviceCode || '',
    datasourceId: row.datasourceId || '',
    resourceId: row.resourceId,
    groupId: groupId,
    groupName: groupRows.isEmpty() ? '默认分组' : groupRows[0].name,
    serviceType: row.serviceType || '数据服务',
    serviceCreateType: row.serviceCreateType || 'api',
    status: status || 'draft',
    protocol: protocol,
    uri: uri,
    method: method,
    address: address,
    publishAddress: publishAddress,
    publishAddressFull: publishAddressFull,
    description: row.serviceDesc || '',
    serviceDesc: row.serviceDesc || '',
    version: row.version || '1.0',
    labels: row.labels || '',
    providerConfig: provider,
    publishConfig: publish,
    extendInfo: ext,
    flowId: row.flowId || provider.flowId || provider.flowid || '',
    publishParams: publishParams,
    requestExample: ext.requestExample || '',
    responseExample: ext.responseExample || '',
    requestCount: row.requestCount || 0,
    successPercent: row.successPercent || 0,
    apiFileCount: apiFileCount,
    createdTime: row.createdTime,
    updatedTime: row.updatedTime
}
