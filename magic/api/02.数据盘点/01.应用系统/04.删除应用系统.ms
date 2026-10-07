{
  "properties": {},
  "id": "datapd_application_delete_01",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "04.删除应用系统",
  "createTime": null,
  "updateTime": 1787153758406,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2080000000000000000\",\n  \"dryRun\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"deleted\": false,\n  \"dryRun\": true\n}",
  "description": "按应用系统标识查找关联数据源、表、字段、目录及接入任务；dryRun 仅预估清理范围，正式执行时清理关联平台元数据和索引，不删除业务物理表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.删除应用系统
// 路由：POST /dst/application/delete
// 业务说明：按应用系统标识查找关联数据源、表、字段、目录及接入任务；dryRun 仅预估清理范围，正式执行时清理关联平台元数据和索引，不删除业务物理表。
// 删除应用系统：处理 POST /delete 的核心写入或状态变更逻辑。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService

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
var tid = body == null ? null : body.tid
if (text(tid) == '') {
    exit 400, 'tid is required'
}
tid = text(tid)
var dryRun = body != null && (body.dryRun == true || body.dryRun == 1 || body.dryRun == '1')
var application = db.table('sym_application_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
if (application == null) {
    return { deleted: false, dryRun: dryRun, alreadyDeleted: true, cleanup: { datasources: 0, tables: 0, columns: 0, catalogs: 0, tasks: 0 } }
}

var datasourceIds = []
for (datasource in db.table('db_datasource_t').where().eq('app_id', tid).eq('is_del', 0).select()) {
    addUnique(datasourceIds, rowValue(datasource, ['tid']))
}
var tableIds = []
var catalogIds = []
if (datasourceIds.size() > 0) {
    for (tableRow in db.table('db_table_t').where().in('datasource_id', datasourceIds).eq('is_del', 0).select()) {
        addUnique(tableIds, rowValue(tableRow, ['tid']))
        addUnique(catalogIds, rowValue(tableRow, ['sourceCatalogId', 'source_catalog_id']))
    }
    for (catalog in db.table('da_catalog_t').where().in('db_id', datasourceIds).eq('is_del', 0).select()) {
        addUnique(catalogIds, rowValue(catalog, ['tid']))
    }
}
if (tableIds.size() > 0) {
    for (catalog in db.table('da_catalog_t').where().in('source_table_id', tableIds).eq('is_del', 0).select()) {
        addUnique(catalogIds, rowValue(catalog, ['tid']))
    }
}
var columnCount = tableIds.size() == 0 ? 0 : db.table('db_table_column_t').where().in('table_id', tableIds).eq('is_del', 0).count()
var taskIds = []
var pipelineIds = []
var collectTasks = rows => { for (task in rows) { addUnique(taskIds, rowValue(task, ['tid'])); addUnique(pipelineIds, rowValue(task, ['pipelineId', 'pipeline_id'])) } }
if (datasourceIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where().in('source_db_id', datasourceIds).eq('is_del', 0).select())
    collectTasks(db.table('data_access_agg_task_t').where().in('target_db_id', datasourceIds).eq('is_del', 0).select())
}
if (tableIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where().in('source_table_id', tableIds).eq('is_del', 0).select())
    collectTasks(db.table('data_access_agg_task_t').where().in('target_table_id', tableIds).eq('is_del', 0).select())
}
if (catalogIds.size() > 0) {
    collectTasks(db.table('data_access_agg_task_t').where().in('data_catalog_id', catalogIds).eq('is_del', 0).select())
}
if (dryRun) {
    return { deleted: false, dryRun: true, appName: rowValue(application, ['appName', 'app_name']), cleanup: { datasources: datasourceIds.size(), tables: tableIds.size(), columns: columnCount, catalogs: catalogIds.size(), tasks: taskIds.size() } }
}

var nowTime = new Date()
if (taskIds.size() > 0) {
    db.table('data_access_field_mapping').where().in('task_id', taskIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
    db.table('data_access_task_monitor_snap_t').where().in('task_id', taskIds).eq('is_del', 0).update({ is_del: 1 })
    db.table('data_access_agg_task_t').where().in('tid', taskIds).eq('is_del', 0).update({ is_del: 1, task_status: 0, updated_time: nowTime })
}
if (pipelineIds.size() > 0) {
    db.table('nifi_pipeline_t').where().in('id', pipelineIds).eq('is_del', 0).update({ is_del: 1, updated_at: System.currentTimeMillis() })
}
if (tableIds.size() > 0) {
    db.table('db_table_column_t').where().in('table_id', tableIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
    db.table('db_table_t').where().in('tid', tableIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
}
if (catalogIds.size() > 0) {
    db.table('da_catalog_item_t').where().in('catalog_id', catalogIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
    db.table('da_prop_t').where().in('parent_id', catalogIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
    db.table('da_catalog_t').where().in('tid', catalogIds).eq('is_del', 0).update({ is_del: 1, updated_time: nowTime })
}
if (datasourceIds.size() > 0) {
    db.table('db_datasource_t').where().in('tid', datasourceIds).eq('is_del', 0).update({ is_del: 1, is_enable: 0, updated_time: nowTime })
}
var changed = db.table('sym_application_t').where().eq('tid', tid).eq('is_del', 0).update({ is_del: 1, is_use: 0, updated_time: nowTime })
var projectionFailures = []
for (assetId in [tid].concat(datasourceIds).concat(tableIds).concat(catalogIds)) {
    try { esCommonService.deleteById('dataassets', assetId) } catch (e) { projectionFailures.add({ assetId: assetId, error: e.message }) }
}
return { deleted: changed > 0, dryRun: false, cleanup: { datasources: datasourceIds.size(), tables: tableIds.size(), columns: columnCount, catalogs: catalogIds.size(), tasks: taskIds.size() }, projectionFailures: projectionFailures }
