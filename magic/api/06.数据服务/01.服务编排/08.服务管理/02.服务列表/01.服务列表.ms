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
  "description": "服务编排管理列表（POST /dws/flowserve/gateway/api/list）。读取 api_info_t 主表、api_param_t 参数表、api_file_t 脚本关联和 sym_dict_t 服务分组字典；按分组、方法、状态、关键字分页查询，是服务编排管理页面主接口；使用请求参数 pageNum、pageSize、keyword、groupId、method、status。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.服务列表
// 路由：POST /dws/flowserve/gateway/api/list
// 业务说明：服务编排管理列表（POST /dws/flowserve/gateway/api/list）。读取 api_info_t 主表、api_param_t 参数表、api_file_t 脚本关联和 sym_dict_t 服务分组字典；按分组、方法、状态、关键字分页查询，是服务编排管理页面主接口；使用请求参数 pageNum、pageSize、keyword、groupId、method、status。
import magicResourceLink

/*
 * 服务编排管理列表。
 * 读取 api_info_t 作为服务主表，api_param_t/api_file_t 只聚合当前页服务，
 * sym_dict_t 中 biz_type=apiServiceGroup 的字典项作为左侧分组名称。
 */
var blank = value => value == null || (value + '').trim() == ''
var sqlValue = value => "'" + ((value + '').replace("'", "''")) + "'"
var likeValue = value => "'%" + ((value + '').replace("'", "''")) + "%'"
var pageNum = body?.pageNum == null ? 1 : body.pageNum::int(1)
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int(20)
if (pageNum < 1) { pageNum = 1 }
if (pageSize < 1) { pageSize = 20 }
if (pageSize > 200) { pageSize = 200 }

var statusExpr = "coalesce(a.status, case when coalesce(a.is_use, 1) = 1 then 'published' else 'offline' end)"
var whereSql = " where a.is_del = 0 "

// 关键字同时命中服务名称、服务编码、服务地址，避免前端多个输入框造成理解成本。
if (!blank(body?.keyword)) {
    var keyword = body.keyword
    whereSql += " and (a.service_name like " + likeValue(keyword) +
        " or a.service_code like " + likeValue(keyword) +
        " or a.uri like " + likeValue(keyword) +
        " or a.publish_address like " + likeValue(keyword) + ") "
}

// 左侧分组树点击后只过滤对应服务分组；未分组服务归入默认分组。
if (!blank(body?.groupId)) {
    whereSql += " and coalesce(a.group_id, 'api_service_group_default') = " + sqlValue(body.groupId) + " "
}
if (!blank(body?.method)) {
    whereSql += " and upper(coalesce(a.method, 'GET')) = " + sqlValue((body.method + '').toUpperCase()) + " "
}
if (!blank(body?.status)) {
    whereSql += " and " + statusExpr + " = " + sqlValue(body.status) + " "
}

var offset = (pageNum - 1) * pageSize

var listSql = """
select
  a.tid as id,
  a.tid as tid,
  a.service_name as serviceName,
  a.service_name as name,
  a.service_code as serviceCode,
  a.service_code as code,
  a.resource_id as resourceId,
  coalesce(a.group_id, 'api_service_group_default') as groupId,
  a.service_type as serviceType,
  coalesce(a.service_create_type, 'api') as serviceCreateType,
  """ + statusExpr + """ as status,
  coalesce(a.protocol, 'HTTP') as protocol,
  coalesce(a.uri, '') as uri,
  upper(coalesce(a.method, 'GET')) as method,
  coalesce(a.publish_address, a.uri, '') as publishAddress,
  coalesce(a.publish_address_full, a.publish_address, a.uri, '') as publishAddressFull,
  a.service_desc as serviceDesc,
  a.version as version,
  a.labels as labels,
  a.catalog_id as catalogId,
  a.org_id as orgId,
  a.provider_config as providerConfig,
  a.publish_config as publishConfig,
  a.flow_id as flowId,
  coalesce(a.call_count, 0) as requestCount,
  coalesce(a.success_percent, 0) as successPercent,
  (select count(1)
     from api_param_t p
    where p.api_id = a.tid
      and p.is_del = 0) as paramCount,
  0 as apiFileCount,
  a.created_time as createdTime,
  a.updated_time as updatedTime
from api_info_t a
""" + whereSql + """
order by a.updated_time desc, a.created_time desc
limit """ + offset + ", " + pageSize

