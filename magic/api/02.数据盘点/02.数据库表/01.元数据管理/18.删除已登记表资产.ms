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
  "requestBody": "{\"dbId\":\"6b1bbbcf1e8b448fb9c072a4f07d6a7b\",\"tableIds\":[\"2903feee5161484cac3c8b0b2894bedc\"],\"dryRun\":true}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"deleted\":true,\"batch\":true,\"requestedCount\":1,\"deletedCount\":1,\"deletedTableIds\":[],\"failed\":[]}",
  "description": "删除已失效表资产（POST /dst/database/metadata/tables/deleteAsset）。支持 tableId 或 tableIds 一次批量级联清理；兼容历史租户缺少的非核心目录关联表；不删除物理表，dryRun=true 仅统计；涉及 db_table_t、da_prop_t、data_access_agg_task_t、db_table_column_t、da_asset_catalog_item_t、da_app_catalog_rela 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：18.删除已登记表资产
// 路由：POST /dst/database/metadata/tables/deleteAsset
// 业务说明：删除已失效表资产（POST /dst/database/metadata/tables/deleteAsset）。支持 tableId 或 tableIds 一次批量级联清理；兼容历史租户缺少的非核心目录关联表；不删除物理表，dryRun=true 仅统计；涉及 db_table_t、da_prop_t、data_access_agg_task_t、db_table_column_t、da_asset_catalog_item_t、da_app_catalog_rela 等业务表。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import cn.hutool.json.JSONUtil
import java.util.Map
import tenantRuntime

/*
 * 元数据管理 18：级联删除已登记但物理库已不存在的数据表资产。
 *
 * 只清理平台登记资产。共享目录仍被其他表引用时仅解除当前表关系，
 * 不会误删共享目录，也绝不连接业务库执行 DROP TABLE。
 */

var blank = value => value == null || (value + '').trim() == ''
var datasourceId = body == null ? null : body.dbId
var tableId = body == null ? null : body.tableId
var dryRun = body != null && (body.dryRun == true || body.dryRun == 1 || body.dryRun == '1')
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '租户会话已失效，请重新登录'
}
if (blank(datasourceId)) {
    exit 400, 'dbId 不能为空'
}
datasourceId = (datasourceId + '').trim()
tableId = blank(tableId) ? null : (tableId + '').trim()

var textOf = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        if (row[key] != null && textOf(row[key]) != '') {
            return row[key]
        }
    }
    return null
}
var addUnique = (list, value) => {
    var normalized = textOf(value)
    if (normalized != '' && list.filter(item => textOf(item) == normalized).size() == 0) {
        list.add(normalized)
    }
}

// 支持单表和批量删除：批量请求只提交一次，由服务端统一执行清理。
var tableIds = []
var rawTableIds = body == null ? null : body.tableIds
if (rawTableIds != null) {
    for (candidateId in rawTableIds) {
        addUnique(tableIds, candidateId)
    }
}
addUnique(tableIds, tableId)
if (tableIds.size() == 0) {
    exit 400, 'tableId 或 tableIds 不能为空'
}
tableId = tableIds[0]

// 历史租户可能尚未创建部分目录关联表。缺表时跳过该非核心关联清理，
// 不能阻断 db_table_t、字段与目录属性的正常删除。
var skippedLegacyTables = []
var safeUpdate = (tableName, operation) => {
    try {
        return operation()
    } catch (e) {
        var message = textOf(e).toLowerCase()
        if (message.indexOf("doesn't exist") >= 0 || message.indexOf('table not found') >= 0 || message.indexOf('不存在') >= 0) {
            addUnique(skippedLegacyTables, tableName)
            return 0
        }
        throw e
    }
}
var table = null

