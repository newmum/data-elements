{
  "properties": {},
  "id": "77dd13a825764993848cb05d8cb6a771",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "01.资源中心总览",
  "createTime": 1782955345141,
  "updateTime": 1787153757526,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/layer-list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心总览：按当前租户和角色数据范围聚合五层表数及记录数，不加载表资产正文。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@get:/dwm/center/dst-info' as dstInfo
import tenantRuntime
import dataScope
import tableRead
import java.util.Map
import cn.hutool.json.JSONUtil

// GET /dwm/center/layer-list 无请求参数，返回当前租户的数仓分层、分域、可见绑定及数据统计。
// 字典使用当前租户的活动 dataSourceType 根。按角色限定数据源和资产范围，关联及统计在集合遍历前完成。
// 配置只用于服务端解析历史分类，响应不返回连接凭据。接口只读。
var textOf = value => value == null ? '' : ('' + value).trim()
var blank = value => textOf(value) == ''
var lower = value => textOf(value).toLowerCase()
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value == null) {
            value = row[key.toUpperCase()]
        }
        if (value != null) {
            return value
        }
    }
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            return value
        }
    }
    return null
}
var tenantId = textOf(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前租户无效'
}
var scope = dataScope.dataSource()
var scopeMode = textOf(scope?.scope)
var scopeUserId = textOf(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
// 各聚合沿用同一可见数据源条件。空组织集合直接匹配空集。
var visibleSource = (query, alias) => {
    query.where(tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0))
    if (scopeMode == 'OWNER') {
        query.where(tableRead.equal(tableRead.column(alias, 'created_by', 'TEXT'), scopeUserId))
    } else if (scopeMode == 'ORG') {
        query.where(tableRead.in(tableRead.column(alias, 'org_id', 'TEXT'), scopeOrgIds))
    }
}

