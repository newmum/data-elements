{
  "properties": {},
  "id": "metadata_tables_delete_asset_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "18.删除已登记表资产",
  "createTime": null,
  "updateTime": 1790152445533,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables/deleteAsset",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"dbId\":\"sourceId\",\"tableIds\":[\"tableId1\",\"tableId2\"],\"dryRun\":true}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"deleted\":true,\"batch\":true,\"dryRun\":true,\"requestedCount\":2,\"deletedCount\":0,\"deletedTableIds\":[],\"failed\":[],\"results\":[{\"deleted\":false,\"dryRun\":true,\"tableName\":\"example\",\"cleanup\":{\"columns\":1,\"catalogs\":0,\"tasks\":0}},{\"deleted\":false,\"dryRun\":true,\"reason\":\"notFound\",\"cleanup\":{\"columns\":0,\"catalogs\":0,\"tasks\":0}}],\"skippedLegacyTables\":[]}",
  "description": "清理当前租户和角色可访问数据源中的登记表及其平台关联。dbId 必填，tableId 或 tableIds 指定最多 100 条记录，dryRun 只预检。单项返回 deleted、dryRun 和 cleanup，批量返回成功 ID、失败项及逐表结果。跨来源表拒绝整批请求。正式操作在同一事务内更新登记及其关联，提交后处理资产搜索索引。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import java.util.ArrayList
import java.util.List
import java.util.Map
import java.util.Date
import java.util.LinkedHashSet
import cn.hutool.json.JSONUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import tenantRuntime
import dataScope
import tableRead
import tableWrite
import identityRuntime
import metadataAsset
import jsons
import log
import '@post:/dst/maintenance/batchRefresh' as refreshAssetIndex

/**
 * POST /dst/database/metadata/tables/deleteAsset 清理当前数据源的登记表及平台关联。
 * dbId 为来源，tableId 或 tableIds 指定最多 100 张表。dryRun 只核对权限并预估影响。
 * 单项返回删除状态和清理统计，批量按请求顺序返回逐项结果及失败表 ID。
 * 数据源和每张表须属于当前登录租户及当前角色范围。登记关系在同一事务内更新，提交后处理索引。
 * 此接口面向平台登记记录和已采集的表快照，操作范围限于平台业务库和资产搜索投影。
 */

var valueText = value => value == null ? '' : (value + '').trim()
var tenantId = tenantRuntime.id()
var datasourceId = valueText(body?.dbId)
var dryRun = body != null && (body.dryRun == true || body.dryRun == 1 || body.dryRun == '1')
if (valueText(tenantId) == '') {
    exit 401, '租户会话已失效，请重新登录'
}
if (datasourceId == '') {
    exit 400, 'dbId 不能为空'
}

// 前端批量选择以当前页为边界。使用首次出现的顺序处理共享目录和字典替代链。
var requestedTableIds = new LinkedHashSet()
var rawTableIds = body?.tableIds
if (rawTableIds != null) {
    if (!(rawTableIds instanceof List) || rawTableIds.size() > 100) {
        exit 400, 'tableIds 必须是最多 100 项的数组'
    }
    for (item in rawTableIds) {
        var id = valueText(item)
        if (id != '') {
            requestedTableIds.add(id)
        }
    }
}
var tableId = valueText(body?.tableId)
if (tableId != '') {
    requestedTableIds.add(tableId)
}
var tableIds = new ArrayList(requestedTableIds)
if (tableIds.isEmpty() || tableIds.size() > 100) {
    exit 400, 'tableId 或 tableIds 必须提供，最多 100 项'
}

var active = {is_del: 0}
var permission = 'MENU:2081000000000000002'
var sourceFields = ['tid', 'org_id', 'created_by']
var tableFields = ['tid', 'datasource_id', 'source_catalog_id', 'table_name', 'business_type', 'is_del']
var scoped = name => db.table(name).where().eq('tenant_id', tenantId).eq('is_del', 0)
var readColumn = (name, kind) => tableRead.column('r', name, kind)
var readText = name => readColumn(name, 'TEXT')
var readActive = name => {
    var query = tableRead.from(name, 'r', tenantId)
    query.where(tableRead.equal(readColumn('is_del', 'NUMBER'), 0))
    return query
}
var textProjection = fields => {
    var projection = {}
    for (field in fields) {
        projection[field] = readText(field)
    }
    return projection
}
var boundedRows = (query, projection) => {
    // 超过上限时整批拒绝，避免按截断的关系集删除目录、任务或字典配置。
    var rows = query.withTimeoutSeconds(30).rows(projection, [projection.tid], ['asc'], 10001)
    if (rows.size() > 10000) {
        exit 409, '关联数据超过本次清理上限，请缩小批次后重试'
    }
    return rows
}
var group = (index, key, value) => {
    if (key == '') {
        return
    }
    if (index[key] == null) {
        index[key] = []
    }
    index[key].add(value)
}

var authorizeSource = source => {
    if (source == null) {
        exit 404, '数据源不存在或无权访问'
    }
    if (!dataScope.canReadDataSourceFor(permission, source)) {
        exit 403, '数据源不存在或无权删除'
    }
}
var sourceQuery = readActive('db_datasource_t')
sourceQuery.where(tableRead.equal(readText('tid'), datasourceId))
var sources = sourceQuery.rows(textProjection(sourceFields), 1)
authorizeSource(sources.isEmpty() ? null : sources[0])

// 先批量校验归属。其他租户的记录与不存在的编号使用同一响应，无法证实来源的旧记录也拒绝写入。
var readRequested = () => {
    var query = tableRead.from('db_table_t', 'r', tenantId)
    query.where(tableRead.in(readText('tid'), tableIds))
    var projection = textProjection(tableFields)
    projection.is_del = readColumn('is_del', 'NUMBER')
    return query.rows(projection, 100)
}
var classifyTables = rows => {
    var tables = {}
    for (row in rows) {
        if (row.is_del == 0 && valueText(row.datasource_id) != datasourceId) {
            exit 400, '数据表不属于当前数据源'
        }
        if (valueText(row.datasource_id) == datasourceId) {
            tables[row.tid] = row
        }
    }
    return tables
}
var requested = classifyTables(readRequested())

// 元数据查询只用于原响应的兼容提示。现行关系统一交由当前业务表处理。
var skippedLegacyTables = []
if (!dryRun && !tableIds.filter(id => requested[id]?.is_del == 0).isEmpty()) {
    for (name in ['da_asset_catalog_item_t', 'da_app_catalog_rela', 'da_asset_api_rela']) {
        if (!identityRuntime.hasTable(name)) {
            skippedLegacyTables.add(name)
        }
    }
}

var parseConfig = raw => {
    if (valueText(raw) == '') {
        return null
    }
    try {
        return raw instanceof Map ? raw : jsons.toJSONObject(valueText(raw))
    } catch (error) {
        return null
    }
}
var normalizedName = value => valueText(value).toLowerCase()
var belongsToDictionary = relation => {
    if (relation == null) {
        return false
    }
    var enabled = normalizedName(relation.enabled)
    var mode = normalizedName(relation.sourceType == null ? relation.mode : relation.sourceType)
    return enabled != 'false' && mode != 'enum' && mode != 'standard' &&
        (valueText(relation.dictionaryTableId) != '' || valueText(relation.dictionaryTable) != '')
}
var emptyDictionaryPlan = () => ({
    affectedTables: 0, affectedFields: 0, migratedFields: 0, invalidatedFields: 0, replacementTableId: null
})
var notFound = () => ({
    deleted: false, dryRun: dryRun, reason: 'notFound', cleanup: {columns: 0, catalogs: 0, tasks: 0}
})

var cleanRegisteredTables = () => {
    if (!dryRun) {
        // 锁定来源与请求记录后重新校验，避免并发改绑或重复提交时按陈旧归属继续写入。
        authorizeSource(tableWrite.lockRow('db_datasource_t', tenantId, 'tid', datasourceId, sourceFields, active))
        requested = classifyTables(tableWrite.lockRows('db_table_t', tenantId, 'tid', tableIds, tableFields, {}))
    }
    var liveIds = tableIds.filter(id => requested[id] != null && requested[id].is_del == 0)
    var knownIds = tableIds.filter(id => requested[id] != null)
    var retryTableIds = knownIds.filter(id => requested[id].is_del == 1)
    var results = {}
    var deletedCatalogs = new LinkedHashSet()
    var refreshedCatalogs = new LinkedHashSet()
    var retryCatalogIds = []
    if (knownIds.isEmpty() || (dryRun && liveIds.isEmpty())) {
        return {results: results, retryTableIds: retryTableIds, retryCatalogIds: retryCatalogIds,
            deletedCatalogs: [], refreshedCatalogs: []}
    }

    var relationNames = ['sourceTableId', 'targetTableId']
    var directCatalogIds = new LinkedHashSet()
    var candidatesByTable = {}
    var referencesByCatalog = {}
    var addCatalogReference = (catalog, id, current) => {
        if (catalog == '' || id == '') {
            return
        }
        if (current && (requested[id] == null || requested[id].is_del == 0)) {
            if (referencesByCatalog[catalog] == null) {
                referencesByCatalog[catalog] = new LinkedHashSet()
            }
            referencesByCatalog[catalog].add(id)
        }
        if (requested[id] != null) {
            if (candidatesByTable[id] == null) {
                candidatesByTable[id] = new LinkedHashSet()
            }
            candidatesByTable[id].add(catalog)
        }
    }
    for (id in dryRun ? liveIds : knownIds) {
        var catalogId = valueText(requested[id].source_catalog_id)
        if (catalogId != '') {
            directCatalogIds.add(catalogId)
            addCatalogReference(catalogId, id, true)
        }
    }

    // 先找兼容属性中的表关系，再合并目录主表的来源、目标和登记表反向引用。
    var linkedProps = tableRead.from('da_prop_t', 'r', tenantId)
    linkedProps.where(tableRead.equal(readText('data_type'), 'catalog'))
    linkedProps.where(tableRead.in(readText('prop_name'), relationNames))
    linkedProps.where(tableRead.in(readColumn('prop_value', 'LOB'), dryRun ? liveIds : knownIds))
    if (dryRun || retryTableIds.isEmpty()) {
        linkedProps.where(tableRead.equal(readColumn('is_del', 'NUMBER'), 0))
    }
    var linkedPropProjection = textProjection(['tid', 'parent_id'])
    linkedPropProjection.prop_value = readColumn('prop_value', 'LOB')
    linkedPropProjection.is_del = readColumn('is_del', 'NUMBER')
    for (prop in boundedRows(linkedProps, linkedPropProjection)) {
        var id = valueText(prop.prop_value)
        var catalogId = valueText(prop.parent_id)
        if (requested[id] != null && catalogId != '') {
            directCatalogIds.add(catalogId)
            addCatalogReference(catalogId, id, prop.is_del == 0)
        }
    }
    var directoryQuery = tableRead.from('da_catalog_t', 'r', tenantId)
    var consideredTableIds = dryRun ? liveIds : knownIds
    directoryQuery.where(tableRead.any([
        tableRead.in(readText('tid'), new ArrayList(directCatalogIds)),
        tableRead.in(readText('source_table_id'), consideredTableIds),
        tableRead.in(readText('target_table_id'), consideredTableIds)
    ]))
    var directoryProjection = textProjection(['tid', 'source_table_id', 'target_table_id'])
    directoryProjection.is_del = readColumn('is_del', 'NUMBER')
    var directories = {}
    var liveCatalogIds = []
    for (directory in boundedRows(directoryQuery, directoryProjection)) {
        var id = directory.tid
        directories[id] = directory
        addCatalogReference(id, valueText(directory.source_table_id), directory.is_del == 0)
        addCatalogReference(id, valueText(directory.target_table_id), directory.is_del == 0)
        var retryProjection = !retryTableIds.filter(table => candidatesByTable[table]?.contains(id) == true).isEmpty()
        if (directory.is_del == 0) {
            liveCatalogIds.add(id)
            if (retryProjection) {
                refreshedCatalogs.add(id)
            }
        } else if (directory.is_del == 1 && retryProjection) {
            retryCatalogIds.add(id)
        }
    }
    if (liveIds.isEmpty()) {
        return {results: results, retryTableIds: retryTableIds, retryCatalogIds: retryCatalogIds,
            deletedCatalogs: [], refreshedCatalogs: new ArrayList(refreshedCatalogs)}
    }

    if (!liveCatalogIds.isEmpty()) {
        // 正式操作对目录用同一批行锁，后续清理仅使用已归入当前租户和有效集合的目录。
        if (!dryRun) {
            if (liveCatalogIds.size() > 1000) {
                exit 409, '关联目录超过本次清理上限，请缩小批次后重试'
            }
            var current = tableWrite.lockRows('da_catalog_t', tenantId, 'tid', liveCatalogIds,
                ['tid', 'source_table_id', 'target_table_id'], active)
            var changed = current.filter(row => row.source_table_id != directories[row.tid].source_table_id ||
                row.target_table_id != directories[row.tid].target_table_id)
            if (current.size() != liveCatalogIds.size() || !changed.isEmpty()) {
                exit 409, '目录关联已变化，请刷新后重试'
            }
        }
        var reverse = readActive('db_table_t')
        reverse.where(tableRead.in(readText('source_catalog_id'), liveCatalogIds))
        for (row in boundedRows(reverse, textProjection(['tid', 'source_catalog_id']))) {
            addCatalogReference(valueText(row.source_catalog_id), row.tid, true)
        }
        var properties = readActive('da_prop_t')
        properties.where(tableRead.in(readText('parent_id'), liveCatalogIds))
        properties.where(tableRead.equal(readText('data_type'), 'catalog'))
        properties.where(tableRead.in(readText('prop_name'), relationNames))
        for (row in boundedRows(properties, linkedPropProjection)) {
            addCatalogReference(valueText(row.parent_id), valueText(row.prop_value), true)
        }
    }

    // 每种依赖只批量读取一次，使用内存索引统计每张表的请求顺序及级联影响。
    var fields = readActive('db_table_column_t')
    fields.where(tableRead.in(readText('table_id'), liveIds))
    var fieldCounts = {}
    for (row in fields.grouped({table_id: readText('table_id'), total: tableRead.count()}, [readText('table_id')])) {
        fieldCounts[row.table_id] = row.total
    }
    var tasks = readActive('data_access_agg_task_t')
    tasks.where(tableRead.any([
        tableRead.in(readText('source_table_id'), liveIds),
        tableRead.in(readText('target_table_id'), liveIds),
        tableRead.in(readText('data_catalog_id'), liveCatalogIds)
    ]))
    var taskProjection = textProjection(['tid', 'source_table_id', 'target_table_id', 'data_catalog_id', 'pipeline_id'])
    var taskRows = boundedRows(tasks, taskProjection)
    var tasksByTable = {}
    var tasksByCatalog = {}
    for (task in taskRows) {
        group(tasksByTable, valueText(task.source_table_id), task.tid)
        group(tasksByTable, valueText(task.target_table_id), task.tid)
        group(tasksByCatalog, valueText(task.data_catalog_id), task.tid)
    }

    var dictionaries = liveIds.filter(id => requested[id].business_type == '字典表')
    var dictionaryRows = []
    var configsByTable = {}
    var originalConfigs = {}
    if (!dictionaries.isEmpty()) {
        var query = readActive('db_table_t')
        query.where(tableRead.equal(readText('datasource_id'), datasourceId))
        var document = readColumn('field_governance_config', 'LOB')
        var needles = new LinkedHashSet(dictionaries)
        var names = new LinkedHashSet()
        for (id in dictionaries) {
            var name = normalizedName(requested[id].table_name)
            if (name != '') {
                names.add(name)
                needles.add(name)
            }
        }
        // 退役 ID 和同名候选先由数据库筛选；业务字段和枚举语义在 MagicScript 中精确校验。
        var normalizedDocument = tableRead.lower(document)
        var tableName = tableRead.trim(readText('table_name'))
        var sameName = tableRead.in(tableRead.lower(tableName), new ArrayList(names))
        if (!dictionaries.filter(id => normalizedName(requested[id].table_name) == '').isEmpty()) {
            sameName = tableRead.any([sameName, tableRead.isNull(tableRead.blankAsNull(tableName))])
        }
        var predicates = [tableRead.all([tableRead.equal(readText('business_type'), '字典表'), sameName])]
        for (needle in needles) {
            predicates.add(tableRead.containsLiteral(normalizedDocument, normalizedName(needle)))
        }
        query.where(tableRead.any(predicates))
        var projection = textProjection(['tid', 'table_name', 'business_type'])
        projection.config = document
        dictionaryRows = boundedRows(query, projection)
        for (row in dictionaryRows) {
            var config = parseConfig(row.config)
            if (config?.fields instanceof List) {
                configsByTable[row.tid] = config
                originalConfigs[row.tid] = valueText(row.config)
            }
        }
    }

    var changedOwners = new LinkedHashSet()
    var removedTables = new LinkedHashSet()
    var removedTasks = new LinkedHashSet()
    var deletedCatalogOwner = {}
    for (id in liveIds) {
        var table = requested[id]
        var catalogsForTable = []
        var tasksForTable = new LinkedHashSet(tasksByTable[id] == null ? [] : tasksByTable[id])
        var candidateIds = candidatesByTable[id] == null ? [] : candidatesByTable[id]
        for (catalogId in candidateIds) {
            if (directories[catalogId]?.is_del != 0 || deletedCatalogs.contains(catalogId)) {
                continue
            }
            var references = referencesByCatalog[catalogId] == null ? [] : new ArrayList(referencesByCatalog[catalogId])
            var remaining = references.filter(other => other != id && !removedTables.contains(other))
            var shared = !remaining.isEmpty()
            if (shared) {
                if (!dryRun && (directories[catalogId].source_table_id == id || directories[catalogId].target_table_id == id)) {
                    refreshedCatalogs.add(catalogId)
                }
                continue
            }
            catalogsForTable.add(catalogId)
            if (tasksByCatalog[catalogId] != null) {
                tasksForTable.addAll(tasksByCatalog[catalogId])
            }
            if (!dryRun) {
                deletedCatalogs.add(catalogId)
                deletedCatalogOwner[catalogId] = id
                refreshedCatalogs.remove(catalogId)
            }
        }
        tasksForTable.removeAll(removedTasks)
        var dictionaryPlan = emptyDictionaryPlan()
        if (table.business_type == '字典表') {
            var name = normalizedName(table.table_name)
            var matches = dictionaryRows.filter(row => row.tid != id && !removedTables.contains(row.tid) &&
                row.business_type == '字典表' && normalizedName(row.table_name) == name)
            var replacement = matches.isEmpty() ? null : matches[0]
            dictionaryPlan.replacementTableId = replacement?.tid
            for (owner in dictionaryRows) {
                var config = configsByTable[owner.tid]
                if (owner.tid == id || removedTables.contains(owner.tid) || config == null) {
                    continue
                }
                var affectedFields = 0
                for (field in config.fields) {
                    var relation = field?.dictionaryRelation == null ? field?.dictionary_relation : field.dictionaryRelation
                    if (!belongsToDictionary(relation)) {
                        continue
                    }
                    var relationId = valueText(relation.dictionaryTableId)
                    if (relationId != id && (relationId != '' || normalizedName(relation.dictionaryTable) != name)) {
                        continue
                    }
                    affectedFields = affectedFields + 1
                    if (!dryRun && replacement != null) {
                        relation.dictionaryTableId = replacement.tid
                        relation.dictionaryTable = valueText(replacement.table_name)
                        relation.invalid = false
                        relation.invalidReason = ''
                        relation.invalidTableId = ''
                        relation.invalidTableName = ''
                    }
                    if (!dryRun && replacement == null) {
                        relation.invalid = true
                        relation.invalidReason = '关联字典已失效，请重新关联'
                        relation.invalidTableId = id
                        relation.invalidTableName = valueText(table.table_name)
                    }
                }
                if (affectedFields > 0) {
                    dictionaryPlan.affectedTables = dictionaryPlan.affectedTables + 1
                    dictionaryPlan.affectedFields = dictionaryPlan.affectedFields + affectedFields
                    changedOwners.add(owner.tid)
                }
            }
            if (replacement == null) {
                dictionaryPlan.invalidatedFields = dictionaryPlan.affectedFields
            } else {
                dictionaryPlan.migratedFields = dictionaryPlan.affectedFields
            }
        }

        var cleanup = {columns: fieldCounts[id] == null ? 0 : fieldCounts[id], catalogs: catalogsForTable.size(),
            tasks: tasksForTable.size(), dictionaryRelations: dictionaryPlan}
        if (dryRun) {
            results[id] = {deleted: false, dryRun: true, tableName: table.table_name, cleanup: cleanup}
            continue
        }
        removedTables.add(id)
        removedTasks.addAll(tasksForTable)
        cleanup.relationRows = 0
        cleanup.propertyRows = 0
        cleanup.skippedLegacyTables = catalogsForTable.isEmpty() ? [] : skippedLegacyTables
        results[id] = {deleted: true, dryRun: false, tableId: id, cleanup: cleanup}
    }
    if (dryRun) {
        return {results: results}
    }

    var removedIds = new ArrayList(removedTables)
    var removedCatalogIds = new ArrayList(deletedCatalogs)
    var removedTaskIds = new ArrayList(removedTasks)
    var impactedParents = new LinkedHashSet(removedIds)
    impactedParents.addAll(removedCatalogIds)
    var parentIds = new ArrayList(impactedParents)

    // 按逻辑归属统计实际被清理的属性和目录关联，避免把同一条关系重复计入逐表结果。
    var propertyQuery = readActive('da_prop_t')
    var propKey = readColumn('prop_value', 'LOB')
    propertyQuery.where(tableRead.any([
        tableRead.in(readText('parent_id'), parentIds),
        tableRead.all([tableRead.equal(readText('data_type'), 'catalog'),
            tableRead.in(readText('prop_name'), relationNames), tableRead.in(propKey, removedIds)])
    ]))
    var propProjection = textProjection(['tid', 'parent_id', 'data_type', 'prop_name'])
    propProjection.prop_value = propKey
    var deletedProps = []
    for (prop in boundedRows(propertyQuery, propProjection)) {
        var parent = valueText(prop.parent_id)
        var referred = valueText(prop.prop_value)
        var owner = removedTables.contains(parent) ? parent : deletedCatalogOwner[parent]
        var relationOwner = prop.data_type == 'catalog' && relationNames.contains(prop.prop_name) &&
            removedTables.contains(referred) ? referred : null
        if (relationOwner != null && parent != relationOwner) {
            results[relationOwner].cleanup.relationRows = results[relationOwner].cleanup.relationRows + 1
        } else if (owner != null) {
            results[owner].cleanup.propertyRows = results[owner].cleanup.propertyRows + 1
        }
        if (relationOwner != null && directories[parent]?.is_del == 0 && !deletedCatalogs.contains(parent)) {
            refreshedCatalogs.add(parent)
        }
        deletedProps.add(prop.tid)
    }
    var relationTables = ['da_catalog_item_t', 'da_catalog_apply_form_rela', 'data_market_shopping_cart_t']
    for (name in relationTables) {
        if (!removedCatalogIds.isEmpty()) {
            var relationQuery = readActive(name)
            var key = readText('catalog_id')
            relationQuery.where(tableRead.in(key, removedCatalogIds))
            for (row in relationQuery.grouped({catalog_id: key, total: tableRead.count()}, [key])) {
                var owner = deletedCatalogOwner[row.catalog_id]
                results[owner].cleanup.relationRows = results[owner].cleanup.relationRows + row.total
            }
        }
    }
    var pipelines = new LinkedHashSet()
    for (task in taskRows) {
        if (removedTasks.contains(task.tid) && valueText(task.pipeline_id) != '') {
            pipelines.add(task.pipeline_id)
        }
    }
    if (!pipelines.isEmpty()) {
        // 多个接入任务可以共用同一画布，仍有运行任务引用时保留画布。
        var remaining = readActive('data_access_agg_task_t')
        remaining.where(tableRead.in(readText('pipeline_id'), new ArrayList(pipelines)))
        for (task in boundedRows(remaining, textProjection(['tid', 'pipeline_id']))) {
            if (!removedTasks.contains(task.tid)) {
                pipelines.remove(task.pipeline_id)
            }
        }
    }

    var now = new Date()
    var deleted = {is_del: 1, updated_time: now}
    var retireBy = (name, key, ids, values) => {
        // 每批键值最多 1000 个，兼容单个 IN 的数据库参数限制。
        var affected = 0
        var offset = 0
        while (offset < ids.size()) {
            var end = Math.min(offset + 1000, ids.size())
            affected = affected + scoped(name).in(key, ids.subList(offset, end)).update(values)
            offset = end
        }
        return affected
    }
    var fieldTotal = 0
    for (count in fieldCounts.values()) {
        fieldTotal = fieldTotal + count
    }
    if (retireBy('db_table_column_t', 'table_id', removedIds, deleted) != fieldTotal ||
        retireBy('da_prop_t', 'tid', deletedProps, deleted) != deletedProps.size()) {
        exit 409, '登记字段或属性已变化，请刷新后重试'
    }
    if (!removedCatalogIds.isEmpty()) {
        for (name in relationTables) {
            retireBy(name, 'catalog_id', removedCatalogIds, deleted)
        }
        if (retireBy('da_catalog_t', 'tid', removedCatalogIds, deleted) != removedCatalogIds.size()) {
            exit 409, '目录状态已变化，请刷新后重试'
        }
    }
    var clearSources = []
    var clearTargets = []
    for (id in refreshedCatalogs) {
        var directory = directories[id]
        if (removedTables.contains(directory.source_table_id)) {
            clearSources.add(id)
        }
        if (removedTables.contains(directory.target_table_id)) {
            clearTargets.add(id)
        }
    }
    // 清除共享目录已经删除的一侧，保留仍有效的一侧及目录数据项。
    if (!clearSources.isEmpty()) {
        scoped('da_catalog_t').in('tid', clearSources).in('source_table_id', removedIds).update({
            source_table_id: null, source_table_name: null, source_db_id: null, source_db_name: null, updated_time: now
        }, true)
    }
    if (!clearTargets.isEmpty()) {
        scoped('da_catalog_t').in('tid', clearTargets).in('target_table_id', removedIds).update({
            target_table_id: null, target_table_name: null, target_db_id: null, target_db_type: null, updated_time: now
        }, true)
    }
    retireBy('data_access_field_mapping', 'task_id', removedTaskIds, deleted)
    if (retireBy('data_access_agg_task_t', 'tid', removedTaskIds,
        {is_del: 1, updated_time: now, task_status: 0}) != removedTaskIds.size()) {
        exit 409, '接入任务状态已变化，请刷新后重试'
    }
    retireBy('nifi_pipeline_t', 'id', new ArrayList(pipelines), {is_del: 1, updated_at: System.currentTimeMillis()})

    // 只锁和写真实受影响的保留表。若治理页面已另行修改配置，整批回滚供用户重试。
    var ownerIds = new ArrayList(changedOwners).filter(id => !removedTables.contains(id))
    var ownerOffset = 0
    while (ownerOffset < ownerIds.size()) {
        var ownerEnd = Math.min(ownerOffset + 1000, ownerIds.size())
        var ownerBatch = ownerIds.subList(ownerOffset, ownerEnd)
        var ownerFilter = {datasource_id: datasourceId, is_del: 0}
        var owners = tableWrite.lockRows('db_table_t', tenantId, 'tid', ownerBatch,
            ['tid', 'field_governance_config'], ownerFilter)
        var changed = owners.filter(row => valueText(row.field_governance_config) != originalConfigs[row.tid])
        if (owners.size() != ownerBatch.size() || !changed.isEmpty()) {
            exit 409, '字典治理配置已变化，请刷新后重试'
        }
        var updates = ownerBatch.map(id => ({
            tid: id, field_governance_config: JSONUtil.toJsonStr(configsByTable[id]), updated_time: now
        }))
        var updated = tableWrite.batchUpdate('db_table_t', tenantId,
            ['field_governance_config', 'updated_time'], ['tid'], ownerFilter, updates)
        if (updated != updates.size()) {
            exit 409, '字典治理配置已变化，请刷新后重试'
        }
        ownerOffset = ownerEnd
    }
    if (scoped('db_table_t').eq('datasource_id', datasourceId).in('tid', removedIds)
        .update(deleted) != removedIds.size()) {
        exit 409, '登记表状态已变化，请刷新后重试'
    }
    metadataAsset.refreshRegistrationStatus(datasourceId, false)
    return {results: results, retryTableIds: retryTableIds, retryCatalogIds: retryCatalogIds,
        deletedCatalogs: removedCatalogIds, refreshedCatalogs: new ArrayList(refreshedCatalogs)}
}

var outcome = dryRun ? cleanRegisteredTables() : db.transaction(cleanRegisteredTables)
var deletedTableIds = []
var results = []
var failed = []
for (id in tableIds) {
    var result = outcome.results[id] == null ? notFound() : outcome.results[id]
    results.add(result)
    if (result.deleted) {
        deletedTableIds.add(id)
    } else if (!dryRun) {
        failed.add({tableId: id, reason: result.reason == null ? 'deleteFailed' : result.reason})
    }
}

var projectionFailures = []
if (!dryRun) {
    // SQL 成功后再处理索引。相同租户和来源的已删行可重试，其他编号不作为投影操作依据。
    var tableProjections = new LinkedHashSet(deletedTableIds)
    tableProjections.addAll(outcome.retryTableIds)
    var catalogProjections = new LinkedHashSet(outcome.deletedCatalogs)
    catalogProjections.addAll(outcome.retryCatalogIds)
    var projectionKeys = new LinkedHashSet(tableProjections)
    projectionKeys.addAll(catalogProjections)
    if (!projectionKeys.isEmpty()) {
        var assetIds = new ArrayList(projectionKeys)
        try {
            if (esCommonService.indexExists('dataassets')) {
                var offset = 0
                while (offset < assetIds.size()) {
                    var end = Math.min(offset + 500, assetIds.size())
                    var keys = assetIds.subList(offset, end)
                    var filter = jsons.toJSONStr({ids: {values: keys}})
                    var found = esCommonService.searchByJsonQuery('dataassets', filter, Map.class, 1, keys.size()).list
                    var ownedIds = []
                    for (document in found) {
                        var id = valueText(document.tid == null ? document.id : document.tid)
                        var type = valueText(document.assetType == null ? document.asset_type : document.assetType)
                        var owner = valueText(document.tenantId == null ? document.tenant_id : document.tenantId)
                        var matches = (type == 'table' && tableProjections.contains(id)) ||
                            (type == 'catalog' && catalogProjections.contains(id))
                        // 旧租户物理索引可存在无租户字段的投影。仍须核对实际编号及资产类型。
                        if (matches && keys.contains(id) && (owner == '' || owner == tenantId)) {
                            ownedIds.add(id)
                        }
                    }
                    if (!ownedIds.isEmpty() && esCommonService.bulkDeleteByIds('dataassets', ownedIds) != ownedIds.size()) {
                        projectionFailures.add({operation: 'delete', assetIds: ownedIds})
                    }
                    offset = end
                }
            }
        } catch (error) {
            projectionFailures.add({operation: 'delete', assetIds: assetIds})
            log.warn('登记表删除已提交，资产索引批量清理失败：{}', error.message)
        }
    }
    if (!outcome.refreshedCatalogs.isEmpty()) {
        var submitted = body
        try {
            var offset = 0
            while (offset < outcome.refreshedCatalogs.size()) {
                var end = Math.min(offset + 200, outcome.refreshedCatalogs.size())
                var ids = outcome.refreshedCatalogs.subList(offset, end)
                body = {assetType: ['catalog'], tids: ids, purgeStale: false}
                var projection = refreshAssetIndex()
                if (projection?.refreshed != ids.size()) {
                    projectionFailures.add({operation: 'refresh', assetIds: ids})
                }
                offset = end
            }
        } catch (error) {
            projectionFailures.add({operation: 'refresh', assetIds: outcome.refreshedCatalogs})
            log.warn('登记表删除已提交，共享目录索引刷新失败：{}', error.message)
        } finally {
            body = submitted
        }
    }
}

if (tableIds.size() == 1) {
    if (!projectionFailures.isEmpty()) {
        results[0].projectionFailures = projectionFailures
    }
    return results[0]
}
var response = {deleted: failed.isEmpty(), batch: true, dryRun: dryRun, requestedCount: tableIds.size(),
    deletedCount: deletedTableIds.size(), deletedTableIds: deletedTableIds, failed: failed,
    results: results, skippedLegacyTables: deletedTableIds.isEmpty() || outcome.deletedCatalogs.isEmpty() ? [] : skippedLegacyTables}
if (!projectionFailures.isEmpty()) {
    response.projectionFailures = projectionFailures
}
return response
