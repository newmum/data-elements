{
  "properties" : { },
  "id" : "4e0ee97e906054229d56e22da830272e",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "08.planning-read",
  "createTime" : null,
  "updateTime" : 1790989454788,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/planning-read",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
import tenantRuntime
import dataScope
import tableRead
import jsons
import cn.hutool.crypto.digest.DigestUtil

/**
 * 磐石数仓规划读取。/resource-center/planning-read
 * 返回当前租户的分层、分域、存储绑定和目录分类。数据源名称、类型和采集表数受角色范围限制。
 * 分层、分域、绑定和分类各最多 1000 项，函数只读。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var scope = dataScope.dataSource()
var text = value => value == null ? '' : value + ''
var json = value => text(value) == '' ? {} : jsons.toJSONObject(value)
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}

var activeRows = (table, fields) => db.camel().table(table).columns(fields).where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
var dictionaryRoot = code => {
    var query = tableRead.from('sym_dict_t', 'd', tenantId)
    var id = tableRead.column('d', 'tid', 'TEXT')
    query.where(tableRead.equal(tableRead.column('d', 'dict_code', 'TEXT'), code))
    query.where(tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0))
    var rows = query.rows({tid: id}, id, 'asc', 1)
    return rows.size() == 0 ? null : rows[0].tid
}
var orderedDictionary = query => query.orderBy('sort_no', 'asc').orderBy('dict_name', 'asc').orderBy('tid', 'asc')
var viewRows = query => {
    var page = query.page(1001, 0)
    var rows = page?.list == null ? [] : page.list
    if (rows.size() > 1000) {
        exit 400, '规划配置超过视图上限'
    }
    return rows
}

// 字典使用当前租户的活动记录。分域按已选分层 ID 一次读取，保留数据库的同级排序。
var dictionaryFields = ['tid', 'dict_name', 'dict_code', 'parent_id', 'sort_no', 'dict_desc',
    'planning_config_json', 'revision_no', 'created_time', 'updated_time']
var rootId = dictionaryRoot('dataSourceType')
var classRootId = dictionaryRoot('resourceCatalogClass')
var layerRows = rootId == null ? [] : viewRows(orderedDictionary(
    activeRows('sym_dict_t', dictionaryFields).eq('parent_id', rootId)))
var layerIds = layerRows.map(row => row.tid)
var domainRows = layerIds.size() == 0 ? [] : viewRows(orderedDictionary(
    activeRows('sym_dict_t', dictionaryFields).in('parent_id', layerIds)))
var classRows = classRootId == null ? [] : viewRows(orderedDictionary(activeRows('sym_dict_t',
    ['tid', 'dict_name', 'dict_code', 'revision_no', 'created_time', 'updated_time']).eq('parent_id', classRootId)))

// 先确定有界绑定集合和最新顺序，再按同一批 ID 关联可见数据源。失效或不可见的数据源保留空投影。
var relationKeys = viewRows(activeRows('dwm_center_layer_source_t', ['tid'])
    .in('target_type', ['layer', 'domain']).orderBy('updated_time', 'desc').orderBy('tid', 'desc'))
var relationRows = []
if (relationKeys.size() > 0) {
    var binding = (column, kind) => tableRead.column('b', column, kind)
    var source = (column, kind) => tableRead.column('s', column, kind)
    var bindingId = binding('tid', 'TEXT')
    var sourceScope = [tableRead.equal(source('is_del', 'NUMBER'), 0)]
    if (scopeMode == 'OWNER') {
        sourceScope.add(tableRead.equal(source('created_by', 'TEXT'), scopeUserId))
    } else if (scopeMode == 'ORG') {
        sourceScope.add(tableRead.in(source('org_id', 'TEXT'), scopeOrgIds))
    }
    var relations = tableRead.from('dwm_center_layer_source_t', 'b', tenantId)
    relations.leftJoin('db_datasource_t', 's', 'tid', 'datasource_id', sourceScope)
    relations.where(tableRead.equal(binding('is_del', 'NUMBER'), 0))
    relations.where(tableRead.in(binding('target_type', 'TEXT'), ['layer', 'domain']))
    relations.where(tableRead.in(bindingId, relationKeys.map(row => row.tid)))
    var relationsById = {}
    var rows = relations.rows({
        tid: bindingId, targetId: binding('target_id', 'TEXT'), targetType: binding('target_type', 'TEXT'),
        sourceId: binding('datasource_id', 'TEXT'), config: binding('configuration_json', 'LOB'),
        version: binding('revision_no', 'NUMBER'), createdTime: binding('created_time', 'TIME'),
        updatedTime: binding('updated_time', 'TIME'), sourceName: source('db_name', 'TEXT'),
        profile: source('db_type', 'TEXT'), sourceUpdatedAt: source('updated_time', 'TIME')
    }, bindingId, 'asc', relationKeys.size())
    for (row in rows) {
        relationsById[row.tid] = row
    }
    for (row in relationKeys) {
        if (relationsById[row.tid] != null) {
            relationRows.add(relationsById[row.tid])
        }
    }
}

// 仅对名称可见的绑定数据源按 ID 汇总采集表，一次聚合读取。
var seenSourceIds = {}
var relationSourceIds = []
for (row in relationRows) {
    if (row.sourceName != null && row.sourceId != null && seenSourceIds[row.sourceId] == null) {
        seenSourceIds[row.sourceId] = true
        relationSourceIds.add(row.sourceId)
    }
}
var tableCounts = {}
if (relationSourceIds.size() > 0) {
    var tables = tableRead.from('db_table_t', 't', tenantId)
    var sourceId = tableRead.column('t', 'datasource_id', 'TEXT')
    tables.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    tables.where(tableRead.in(sourceId, relationSourceIds))
    var counts = tables.grouped({sourceId: sourceId, tableCount: tableRead.count()}, [sourceId])
    for (row in counts) {
        tableCounts[text(row.sourceId)] = row.tableCount
    }
}
var relationByTarget = {}
for (row in relationRows) {
    var key = text(row.targetType) + ':' + text(row.targetId)
    if (relationByTarget[key] == null) {
        relationByTarget[key] = row
    }
}
var layers = []
var layerById = {}
var builtIns = ['DST', 'ODS', 'DWD', 'DWM', 'DWS', 'ODAS']
for (row in layerRows) {
    var config = json(row.planningConfigJson)
    var source = relationByTarget['layer:' + text(row.tid)]
    var sourceId = source == null ? '' : text(source.sourceId)
    var code = text(row.dictCode).toUpperCase()
    var layer = {
        id: text(row.tid), name: row.dictName, code: code,
        purpose: text(row.dictDesc), owner: text(config.owner), order: layers.size(), sortNo: row.sortNo,
        builtIn: builtIns.contains(code), state: config.state == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        sourceId: sourceId, sourceName: source == null ? '' : text(source.sourceName),
        sourceDbType: source == null ? '' : text(source.profile),
        bindingId: source == null ? '' : text(source.tid),
        bindingVersion: source == null ? null : source.version,
        tableCount: tableCounts[sourceId] == null ? 0 : tableCounts[sourceId],
        version: row.revisionNo, createdAt: row.createdTime, updatedAt: row.updatedTime
    }
    layers.add(layer)
    layerById[layer.id] = layer
}
var databases = []
var domainById = {}
var domainGroups = {}
// 同一父层排序号的分域共享排序组，组内保留数据库的分域排序。父层顺序也沿用数据库的空值排序规则。
for (row in domainRows) {
    var parent = layerById[text(row.parentId)]
    if (parent == null) {
        continue
    }
    var config = json(row.planningConfigJson)
    var source = relationByTarget['domain:' + text(row.tid)]
    var sourceId = source == null ? '' : text(source.sourceId)
    var domain = {
        id: text(row.tid), name: row.dictName, code: text(row.dictCode), layerId: parent.id,
        domain: config.domain == null ? text(row.dictName) : text(config.domain),
        owner: text(config.owner), purpose: text(row.dictDesc),
        mode: parent.code == 'DST' || config.mode == 'REFERENCE' ? 'REFERENCE' : 'MANAGED',
        state: config.state == 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
        sourceId: sourceId, sourceName: source == null ? '' : text(source.sourceName),
        sourceDbType: source == null ? '' : text(source.profile),
        bindingId: source == null ? '' : text(source.tid),
        bindingVersion: source == null ? null : source.version,
        tableCount: tableCounts[sourceId] == null ? 0 : tableCounts[sourceId],
        version: row.revisionNo, createdAt: row.createdTime, updatedAt: row.updatedTime
    }
    var parentOrder = text(parent.sortNo)
    if (domainGroups[parentOrder] == null) {
        domainGroups[parentOrder] = []
    }
    domainGroups[parentOrder].add(domain)
    domainById[domain.id] = domain
}
for (layer in layers) {
    var group = domainGroups.remove(text(layer.sortNo))
    if (group != null) {
        databases.addAll(group)
    }
}

var bindings = []
for (row in relationRows) {
    if (row.targetType != 'domain' || domainById[text(row.targetId)] == null) {
        continue
    }
    var config = json(row.config)
    var environment = config.environment == null ? 'DEV' : config.environment
    var category = config.category == null ? 'REFERENCE' : config.category
    var hash = DigestUtil.sha256Hex(text(row.sourceId) + '|' + text(row.sourceUpdatedAt) + '|' + environment + '|' +
        text(config.catalog) + '|' + text(config.schema) + '|' + text(config.prefix) + '|' + category)
    bindings.add({
        id: text(row.tid), databaseId: text(row.targetId), sourceId: text(row.sourceId),
        environment: environment, catalog: text(config.catalog), schema: text(config.schema), prefix: text(config.prefix),
        category: category, profile: text(row.profile), sourceName: text(row.sourceName),
        tableCount: tableCounts[text(row.sourceId)] == null ? 0 : tableCounts[text(row.sourceId)],
        state: row.sourceName == null ? 'INVALID' : config.checkedHash == hash ? 'VALID' : 'NOT_CHECKED',
        checkedHash: config.checkedHash, checkedAt: config.checkedAt, currentHash: hash,
        version: row.version, createdAt: row.createdTime, updatedAt: row.updatedTime
    })
}
var classes = []
for (row in classRows) {
    classes.add({
        id: text(row.tid), name: row.dictName, code: text(row.dictCode), version: row.revisionNo,
        createdAt: row.createdTime, updatedAt: row.updatedTime
    })
}
return {layers: layers, databases: databases, bindings: bindings, classifications: classes}