// Reconcile dictionary relations before/after a dictionary table asset is retired.
// A same-name active dictionary is treated as the replacement; otherwise the
// relation remains visible but is explicitly marked invalid for Step 4 repair.
var parseGovernanceConfig = (value) => {
    if (value == null || textOf(value) == '') { return null }
    try { return value instanceof Map ? value : jsons.toJSONObject(textOf(value)) } catch (e) { return null }
}
var isDictionaryRelation = (relation) => {
    if (relation == null) { return false }
    var enabled = textOf(relation.enabled).toLowerCase()
    var sourceType = textOf(relation.sourceType == null ? relation.mode : relation.sourceType).toLowerCase()
    return enabled != 'false' && sourceType != 'enum' && sourceType != 'standard'
        && (textOf(relation.dictionaryTableId) != '' || textOf(relation.dictionaryTable) != '')
}
var reconcileRetiredDictionaryRelations = (applyChanges) => {
    var result = { affectedTables: 0, affectedFields: 0, migratedFields: 0, invalidatedFields: 0, replacementTableId: null }
    var businessType = textOf(rowValue(table, ['businessType', 'business_type']))
    if (businessType != '字典表') { return result }
    var retiredName = textOf(rowValue(table, ['tableName', 'table_name'])).toLowerCase()
    var activeRows = db.table('db_table_t').where().eq('datasource_id', datasourceId).eq('is_del', 0).select()
    var replacement = null
    for (candidate in activeRows) {
        var candidateId = textOf(rowValue(candidate, ['tid']))
        var candidateName = textOf(rowValue(candidate, ['tableName', 'table_name'])).toLowerCase()
        var candidateType = textOf(rowValue(candidate, ['businessType', 'business_type']))
        if (candidateId != tableId && candidateType == '字典表' && candidateName == retiredName) {
            replacement = candidate
            break
        }
    }
    var replacementId = replacement == null ? '' : textOf(rowValue(replacement, ['tid']))
    var replacementName = replacement == null ? '' : textOf(rowValue(replacement, ['tableName', 'table_name']))
    result.replacementTableId = replacementId == '' ? null : replacementId
    for (owner in activeRows) {
        var ownerId = textOf(rowValue(owner, ['tid']))
        if (ownerId == tableId) { continue }
        var config = parseGovernanceConfig(rowValue(owner, ['fieldGovernanceConfig', 'field_governance_config']))
        if (config == null || config.fields == null) { continue }
        var tableChanged = false
        for (field in config.fields) {
            var relation = field.dictionaryRelation == null ? field.dictionary_relation : field.dictionaryRelation
            if (!isDictionaryRelation(relation)) { continue }
            var relationId = textOf(relation.dictionaryTableId)
            var relationName = textOf(relation.dictionaryTable).toLowerCase()
            if (relationId != tableId && !(relationId == '' && relationName == retiredName)) { continue }
            result.affectedFields = result.affectedFields + 1
            tableChanged = true
            if (replacementId != '') {
                relation.dictionaryTableId = replacementId
                relation.dictionaryTable = replacementName
                relation.invalid = false
                relation.invalidReason = ''
                relation.invalidTableId = ''
                relation.invalidTableName = ''
                result.migratedFields = result.migratedFields + 1
            } else {
                relation.invalid = true
                relation.invalidReason = '关联字典已失效，请重新关联'
                relation.invalidTableId = tableId
                relation.invalidTableName = textOf(rowValue(table, ['tableName', 'table_name']))
                result.invalidatedFields = result.invalidatedFields + 1
            }
        }
        if (tableChanged) {
            result.affectedTables = result.affectedTables + 1
            if (applyChanges) {
                db.table('db_table_t').where().eq('tid', ownerId).eq('is_del', 0)
                    // Preserve nullable historical fields without exposing Hutool
                    // JSONNull to the platform Jackson serializer.
                    .update({ field_governance_config: JSONUtil.toJsonStr(config), updated_time: new Date() })
            }
        }
    }
    return result
}

var deleteOne = itemId => {
    tableId = textOf(itemId)
    table = db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0).selectOne()