// 只从有效的顶层根定位目录，兼容旧库用空父标识表示顶层的记录。
var dictionary = tableRead.from('sym_dict_t', 'r', tenantId)
var dictionaryId = tableRead.column('r', 'tid', 'TEXT')
var dictionaryParent = tableRead.column('r', 'parent_id', 'TEXT')
dictionary.where(tableRead.equal(tableRead.column('r', 'dict_code', 'TEXT'), 'dataSourceType'))
dictionary.where(tableRead.equal(tableRead.column('r', 'is_del', 'NUMBER'), 0))
dictionary.where(tableRead.any([
    tableRead.equal(dictionaryParent, '0'), tableRead.equal(dictionaryParent, ''), tableRead.isNull(dictionaryParent)
]))
var dictionaryRoots = dictionary.rows({
    tid: dictionaryId, treePath: tableRead.column('r', 'tree_path', 'TEXT')
}, dictionaryId, 'asc', 1)
var layerRows = []
var dataSourceTypeId = ''
if (dictionaryRoots.size() > 0) {
    var dictionaryRoot = dictionaryRoots[0]
    dataSourceTypeId = textOf(dictionaryRoot.tid)
    var rootPath = textOf(dictionaryRoot.treePath)
    if (rootPath != '' && !rootPath.contains('%') && !rootPath.contains('_')) {
        var prefix = (rootPath.endsWith('.') ? rootPath : rootPath + '.') + '%'
        var orderedLayers = db.camel().table('sym_dict_t').columns('tid', 'dict_name', 'dict_code', 'parent_id', 'tree_path')
            .where().eq('tenant_id', tenantId).eq('is_del', 0).ne('tid', dataSourceTypeId).like('tree_path', prefix)
            .orderBy('level_no', 'asc').orderBy('sort_no', 'asc').orderBy('dict_name', 'asc').orderBy('tid', 'asc')
        var windowSize = 1000
        var first = orderedLayers.page(windowSize, 0)
        if (first.list != null) {
            layerRows.addAll(first.list)
        }
        var offset = windowSize
        while (first.total != null && offset < first.total) {
            var next = orderedLayers.page(windowSize, offset)
            if (next.list == null || next.list.size() == 0) {
                break
            }
            layerRows.addAll(next.list)
            offset = offset + windowSize
        }
    }
}
var roots = {}
var childrenByParent = {}
var rootByCode = { dst: 'dst', ods: 'ods', dwd: 'dwd', dwm: 'dwm', dws: 'dws' }
for (row in layerRows) {
    var code = lower(firstValue(row, ['dictCode', 'dict_code']))
    var parentId = textOf(firstValue(row, ['parentId', 'parent_id']))
    if (parentId == dataSourceTypeId && rootByCode[code] != null) {
        roots[code] = row
    }
    if (childrenByParent[parentId] == null) {
        childrenByParent[parentId] = []
    }
    childrenByParent[parentId].add(row)
}
// 一次建立分类索引；后续只遍历按来源和分类汇总的行，不遍历物理表。
for (row in layerRows) {
    var code = lower(firstValue(row, ['dictCode', 'dict_code']))
    if (rootByCode[code] != null) {
        continue
    }
    var treePath = '.' + textOf(firstValue(row, ['treePath', 'tree_path'])) + '.'
    for (rootCode in ['ods', 'dwd', 'dwm', 'dws']) {
        var rootId = textOf(firstValue(roots[rootCode], ['tid']))
        if (rootId != '' && treePath.indexOf('.' + rootId + '.') >= 0) {
            rootByCode[code] = rootCode
            break
        }
    }
}
var rootCodeOf = code => rootByCode[lower(code)] == null ? 'dst' : rootByCode[lower(code)]
// 同次查询取得可见数据源和按来源聚合的旧分类属性，按来源 ID 分窗读取。
var legacyTypes = tableRead.from('da_prop_t', 'p', tenantId)
var legacySourceId = tableRead.column('p', 'parent_id', 'TEXT')
var legacyValue = tableRead.column('p', 'prop_value', 'LOB')
legacyTypes.leftJoin('db_datasource_t', 'registered', 'tid', 'parent_id', [])
legacyTypes.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
legacyTypes.where(tableRead.in(tableRead.column('p', 'prop_name', 'TEXT'), ['dataSourceType', 'connection.dataSourceType']))
visibleSource(legacyTypes, 'registered')
var legacyProjection = {source_id: legacySourceId, source_type: tableRead.max(tableRead.boundedText(legacyValue))}
var datasourceId = tableRead.column('d', 'tid', 'TEXT')
var sourceProjection = {
    tid: datasourceId, dbName: tableRead.column('d', 'db_name', 'TEXT'), dbType: tableRead.column('d', 'db_type', 'TEXT'),
    poolCfg: tableRead.column('d', 'pool_cfg', 'LOB'), legacyType: tableRead.column('legacy_type', 'source_type', 'TEXT')
}
var sourceTypeMap = {}
var datasourceMap = {}
var sourceCursor = null
var sourceWindow = 1000
while (true) {
    var sources = tableRead.from('db_datasource_t', 'd', tenantId)
    visibleSource(sources, 'd')
    sources.leftJoinGrouped(legacyTypes, 'legacy_type', 'source_id', 'tid', legacyProjection, [legacySourceId])
    if (sourceCursor != null) {
        sources.where(tableRead.greaterThan(datasourceId, sourceCursor))
    }
    var sourceRows = sources.rows(sourceProjection, datasourceId, 'asc', sourceWindow)
    for (source in sourceRows) {
        var sourceId = textOf(source.tid)
        var poolConfig = source.poolCfg
        var sourceType = ''
        if (!blank(poolConfig) && JSONUtil.isTypeJSONObject(poolConfig)) {
            // 先读现代配置的分类，再读历史属性。连接配置保留在当前窗口内。
            var config = JSONUtil.parseObj(poolConfig)
            sourceType = lower(config.get('dataSourceType'))
            if (sourceType == '' && config.get('connection') instanceof Map) {
                sourceType = lower(config.get('connection').get('dataSourceType'))
            }
        }
        if (sourceType == '') {
            sourceType = lower(source.legacyType)
        }
        sourceTypeMap[sourceId] = sourceType
        datasourceMap[sourceId] = {tid: sourceId, dbName: blank(source.dbName) ? null : source.dbName,
            dbType: blank(source.dbType) ? null : source.dbType}
    }
    if (sourceRows.size() < sourceWindow) {
        break
    }
    sourceCursor = sourceRows[sourceRows.size() - 1].tid
}

