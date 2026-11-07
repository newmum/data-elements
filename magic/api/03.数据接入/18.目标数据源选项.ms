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
  "description": "物化建表目标数据源选项。taskCategory=ingestion（默认）返回当前租户内有效的 ODS 分域数据源；distribution 返回接入左树同口径的已登记非 ODS 数据源，按部门→数据源分组，不依赖 ES。分发最多读取 1000 个候选，一次关联查询，不返回连接凭据；未完成登记或未提供连接的叶节点仅展示、不可选。涉及 sym_dict_t、dwm_center_layer_source_t、db_datasource_t、rm_org_t。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead
import '@/common/ods-source-classifier' as odsSourceClassifier

/**
 * 物化建表目标数据源选项。POST /ods/targetDatasource/options
 * 接入返回当前租户 ODS 分域，分发返回有效部门下已登记的非 ODS 数据源。
 * taskCategory 默认接入，返回级联 options、平铺 list 与 total。
 */

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
    // 与接入左树共用 ODS 分类，部门与数据源均须在当前租户有效。
    var classification = odsSourceClassifier()
    var candidates = tableRead.from('db_datasource_t', 'd', tenantId)
    var datasourceIdColumn = tableRead.column('d', 'tid', 'TEXT')
    var departmentIdColumn = tableRead.column('d', 'org_id', 'TEXT')
    var validDepartmentId = tableRead.column('o', 'id', 'TEXT')
    candidates.leftJoin('rm_org_t', 'o', 'id', 'org_id', [
        tableRead.equal(tableRead.column('o', 'deleted', 'NUMBER'), 0)
    ])
    candidates.leftJoinGrouped(classification.query, 'ods', 'source_id', 'tid',
        classification.projection, classification.groups)
    candidates.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
    candidates.where(tableRead.greaterThan(tableRead.column('d', 'asset_status', 'NUMBER'), 0))
    candidates.where(tableRead.not(tableRead.isNull(validDepartmentId)))
    candidates.where(tableRead.isNull(tableRead.column('ods', 'source_id', 'TEXT')))

    var candidateLimit = 1000
    var candidateRows = candidates.rows({
        tid: datasourceIdColumn, orgId: departmentIdColumn, departmentId: validDepartmentId
    }, datasourceIdColumn, 'asc', candidateLimit)
    if (candidateRows.size() == 0) {
        return {options: [], list: [], total: 0}
    }
    if (candidateRows.size() == candidateLimit) {
        // 首窗满时再探查一项，明确拒绝溢出。
        candidates.where(tableRead.greaterThan(datasourceIdColumn, candidateRows[candidateLimit - 1].tid))
        var overflow = candidates.rows({tid: datasourceIdColumn}, datasourceIdColumn, 'asc', 1)
        if (overflow.size() > 0) {
            exit 400, '分发目标数据源候选超过 1000 个，请联系管理员调整可用数据源范围'
        }
    }

    var candidateById = {}
    var datasourceIds = []
    var departmentIds = []
    for (candidate in candidateRows) {
        var candidateId = text(candidate.tid)
        var departmentId = text(candidate.departmentId)
        datasourceIds.add(candidateId)
        candidateById[candidateId] = candidate
        if (!departmentIds.contains(departmentId)) {
            departmentIds.add(departmentId)
        }
    }

    // 按数据库原生顺序分别读取部门和库名，再按部门组装，保持同序号、同库名的 ID 顺序。
    var departments = db.camel().table('rm_org_t').columns('id', 'name').where()
        .eq('tenant_id', tenantId).eq('deleted', 0).in('id', departmentIds)
        .orderBy('sort_num', 'asc').orderBy('id', 'asc').select()
    var datasources = db.camel().table('db_datasource_t')
        .columns('tid', 'org_id', 'db_name', 'db_type', 'show_connect', 'asset_status').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).gt('asset_status', 0).in('tid', datasourceIds)
        .orderBy('db_name', 'asc').orderBy('tid', 'asc').select()
    var optionsByDepartment = {}
    for (datasource in datasources) {
        var orgId = text(datasource.orgId)
        var datasourceId = text(datasource.tid)
        var candidate = candidateById[datasourceId]
        if (candidate == null || text(candidate.orgId) != orgId) {
            continue
        }
        var departmentId = text(candidate.departmentId)
        if (optionsByDepartment[departmentId] == null) {
            optionsByDepartment[departmentId] = []
        }
        var datasourceName = blank(datasource.dbName) ? datasourceId : text(datasource.dbName)
        var unavailable = text(datasource.showConnect) != '1' || text(datasource.assetStatus) != '2'
        var reason = text(datasource.showConnect) != '1' ? '未提供连接' : '未完成登记'
        var option = {
            label: datasourceName + (unavailable ? '（' + reason + '）' : ''),
            value: datasourceId, tid: datasourceId, datasourceId: datasourceId,
            datasourceName: datasourceName, dbType: datasource.dbType,
            orgId: orgId, parentValue: orgId,
            showConnect: datasource.showConnect, assetStatus: datasource.assetStatus,
            disabled: unavailable
        }
        optionsByDepartment[departmentId].add(option)
    }
    var departmentOptions = []
    var departmentByOrgId = {}
    var distributionList = []
    for (department in departments) {
        var children = optionsByDepartment[text(department.id)]
        if (children == null) {
            continue
        }
        for (option in children) {
            var orgId = option.orgId
            var group = departmentByOrgId[orgId]
            if (group == null) {
                group = {value: orgId, label: department.name, children: []}
                departmentByOrgId[orgId] = group
                departmentOptions.add(group)
            }
            group.children.add(option)
            distributionList.add(option)
        }
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
    .orderBy('tid', 'asc')
    .select()
var domainIds = []
for (domain in (domains == null ? [] : domains)) {
    domainIds.add(text(domain.tid))
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
var visibleDatasources = (datasourceRows == null ? [] : datasourceRows)
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
        domainOrder: domain.sortNo,
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