if (table == null) {
    return { deleted: false, dryRun: dryRun, reason: 'notFound', cleanup: { columns: 0, catalogs: 0, tasks: 0 } }
}
if (!blank(rowValue(table, ['datasourceId', 'datasource_id'])) && textOf(rowValue(table, ['datasourceId', 'datasource_id'])) != datasourceId) {
    exit 400, '数据表不属于当前数据源'
}

// 收集表直接关联的目录，再识别哪些目录为该表独占，避免误删共享目录。
var candidateCatalogIds = []
addUnique(candidateCatalogIds, rowValue(table, ['sourceCatalogId', 'source_catalog_id']))
var catalogTableProps = db.table('da_prop_t').where()
    .eq('data_type', 'catalog')
    .in('prop_name', ['sourceTableId', 'targetTableId'])
    .eq('prop_value', tableId)
    .eq('is_del', 0)
    .select()
for (prop in catalogTableProps) {
    addUnique(candidateCatalogIds, rowValue(prop, ['parentId', 'parent_id']))
}

var catalogIds = []
for (candidateCatalogId in candidateCatalogIds) {
    var shared = false
    var relationProps = db.table('da_prop_t').where()
        .eq('parent_id', candidateCatalogId)
        .eq('data_type', 'catalog')
        .in('prop_name', ['sourceTableId', 'targetTableId'])
        .eq('is_del', 0)
        .select()
    for (prop in relationProps) {
        if (textOf(rowValue(prop, ['propValue', 'prop_value'])) != tableId) {
            shared = true
        }
    }
    if (!shared) {
        addUnique(catalogIds, candidateCatalogId)
    }
}

// 表及其专属目录关联的任务无法继续运行，需要一并清理。
var taskIds = []
var pipelineIds = []
var collectTasks = rows => {
    for (task in rows) {
        addUnique(taskIds, rowValue(task, ['tid']))
        addUnique(pipelineIds, rowValue(task, ['pipelineId', 'pipeline_id']))
    }
}
collectTasks(db.table('data_access_agg_task_t').where().eq('source_table_id', tableId).eq('is_del', 0).select())
collectTasks(db.table('data_access_agg_task_t').where().eq('target_table_id', tableId).eq('is_del', 0).select())
if (catalogIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where().in('data_catalog_id', catalogIds).eq('is_del', 0).select())
}

var columnCount = db.table('db_table_column_t').where().eq('table_id', tableId).eq('is_del', 0).count()
var dictionaryRelationPlan = reconcileRetiredDictionaryRelations(false)
if (dryRun) {
    return {
        deleted: false,
        dryRun: true,
        tableName: rowValue(table, ['tableName', 'table_name']),
        cleanup: { columns: columnCount, catalogs: catalogIds.size(), tasks: taskIds.size(), dictionaryRelations: dictionaryRelationPlan }
    }
}

var nowTime = new Date()
var cleanup = { columns: columnCount, catalogs: catalogIds.size(), tasks: taskIds.size(), relationRows: 0, propertyRows: 0, skippedLegacyTables: skippedLegacyTables }

// 先删字段、属性和所有指向当前表的关系。
cleanup.columns = db.table('db_table_column_t').where().eq('table_id', tableId).eq('is_del', 0)
    .update({ is_del: 1, updated_time: nowTime })
cleanup.propertyRows = cleanup.propertyRows + db.table('da_prop_t').where().eq('parent_id', tableId).eq('is_del', 0)
    .update({ is_del: 1, updated_time: nowTime })
cleanup.relationRows = cleanup.relationRows + db.table('da_prop_t').where()
    .eq('data_type', 'catalog')
    .in('prop_name', ['sourceTableId', 'targetTableId'])
    .eq('prop_value', tableId)
    .eq('is_del', 0)
    .update({ is_del: 1, updated_time: nowTime })