// 绑定只读取会进入响应的字典节点，组织汇总行没有分域绑定。
var relationTargetIds = []
for (rootCode in ['dst', 'ods', 'dwd', 'dwm', 'dws']) {
    var rootId = textOf(firstValue(roots[rootCode], ['tid']))
    if (rootId == '') {
        continue
    }
    relationTargetIds.add(rootId)
    if (rootCode != 'dst' && childrenByParent[rootId] != null) {
        for (child in childrenByParent[rootId]) {
            relationTargetIds.add(child.tid)
        }
    }
}
var sourceRelationMap = {}
var relationStart = 0
while (datasourceMap.size() > 0 && relationStart < relationTargetIds.size()) {
    var relationEnd = Math.min(relationStart + 500, relationTargetIds.size())
    var relations = db.camel().table('dwm_center_layer_source_t').columns('target_id', 'target_type', 'datasource_id')
        .where().eq('tenant_id', tenantId).eq('is_del', 0).in('target_type', ['layer', 'domain'])
        .in('target_id', relationTargetIds.subList(relationStart, relationEnd)).select()
    for (relation in relations) {
        // 关联逐 ID 对照已授权的来源索引，无法通过分域绑定扩大来源范围。
        var source = datasourceMap[textOf(relation.datasourceId)]
        if (source != null) {
            sourceRelationMap[textOf(relation.targetType) + '_' + textOf(relation.targetId)] = source
        }
    }
    relationStart = relationEnd
}
var sourceInfoOf = (targetType, targetId) => {
    var source = sourceRelationMap[textOf(targetType) + '_' + textOf(targetId)]
    return source == null ? { datasourceId: null, datasourceName: null, dbType: null } :
        { datasourceId: source.tid, datasourceName: source.dbName, dbType: source.dbType }
}

// 数据库汇总已登记表，只返回来源、原始分类和计数。脚本归一分类时累加同义分组。
var tables = tableRead.from('db_table_t', 't', tenantId)
var tableSourceId = tableRead.column('t', 'datasource_id', 'TEXT')
var tableTypeColumn = tableRead.column('t', 'data_source_type', 'TEXT')
var records = tableRead.column('t', 'record_count', 'NUMBER')
tables.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [])
visibleSource(tables, 'd')
tables.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
tables.where(tableRead.equal(tableRead.column('t', 'asset_status', 'NUMBER'), 2))
var tableStats = tables.grouped({datasourceId: tableSourceId, tableType: tableTypeColumn,
    tableCount: tableRead.count(), dataCount: tableRead.coalesce([tableRead.sum(records), 0])}, [tableSourceId, tableTypeColumn])