var list = db.select(listSql)
list = magicResourceLink.attachApiFileCounts(list)

// 分组字典与服务主表可能来自不同时期建表，字符集排序规则不一定一致；
// 这里用内存映射规避跨表 collation 问题，也方便后续切换达梦。
var groupRows = db.select("""
select tid as id, dict_name as name
  from sym_dict_t
 where is_del = 0
   and biz_type = 'apiServiceGroup'
""")
var groupMap = {}
for (group in groupRows) {
    groupMap[(group.id || group.ID || group.tid || '') + ''] = group.name || group.dictName || group.dict_name
}
var viewList = []
for (row in list) {
    var gid = row.groupId || row.groupid || row.group_id || 'api_service_group_default'
    row.groupId = gid
    row.groupName = groupMap[gid + ''] || '默认分组'
    row.name = row.serviceName || row.servicename || row.service_name || row.name
    row.serviceName = row.name
    row.code = row.serviceCode || row.servicecode || row.service_code || row.code
    row.serviceCode = row.code
    // 输出给前端的 DTO 只保留一套字段，避免 DB 返回的小写别名和 camelCase 字段并存。
    viewList.add({
        id: row.id || row.tid,
        tid: row.tid || row.id,
        serviceName: row.serviceName || '',
        name: row.serviceName || '',
        serviceCode: row.serviceCode || '',
        code: row.serviceCode || '',
        resourceId: row.resourceId || row.resourceid || row.resource_id,
        groupId: row.groupId || gid,
        groupName: row.groupName || '默认分组',
        serviceType: row.serviceType || row.servicetype || row.service_type,
        serviceCreateType: row.serviceCreateType || row.servicecreatetype || row.service_create_type || 'api',
        status: row.status || 'published',
        protocol: row.protocol || 'HTTP',
        uri: row.uri || '',
        method: row.method || 'GET',
        publishAddress: row.publishAddress || row.publishaddress || row.publish_address || '',
        publishAddressFull: row.publishAddressFull || row.publishaddressfull || row.publish_address_full || '',
        serviceDesc: row.serviceDesc || row.servicedesc || row.service_desc,
        version: row.version,
        labels: row.labels,
        catalogId: row.catalogId || row.catalogid || row.catalog_id,
        orgId: row.orgId || row.orgid || row.org_id,
        providerConfig: row.providerConfig || row.providerconfig || row.provider_config,
        publishConfig: row.publishConfig || row.publishconfig || row.publish_config,
        extendInfo: {},
        flowId: row.flowId || row.flowid || row.flow_id,
        requestCount: row.requestCount || row.requestcount || row.request_count || 0,
        successPercent: row.successPercent || row.successpercent || row.success_percent || 0,
        paramCount: row.paramCount || row.paramcount || row.param_count || 0,
        apiFileCount: row.apiFileCount || row.apifilecount || row.api_file_count || 0,
        createdTime: row.createdTime || row.createdtime || row.created_time,
        updatedTime: row.updatedTime || row.updatedtime || row.updated_time
    })
}

// 统计当前过滤条件下不同服务状态的数量，供顶部卡片筛选使用。
var statsRows = db.select("""
select
  count(1) as total,
  sum(case when """ + statusExpr + """ = 'draft' then 1 else 0 end) as draft,
  sum(case when """ + statusExpr + """ = 'published' then 1 else 0 end) as published,
  sum(case when """ + statusExpr + """ = 'offline' then 1 else 0 end) as offline
from api_info_t a
""" + whereSql)
var stats = statsRows == null || statsRows.size() == 0
    ? {total: 0, draft: 0, published: 0, offline: 0}
    : statsRows[0]
var total = stats.total == null ? 0 : stats.total

return {
    list: viewList,
    total: total,
    pageNum: pageNum,
    pageSize: pageSize,
    stats: stats
}
