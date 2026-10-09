{
  "properties": {},
  "id": "ods_target_datasource_options_01",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "18.目标数据源选项",
  "createTime": 1785067200000,
  "updateTime": 1787156377696,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/targetDatasource/options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"options\": [\n    {\n      \"label\": \"贴源层 ODS\",\n      \"value\": \"ODS\",\n      \"children\": [\n        {\n          \"label\": \"原始库 / 示例数据源\",\n          \"value\": \"datasource-id\",\n          \"layerCode\": \"ODS\"\n        }\n      ]\n    }\n  ],\n  \"list\": []\n}",
  "description": "目标数据源选项（POST /ods/targetDatasource/options）。只读接口；按当前租户的数据组织 dataSourceType 字典读取贴源层 ODS 及其分域，并通过分层分域配置的关联表返回每个分域已配置且在当前角色数据范围内的目标数据源。用于数据接入物化建表弹框，不依赖 ES；涉及 sym_dict_t、dwm_center_layer_source_t、db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：18.目标数据源选项
// 路由：POST /ods/targetDatasource/options
// 业务说明：目标数据源选项（POST /ods/targetDatasource/options）。只读接口；按当前租户的数据组织 dataSourceType 字典读取贴源层 ODS 及其分域，并通过分层分域配置的关联表返回每个分域已配置的目标数据源。用于数据接入物化建表弹框，不依赖 ES；涉及 sym_dict_t、dwm_center_layer_source_t、db_datasource_t 等业务表。
/*
 * 物化建表目标数据源选项。
 * 只读取当前租户 dataSourceType -> ODS 下的分域，并取每个分域在
 * dwm_center_layer_source_t 中关联的目标数据源；不会混入普通数据源登记列表。
 */
import tenantRuntime
import dataScope

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录上下文无效，请重新登录'
}

var root = db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('dict_code', 'dataSourceType')
    .eq('parent_id', '0')
    .eq('is_del', 0)
    .selectOne()
if (root == null) {
    return { options: [], list: [], total: 0 }
}

var ods = db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('parent_id', root.tid)
    .eq('dict_code', 'ods')
    .eq('is_del', 0)
    .selectOne()
if (ods == null) {
    return { options: [], list: [], total: 0 }
}

var domains = db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('parent_id', ods.tid)
    .eq('is_del', 0)
    .orderBy('sort_no', 'asc')
    .select()
var domainMap = {}
var domainIds = []
for (domain in (domains == null ? [] : domains)) {
    var domainId = text(domain.tid)
    domainMap[domainId] = domain
    domainIds.add(domainId)
}
if (domainIds.size() == 0) {
    return { options: [], list: [], total: 0 }
}

var relations = db.table('dwm_center_layer_source_t').where()
    .eq('tenant_id', tenantId)
    .eq('target_type', 'domain')
    .in('target_id', domainIds)
    .eq('is_del', 0)
    .orderBy('updated_time', 'desc')
    .select()
var relationByDomain = {}
for (relation in (relations == null ? [] : relations)) {
    var relationDomainId = text(relation.targetId)
    if (relationByDomain[relationDomainId] == null && !blank(relation.datasourceId)) {
        relationByDomain[relationDomainId] = relation
    }
}

var datasourceIds = []
for (domainId in domainIds) {
    var relation = relationByDomain[domainId]
    if (relation != null) {
        datasourceIds.add(text(relation.datasourceId))
    }
}
if (datasourceIds.size() == 0) {
    return { options: [], list: [], total: 0 }
}

var datasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId)
    .in('tid', datasourceIds)
    .eq('is_del', 0)
    .eq('show_connect', 1)
    .select()
var visibleDatasources = dataScope.visibleDataSourcesFor(
    'MENU:2081000000000000002', datasourceRows == null ? [] : datasourceRows)
var datasourceMap = {}
for (datasource in visibleDatasources) {
    datasourceMap[text(datasource.tid)] = datasource
}

var children = []
var list = []
for (domain in (domains == null ? [] : domains)) {
    var domainId = text(domain.tid)
    var relation = relationByDomain[domainId]
    var datasource = relation == null ? null : datasourceMap[text(relation.datasourceId)]
    if (datasource == null) {
        continue
    }
    var datasourceId = text(datasource.tid)
    var datasourceName = text(datasource.dbName).trim()
    if (blank(datasourceName)) {
        datasourceName = text(datasource.database).trim()
    }
    var option = {
        label: text(domain.dictName) + ' / ' + datasourceName,
        value: datasourceId,
        tid: datasourceId,
        datasourceId: datasourceId,
        datasourceName: datasourceName,
        dbType: datasource.dbType,
        domainId: domainId,
        domainName: domain.dictName,
        domainCode: domain.dictCode,
        layerCode: 'ODS',
        tablePrefix: 'ODS',
        parentValue: 'ODS',
        showConnect: datasource.showConnect,
        assetStatus: datasource.assetStatus
    }
    children.add(option)
    list.add(option)
}

var options = children.size() == 0 ? [] : [{
    label: ods.dictName,
    value: 'ODS',
    layerCode: 'ODS',
    children: children
}]
return { options: options, list: list, total: list.size() }