// 仅删除没有其他表引用的专属目录及其下游关系。
if (catalogIds.size() > 0) {
    cleanup.relationRows = cleanup.relationRows + safeUpdate('da_asset_catalog_item_t', () =>
        db.table('da_asset_catalog_item_t').where().in('catalog_id', catalogIds).eq('is_del', 0)
            .update({ is_del: 1, updated_time: nowTime })
    )
    cleanup.relationRows = cleanup.relationRows + safeUpdate('da_app_catalog_rela', () =>
        db.table('da_app_catalog_rela').where().in('data_catalog_id', catalogIds).eq('is_del', 0)
            .update({ is_del: 1, updated_time: nowTime })
    )
    cleanup.relationRows = cleanup.relationRows + safeUpdate('da_asset_api_rela', () =>
        db.table('da_asset_api_rela').where().in('catalog_id', catalogIds).eq('is_del', 0)
            .update({ is_del: 1, updated_time: nowTime })
    )
    cleanup.relationRows = cleanup.relationRows + db.table('data_apply_form_catalog_rela').where().in('catalog_id', catalogIds).eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    cleanup.relationRows = cleanup.relationRows + db.table('data_market_shopping_cart_t').where().in('catalog_id', catalogIds).eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    cleanup.propertyRows = cleanup.propertyRows + db.table('da_prop_t').where().in('parent_id', catalogIds).eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    safeUpdate('da_asset_t', () =>
        db.table('da_asset_t').where().in('tid', catalogIds).eq('asset_type', 'catalog').eq('is_del', 0)
            .update({ is_del: 1, updated_time: nowTime })
    )
}

if (taskIds.size() > 0) {
    db.table('data_access_field_mapping').where().in('task_id', taskIds).eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    db.table('data_access_task_monitor_snap_t').where().in('task_id', taskIds).eq('is_del', 0)
        .update({ is_del: 1 })
    db.table('data_access_agg_task_t').where().in('tid', taskIds).eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime, task_status: 0 })
}
if (pipelineIds.size() > 0) {
    db.table('nifi_pipeline_t').where().in('id', pipelineIds).eq('is_del', 0)
        .update({ is_del: 1, updated_at: System.currentTimeMillis() })
}

var tableDeleted = db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0)
    .update({ is_del: 1, updated_time: nowTime })
cleanup.dictionaryRelations = tableDeleted > 0 ? reconcileRetiredDictionaryRelations(true) : dictionaryRelationPlan

// 删除 ES 投影，确保资源列表不再命中已删除表和专属目录。
if (tableDeleted > 0) {
    esCommonService.deleteById('dataassets', tableId)
}
for (catalogId in catalogIds) {
    esCommonService.deleteById('dataassets', catalogId)
}

return { deleted: tableDeleted > 0, dryRun: false, tableId: tableId, cleanup: cleanup }
}

var results = []
var deletedTableIds = []
var failed = []
for (requestedTableId in tableIds) {
    if (tableIds.size() == 1) {
        results.add(deleteOne(requestedTableId))
        continue
    }
    try {
        var result = deleteOne(requestedTableId)
        results.add(result)
        if (dryRun) {
            continue
        }
        if (result != null && result.deleted == true) {
            addUnique(deletedTableIds, requestedTableId)
        } else {
            failed.add({ tableId: requestedTableId, reason: result == null ? 'deleteFailed' : (result.reason || 'deleteFailed') })
        }
    } catch (e) {
        failed.add({ tableId: requestedTableId, reason: textOf(e) })
    }
}
if (tableIds.size() == 1) {
    return results[0]
}
return {
    deleted: failed.size() == 0,
    batch: true,
    dryRun: dryRun,
    requestedCount: tableIds.size(),
    deletedCount: deletedTableIds.size(),
    deletedTableIds: deletedTableIds,
    failed: failed,
    results: results,
    skippedLegacyTables: skippedLegacyTables
}
