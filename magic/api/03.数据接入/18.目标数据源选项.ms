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
  "requestBody": "{\n  \"taskCategory\": \"ingestion\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"options\": [\n    {\n      \"label\": \"贴源层 ODS\",\n      \"value\": \"ODS\",\n      \"children\": [\n        {\n          \"label\": \"原始库 / 示例数据源\",\n          \"value\": \"datasource-id\",\n          \"layerCode\": \"ODS\"\n        }\n      ]\n    }\n  ],\n  \"list\": []\n}",
  "description": "物化建表目标数据源选项。taskCategory=ingestion（默认）返回当前租户、角色可见的 ODS 分域数据源；distribution 返回接入左树同口径的已登记非 ODS 数据源，按部门→数据源分组，不依赖 ES。分发最多读取 1000 个候选，一次关联查询，不返回连接凭据；未完成登记或未提供连接的叶节点仅展示、不可选。涉及 sym_dict_t、dwm_center_layer_source_t、db_datasource_t、rm_org_t。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：18.目标数据源选项
// 路由：POST /ods/targetDatasource/options
// 接入返回 ODS 分域；分发按接入左树数据源范围返回部门→数据源。
/*
 * 物化建表目标数据源选项。
 * 两种任务共享真实数据库类型和角色范围校验，分发目标不默认勾选。
 */
import tenantRuntime
import dataScope

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录上下文无效，请重新登录'
}

var taskCategory = blank(body?.taskCategory) ? 'ingestion' : text(body.taskCategory).trim()
if (taskCategory != 'ingestion' && taskCategory != 'distribution') {
    exit 400, '不支持的任务分类'
}
if (taskCategory == 'distribution') {
    // 同接入左树：已登记的非 ODS 数据源。只投影选项与权限所需字段，部门也校验租户和有效性。
    // LIMIT 多读一项用于明确拒绝超上限，不能静默截断或逐数据源补查。
    var candidates = db.select("""
        SELECT d.tid, d.tenant_id, d.org_id, d.created_by, d.db_name,
               d.db_type, d.show_connect, d.asset_status, o.name AS department_name
          FROM db_datasource_t d
          JOIN rm_org_t o ON o.id=d.org_id AND o.tenant_id=d.tenant_id AND o.deleted=0
         WHERE d.tenant_id=#{tenantId} AND d.is_del=0 AND d.asset_status>0
           AND NOT EXISTS (
                SELECT 1 FROM dwm_center_layer_source_t binding
                JOIN sym_dict_t node ON node.tid=binding.target_id
                     AND node.tenant_id=#{tenantId} AND node.is_del=0
                JOIN sym_dict_t ods ON ods.tenant_id=#{tenantId} AND ods.is_del=0
                     AND LOWER(ods.dict_code)='ods'
                     AND ((binding.target_type='layer' AND node.tid=ods.tid)
                          OR (binding.target_type='domain' AND node.parent_id=ods.tid))
                JOIN sym_dict_t root ON root.tid=ods.parent_id AND root.tenant_id=#{tenantId}
                     AND root.is_del=0 AND root.parent_id='0' AND root.dict_code='dataSourceType'
                WHERE binding.tenant_id=#{tenantId} AND binding.is_del=0 AND binding.datasource_id=d.tid
           )
         ORDER BY o.sort_num, o.id, d.db_name, d.tid
         LIMIT 1001
    """)
    if (candidates.size() > 1000) {
        exit 400, '分发目标数据源候选超过 1000 个，请联系管理员调整可用数据源范围'
    }
    var visibleCandidates = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', candidates)
    var departmentOptions = []
    var departmentMap = {}
    var distributionList = []
    for (datasource in visibleCandidates) {
        var departmentId = text(datasource.orgId)
        var department = departmentMap[departmentId]
        if (department == null) {
            department = { value: departmentId, label: datasource.departmentName, children: [] }
            departmentMap[departmentId] = department
            departmentOptions.add(department)
        }
        var datasourceId = text(datasource.tid)
        var datasourceName = blank(datasource.dbName) ? datasourceId : text(datasource.dbName)
        var unavailable = text(datasource.showConnect) != '1' || text(datasource.assetStatus) != '2'
        var reason = text(datasource.showConnect) != '1' ? '未提供连接' : '未完成登记'
        var option = {
            label: datasourceName + (unavailable ? '（' + reason + '）' : ''),
            value: datasourceId, tid: datasourceId, datasourceId: datasourceId,
            datasourceName: datasourceName, dbType: datasource.dbType,
            orgId: departmentId, parentValue: departmentId,
            showConnect: datasource.showConnect, assetStatus: datasource.assetStatus,
            disabled: unavailable
        }
        department.children.add(option)
        distributionList.add(option)
    }
    return { options: departmentOptions, list: distributionList, total: distributionList.size() }
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
