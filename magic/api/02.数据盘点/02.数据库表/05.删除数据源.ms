{
  "properties": {},
  "id": "datapd_database_delete_01",
  "script": null,
  "groupId": "datapd_database_group_01",
  "name": "05.删除数据源",
  "createTime": null,
  "updateTime": 1787153758635,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086644303757058048\",\n  \"dryRun\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"deleted\": false,\n  \"dryRun\": true\n}",
  "description": "删除以租户业务库为事实来源：先软删除数据源及其当前租户内的关联记录，；使用请求参数 tid、dryRun；涉及 db_datasource_t、db_table_t、da_catalog_t、db_table_column_t、data_access_agg_task_t、data_access_field_mapping 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.删除数据源
// 路由：POST /dst/database/delete
// 业务说明：删除以租户业务库为事实来源：先软删除数据源及其当前租户内的关联记录，；使用请求参数 tid、dryRun；涉及 db_datasource_t、db_table_t、da_catalog_t、db_table_column_t、data_access_agg_task_t、data_access_field_mapping 等业务表。
/*
 * 删除数据源。
 *
 * 删除以租户业务库为事实来源：先软删除数据源及其当前租户内的关联记录，
 * 再清理当前租户 Elasticsearch 投影。重复删除仍会执行投影清理，避免
 * “数据库已经删除、列表中的 ES 卡片仍然存在”而无法自愈。
 */
import tenantRuntime
import tenantEs
import '@/common/configData' as configData

var text = value => value == null ? '' : (value + '').trim()
var addUnique = (rows, value) => {
    var normalized = text(value)
    if (normalized != '' && !rows.contains(normalized)) {
        rows.add(normalized)
    }
}
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        if (row[key] != null && text(row[key]) != '') {
            return row[key]
        }
    }
    return null
}

var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '无法识别当前租户'
}

var tid = body == null ? null : body.tid
if (text(tid) == '') {
    exit 400, 'tid is required'
}
tid = text(tid)
var dryRun = body != null && (body.dryRun == true || body.dryRun == 1 || body.dryRun == '1')

var datasourceRow = db.table('db_datasource_t').where()
    .eq('tid', tid)
    .eq('tenant_id', tenantId)
    .selectOne()
var datasource = db.table('db_datasource_t').where()
    .eq('tid', tid)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .selectOne()

var tableRows = db.table('db_table_t').where()
    .eq('datasource_id', tid)
    .eq('tenant_id', tenantId)
    .select()
var activeTableRows = db.table('db_table_t').where()
    .eq('datasource_id', tid)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .select()
var tableIds = []
var catalogIds = []
for (tableRow in tableRows) {
    addUnique(tableIds, rowValue(tableRow, ['tid']))
    addUnique(catalogIds, rowValue(tableRow, ['sourceCatalogId', 'source_catalog_id']))
}

for (catalog in db.table('da_catalog_t').where()
    .eq('db_id', tid)
    .eq('tenant_id', tenantId)
    .select()) {
    addUnique(catalogIds, rowValue(catalog, ['tid']))
}
if (tableIds.size() > 0) {
    for (catalog in db.table('da_catalog_t').where()
        .in('source_table_id', tableIds)
        .eq('tenant_id', tenantId)
        .select()) {
        addUnique(catalogIds, rowValue(catalog, ['tid']))
    }
}

var columnCount = tableIds.size() == 0 ? 0 : db.table('db_table_column_t').where()
    .in('table_id', tableIds)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .count()

var taskIds = []
var pipelineIds = []
var collectTasks = rows => {
    for (task in rows) {
        addUnique(taskIds, rowValue(task, ['tid']))
        addUnique(pipelineIds, rowValue(task, ['pipelineId', 'pipeline_id']))
    }
}
collectTasks(db.table('data_access_agg_task_t').where()
    .eq('source_db_id', tid)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .select())
collectTasks(db.table('data_access_agg_task_t').where()
    .eq('target_db_id', tid)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .select())
if (tableIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where()
        .in('source_table_id', tableIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .select())
    collectTasks(db.table('data_access_agg_task_t').where()
        .in('target_table_id', tableIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .select())
}
if (catalogIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where()
        .in('data_catalog_id', catalogIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .select())
}

var cleanup = {
    tables: activeTableRows.size(),
    columns: columnCount,
    catalogs: catalogIds.size(),
    tasks: taskIds.size()
}
if (dryRun) {
    return {
        deleted: false,
        dryRun: true,
        exists: datasourceRow != null,
        alreadyDeleted: datasource == null,
        dbName: rowValue(datasourceRow, ['dbName', 'db_name']),
        cleanup: cleanup
    }
}

var nowTime = new Date()
if (taskIds.size() > 0) {
    db.table('data_access_field_mapping').where()
        .in('task_id', taskIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    db.table('data_access_task_monitor_snap_t').where()
        .in('task_id', taskIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1 })
    db.table('data_access_agg_task_t').where()
        .in('tid', taskIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, task_status: 0, updated_time: nowTime })
}
if (pipelineIds.size() > 0) {
    db.table('nifi_pipeline_t').where()
        .in('id', pipelineIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_at: System.currentTimeMillis() })
}
if (tableIds.size() > 0) {
    db.table('db_table_column_t').where()
        .in('table_id', tableIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    db.table('db_table_t').where()
        .in('tid', tableIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
}
if (catalogIds.size() > 0) {
    db.table('da_catalog_item_t').where()
        .in('catalog_id', catalogIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    db.table('da_prop_t').where()
        .in('parent_id', catalogIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
    db.table('da_catalog_t').where()
        .in('tid', catalogIds)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, updated_time: nowTime })
}

var changed = 0
if (datasource != null) {
    changed = db.table('db_datasource_t').where()
        .eq('tid', tid)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .update({ is_del: 1, is_enable: 0, updated_time: nowTime })
}

// 必须清理 tenantEs 的物理租户索引。即使主记录早已软删除，也继续执行，
// 让历史残留投影可通过再次点击删除自动修复。
var projectionFailures = []
var projectionMisses = 0
var projectionIds = []
addUnique(projectionIds, tid)
for (tableId in tableIds) {
    addUnique(projectionIds, tableId)
}
for (catalogId in catalogIds) {
    addUnique(projectionIds, catalogId)
}
var dataassetsIndex = tenantEs.index(configData().es_dataassets)
for (assetId in projectionIds) {
    try {
        dataassetsIndex.delete(assetId)
    } catch (e) {
        var projectionError = text(e.message)
        // ES 把删除不存在的文档返回为 404；幂等删除中这是正常结果，不应当作为失败。
        if (projectionError.contains('404 Not Found') || projectionError.contains('"result":"not_found"')) {
            projectionMisses = projectionMisses + 1
        } else {
            projectionFailures.add({ assetId: assetId, error: e.message })
        }
    }
}

return {
    deleted: changed > 0,
    alreadyDeleted: datasource == null,
    dryRun: false,
    cleanup: cleanup,
    projectionMisses: projectionMisses,
    projectionFailures: projectionFailures
}
