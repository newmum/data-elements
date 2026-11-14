{
  "properties": {},
  "id": "510fe82cba4a42248abd1faefc515441",
  "script": null,
  "groupId": "45b082c7d94f4ff08e3b9bf367521723",
  "name": "01.服务列表",
  "createTime": 1782955363383,
  "updateTime": 1787153757564,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"keyword\": \"人员\",\n  \"groupId\": \"api_service_group_default\",\n  \"method\": \"POST\",\n  \"status\": \"published\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0,\n  \"stats\": {\"total\": 0, \"draft\": 0, \"published\": 0, \"offline\": 0}\n}",
  "description": "分页查询当前租户角色可见的服务及状态统计。支持关键字、分组、方法、状态、创建类型和白名单排序，指定部门时按当前会话收窄范围。按 datasourceId 查询时先检查数据源访问权限。无部门或数据范围时返回 403，标识或创建类型无效时返回 400，父数据源不可访问时返回 404。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import "@post:/sym/user/me" as me
import dataScope
import java.util.Locale
import magicResourceLink
import tableRead
import tableSort
import tenantRuntime

/**
 * POST /dws/flowserve/gateway/api/list 分页读取服务编排和代理数据源登记的服务列表。
 * 支持 pageNum、pageSize、keyword、groupId、method、status、serviceCreateType 和白名单排序。
 * 按可信租户与角色的数据范围返回服务、参数数、关联脚本数及当前筛选条件下的状态统计。
 * departmentOnly 按会话部门收窄范围，datasourceId 还须通过数据源登记权限检查。接口只读。
 */
var blank = value => value == null || (value + '').trim() == ''
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int(1)
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int(20)
pageNum = Math.max(1, pageNum)
pageSize = Math.min(100, pageSize < 1 ? 20 : pageSize)

var services = tableRead.from('api_info_t', 'service', tenantId)
var field = (name, kind) => tableRead.column('service', name, kind)
var text = name => field(name, 'TEXT')
var number = name => field(name, 'NUMBER')
var filters = [tableRead.equal(number('is_del'), 0)]

// 本部门视图使用会话部门，与角色范围取交集，请求中的组织标识不参与授权。
if (body?.departmentOnly == true) {
    var orgId = me()?.orgId
    if (blank(orgId)) {
        exit 403, '当前账号未关联部门，无法查看本部门服务'
    }
    filters.add(tableRead.equal(text('org_id'), orgId))
}
if (scope.scope == 'OWNER') {
    filters.add(tableRead.in(text('created_by'), blank(scope.userId) ? [] : [scope.userId]))
} else if (scope.scope == 'ORG') {
    filters.add(tableRead.in(text('org_id'), scope.organizationIds || []))
} else if (scope.scope != 'ALL') {
    exit 403, '数据范围无效'
}

// 指定父数据源时校验登记菜单的范围，服务行仍受自身的角色条件限制。
var datasourceId = body?.datasourceId == null ? '' : (body.datasourceId + '').trim()
if (datasourceId.length() > 32) {
    exit 400, '数据源标识格式不正确'
}
if (body?.containsKey('datasourceId') == true && blank(datasourceId)) {
    exit 400, '请选择已保存的数据源'
}
if (!blank(datasourceId)) {
    var source = db.table('db_datasource_t').columns('tid', 'tenant_id', 'org_id', 'created_by').where()
        .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (source == null || !dataScope.canReadDataSourceFor('MENU:2081000000000000002', source)) {
        exit 404, '数据源不存在或不在当前数据范围内'
    }
    filters.add(tableRead.equal(text('datasource_id'), datasourceId))
}

var defaultGroup = 'api_service_group_default'
var storedGroup = text('group_id')
var group = tableRead.coalesce([storedGroup, defaultGroup])
var creationType = tableRead.coalesce([text('service_create_type'), 'api'])
var method = tableRead.coalesce([text('method'), 'GET'])
// 历史服务没有 status 时仍按 is_use 判断发布状态，列表和统计共用同一条件。
var status = tableRead.coalesce([text('status'), tableRead.when(
    tableRead.equal(tableRead.coalesce([number('is_use'), 1]), 1), 'published', 'offline')])

if (!blank(body?.keyword)) {
    var pattern = '%' + body.keyword + '%'
    filters.add(tableRead.any([
        tableRead.like(text('service_name'), pattern),
        tableRead.like(text('service_code'), pattern),
        tableRead.like(text('uri'), pattern),
        tableRead.like(text('publish_address'), pattern)
    ]))
}
if (!blank(body?.groupId)) {
    var requestedGroup = body.groupId + ''
    var groupMatch = tableRead.equal(group, requestedGroup)
    if (requestedGroup == defaultGroup) {
        // 默认分组也包含历史空字符串。追加分隔符可区分空字符串与仅有空格的旧标识。
        var emptyGroup = tableRead.equal(tableRead.concat(storedGroup, '|'), '|')
        groupMatch = tableRead.any([groupMatch, emptyGroup])
    }
    filters.add(groupMatch)
}
if (!blank(body?.method)) {
    filters.add(tableRead.equal(tableRead.lower(method), (body.method + '').toLowerCase(Locale.ROOT)))
}
if (!blank(body?.status)) {
    filters.add(tableRead.equal(status, body.status + ''))
}
if (!blank(body?.serviceCreateType)) {
    if (body.serviceCreateType != 'api' && body.serviceCreateType != 'apiBuild') {
        exit 400, '服务创建类型无效'
    }
    filters.add(tableRead.equal(creationType, body.serviceCreateType))
}
var eligible = tableRead.all(filters)
services.where(eligible)

var sortKey = tableSort.field(body, {
    serviceName: 'serviceName',
    name: 'serviceName',
    createdTime: 'createdTime',
    updatedTime: 'updatedTime',
    requestCount: 'requestCount',
    successPercent: 'successPercent',
    paramCount: 'paramCount'
}, 'updatedTime')
var direction = tableSort.direction(body)

var stats = services.grouped({
    total: tableRead.count(),
    draft: tableRead.countIf(tableRead.equal(status, 'draft')),
    published: tableRead.countIf(tableRead.equal(status, 'published')),
    offline: tableRead.countIf(tableRead.equal(status, 'offline'))
}, [])[0]
// 保留旧接口空集合的统计桶及请求页码，越界时无需再读取分页结果。
if (stats.total == 0) {
    stats = {total: 0, draft: null, published: null, offline: null}
}
if ((pageNum - 1L) * pageSize >= stats.total) {
    return {list: [], total: stats.total, pageNum: pageNum, pageSize: pageSize, stats: stats}
}

var serviceId = text('tid')
var fields = {
    id: serviceId,
    serviceName: text('service_name'),
    serviceCode: text('service_code'),
    resourceId: text('resource_id'),
    datasourceId: text('datasource_id'),
    groupId: group,
    serviceType: text('service_type'),
    serviceCreateType: creationType,
    status: status,
    protocol: text('protocol'),
    uri: text('uri'),
    upstreamAddress: tableRead.coalesce([text('address'), text('uri')]),
    method: method,
    publishAddress: tableRead.coalesce([text('publish_address'), text('uri')]),
    publishAddressFull: tableRead.coalesce([text('publish_address_full'), text('publish_address'), text('uri')]),
    serviceDesc: field('service_desc', 'LOB'),
    version: text('version'),
    labels: text('labels'),
    catalogId: text('catalog_id'),
    orgId: text('org_id'),
    providerConfig: field('provider_config', 'LOB'),
    publishConfig: field('publish_config', 'LOB'),
    flowId: text('flow_id'),
    requestCount: tableRead.coalesce([number('call_count'), 0]),
    successPercent: tableRead.coalesce([number('success_percent'), 0]),
    createdTime: field('created_time', 'TIME'),
    updatedTime: field('updated_time', 'TIME')
}

// 通过已授权服务关联有效参数，并以服务主键聚合，保留数据库原有标识比较规则。
var params = tableRead.from('api_param_t', 'param', tenantId)
params.leftJoin('api_info_t', 'service', 'tid', 'api_id', [])
params.where(tableRead.equal(tableRead.column('param', 'is_del', 'NUMBER'), 0))
params.where(eligible)
var counts = {service_id: serviceId, total: tableRead.count()}

// 仅按参数数排序时需要先聚合整个可见结果；其他排序只补齐本页服务的计数。
if (sortKey == 'paramCount') {
    services.leftJoinGroupedProjection(params, 'param_count', 'service_id', 'tid', counts, [serviceId])
    fields.paramCount = tableRead.coalesce([tableRead.column('param_count', 'total', 'NUMBER'), 0])
}
var page = services.page(fields, fields[sortKey], direction, 'tid', pageNum, pageSize)
var rows = page.rows
if (rows.isEmpty()) {
    return {list: [], total: stats.total, pageNum: pageNum, pageSize: pageSize, stats: stats}
}

var paramsByService = {}
if (sortKey != 'paramCount') {
    params.where(tableRead.in(serviceId, rows.map(row => row.id)))
    for (entry in params.grouped(counts, [serviceId])) {
        paramsByService[entry.service_id] = entry.total
    }
}

// 只为当前页批量读取分组名称和控制库脚本数，保留分组名称匹配规则。
var groupIds = rows.map(row => row.groupId || defaultGroup).distinct()
var groups = tableRead.from('sym_dict_t', 'dict_row', tenantId)
var groupId = tableRead.column('dict_row', 'tid', 'TEXT')
groups.where(tableRead.equal(tableRead.column('dict_row', 'is_del', 'NUMBER'), 0))
groups.where(tableRead.equal(tableRead.column('dict_row', 'biz_type', 'TEXT'), 'apiServiceGroup'))
groups.where(tableRead.in(groupId, groupIds))
var groupNames = {}
for (entry in groups.grouped({
    id: groupId,
    name: tableRead.column('dict_row', 'dict_name', 'TEXT')
}, [])) {
    groupNames[entry.id] = entry.name
}
rows = magicResourceLink.attachApiFileCounts(rows)

var list = []
for (row in rows) {
    var gid = row.groupId || defaultGroup
    list.add({
        id: row.id,
        tid: row.id,
        serviceName: row.serviceName || '',
        name: row.serviceName || '',
        serviceCode: row.serviceCode || '',
        code: row.serviceCode || '',
        datasourceId: row.datasourceId || '',
        resourceId: row.resourceId || null,
        groupId: gid,
        groupName: groupNames[gid] || '默认分组',
        serviceType: row.serviceType || null,
        serviceCreateType: row.serviceCreateType || 'api',
        status: row.status || 'published',
        protocol: row.protocol || 'HTTP',
        uri: row.uri || '',
        upstreamAddress: row.upstreamAddress || row.uri || '',
        method: (row.method || 'GET').toUpperCase(Locale.ROOT),
        publishAddress: row.publishAddress || '',
        publishAddressFull: row.publishAddressFull || '',
        serviceDesc: row.serviceDesc || null,
        version: row.version,
        labels: row.labels,
        catalogId: row.catalogId || null,
        orgId: row.orgId || null,
        providerConfig: row.providerConfig || null,
        publishConfig: row.publishConfig || null,
        extendInfo: {},
        flowId: row.flowId || null,
        requestCount: row.requestCount || 0,
        successPercent: row.successPercent || 0,
        paramCount: row.paramCount || paramsByService[row.id] || 0,
        apiFileCount: row.apiFileCount || 0,
        createdTime: row.createdTime,
        updatedTime: row.updatedTime
    })
}

return {list: list, total: stats.total, pageNum: pageNum, pageSize: pageSize, stats: stats}