var layerCounts = { dst: 0, ods: 0, dwd: 0, dwm: 0, dws: 0 }
var domainCounts = {}
var addDomain = (rootCode, code, tableCount, dataCount) => {
    if (blank(code)) {
        return
    }
    var key = rootCode + '_' + code
    if (domainCounts[key] == null) {
        domainCounts[key] = { tableCount: 0, dataCount: 0 }
    }
    domainCounts[key].tableCount = domainCounts[key].tableCount + tableCount
    domainCounts[key].dataCount = domainCounts[key].dataCount + dataCount
}
for (stats in tableStats) {
    var tableType = lower(firstValue(stats, ['tableType', 'table_type']))
    var sourceType = lower(sourceTypeMap[textOf(firstValue(stats, ['datasourceId', 'datasource_id']))])
    var rootCode = rootCodeOf(tableType == '' ? sourceType : tableType)
    var tableCount = rowValue(stats, ['tableCount', 'table_count'])::long
    var dataCount = rowValue(stats, ['dataCount', 'data_count'])::long
    layerCounts[rootCode] = layerCounts[rootCode] + tableCount
    // 保留表分类/来源分类匹配任一分域的原口径，同一代码不重复计数。
    addDomain(rootCode, tableType, tableCount, dataCount)
    if (sourceType != tableType) {
        addDomain(rootCode, sourceType, tableCount, dataCount)
    }
}
var buildDictionaryItems = rootCode => {
    var rootId = textOf(firstValue(roots[rootCode], ['tid']))
    var children = childrenByParent[rootId] == null ? [] : childrenByParent[rootId]
    var items = []
    for (child in children) {
        var childCode = lower(firstValue(child, ['dictCode', 'dict_code']))
        var counts = domainCounts[rootCode + '_' + childCode]
        var source = sourceInfoOf('domain', firstValue(child, ['tid']))
        items.add({
            id: firstValue(child, ['tid']), name: firstValue(child, ['dictName', 'dict_name']), layerCode: childCode,
            sourceId: source.datasourceId, sourceName: source.datasourceName, sourceDbType: source.dbType,
            catalogCount: counts == null ? 0 : counts.tableCount,
            tableCount: counts == null ? 0 : counts.tableCount,
            dataCount: counts == null ? 0 : counts.dataCount
        })
    }
    return items
}
// ODS 与其他数仓层一致，按 dataSourceType 字典下的二级分类展示。
// 例如 ODS 下的“原始库”节点即为可维护、可保存目标数据源的分域节点，
// 不再按应用系统聚合，确保分域关联可以正确回显。
var odsItems = buildDictionaryItems('ods')
var odsTableCount = layerCounts.ods
var dstResult = dstInfo()
var dwdItems = buildDictionaryItems('dwd')
var dwmItems = buildDictionaryItems('dwm')
var dwsItems = buildDictionaryItems('dws')
var dwsId = firstValue(roots.dws, ['tid'])
var dwmId = firstValue(roots.dwm, ['tid'])
var dwdId = firstValue(roots.dwd, ['tid'])
var odsId = firstValue(roots.ods, ['tid'])
var dstId = firstValue(roots.dst, ['tid'])
var dwsSource = sourceInfoOf('layer', dwsId)
var dwmSource = sourceInfoOf('layer', dwmId)
var dwdSource = sourceInfoOf('layer', dwdId)
var odsSource = sourceInfoOf('layer', odsId)
var dstSource = sourceInfoOf('layer', dstId)
return [
    {
        id: dwsId,
        name: 'DWS',
        title: '服务层 DWS',
        sourceId: dwsSource.datasourceId,
        sourceName: dwsSource.datasourceName,
        sourceDbType: dwsSource.dbType,
        categoryCount: layerCounts.dws,
        domains: [{ name: '专题库', items: dwsItems }]
    },
    {
        id: dwmId,
        name: 'DWM',
        title: '整合层 DWM',
        sourceId: dwmSource.datasourceId,
        sourceName: dwmSource.datasourceName,
        sourceDbType: dwmSource.dbType,
        categoryCount: layerCounts.dwm,
        domains: [{ name: '主题库', items: dwmItems }]
    },
    {
        id: dwdId,
        name: 'DWD',
        title: '基础层 DWD',
        sourceId: dwdSource.datasourceId,
        sourceName: dwdSource.datasourceName,
        sourceDbType: dwdSource.dbType,
        categoryCount: layerCounts.dwd,
        domains: [{ name: '标准库', items: dwdItems }]
    },
    {
        id: odsId,
        name: 'ODS',
        title: '贴源层 ODS',
        sourceId: odsSource.datasourceId,
        sourceName: odsSource.datasourceName,
        sourceDbType: odsSource.dbType,
        categoryCount: odsTableCount,
        domains: [{ name: '原始库', items: odsItems }]
    },
    {
        id: dstId,
        name: 'DST',
        title: '数源层 DST',
        sourceId: dstSource.datasourceId,
        sourceName: dstSource.datasourceName,
        sourceDbType: dstSource.dbType,
        tableCount: dstResult == null ? 0 : dstResult.dstTableCount,
        domains: [{ name: '原始库', items: dstResult == null ? [] : dstResult.dst }]
    }
]
