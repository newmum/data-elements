{
  "properties": {},
  "id": "1f475a245fda4ba4a885b3ac4f8e2d59",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "10.Hive原始库数据表分页接口",
  "path": "/hiveTargetPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "Hive原始库数据表分页（POST /ods/hiveTargetPage）。只读；以OB阶段已物化的目标表为Hive新来源表，只关联Hive阶段新建的任务和目标表；使用请求参数 pageNum、pageSize、keyword、accessStatus、datasourceIds、datasourceId；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.Hive原始库数据表分页接口
// 路由：POST /ods/hiveTargetPage
// 业务说明：Hive原始库数据表分页（POST /ods/hiveTargetPage）。只读；以OB阶段已物化的目标表为Hive新来源表，只关联Hive阶段新建的任务和目标表；使用请求参数 pageNum、pageSize、keyword、accessStatus、datasourceIds、datasourceId；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表。
// Hive 原始库数据表分页：只读。OB 物化目标表在此阶段成为来源表，只返回第二阶段任务与目标表。
import tenantRuntime
import dataScope

var text = (value) => value == null ? '' : value + ''
var blank = (value) => text(value).trim() == ''
var first = (row, keys) => { for (key in keys) { if (row != null && row[key] != null && text(row[key]) != '') { return row[key] } } return null }
var taskStatusText = (value) => {
  var status = text(value)
  if (status == '0') { return '待运行' }
  if (status == '1') { return '运行中' }
  if (status == '2') { return '停止' }
  if (status == '3') { return '执行失败' }
  if (status == '4') { return '执行成功' }
  return blank(status) ? '未配置任务' : status
}
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) { exit 401, '登录状态已失效，请重新登录' }

var pageNum = body?.pageNum == null ? 1 : body.pageNum::int
var pageSize = body?.pageSize == null ? 20 : body.pageSize::int
var keyword = text(body?.keyword).trim().toLowerCase()
var accessStatus = text(body?.accessStatus)
var requestedDatasourceIds = body?.datasourceIds == null ? [] : body.datasourceIds
var requestedDatasourceId = text(body?.datasourceId)
if (!blank(requestedDatasourceId) && !requestedDatasourceIds.contains(requestedDatasourceId)) { requestedDatasourceIds.add(requestedDatasourceId) }

var rows = db.table('db_table_t').where()
  .eq('tenant_id', tenantId).eq('is_del', 0)
  .ne('source_table_id', '').orderBy('updated_time', 'desc').select()
var targetRows = []
var targetIds = []
var sourceIds = []
var datasourceIds = []
for (row in (rows == null ? [] : rows)) {
  var targetId = text(row.tid)
  var sourceId = text(row.sourceTableId || row.source_table_id)
  var datasourceId = text(row.datasourceId || row.datasource_id)
  if (blank(targetId) || blank(sourceId) || targetId == sourceId || blank(datasourceId)) { continue }
  targetRows.add(row); targetIds.add(targetId)
  if (!sourceIds.contains(sourceId)) { sourceIds.add(sourceId) }
  if (!datasourceIds.contains(datasourceId)) { datasourceIds.add(datasourceId) }
}

var sourceTableMap = {}
if (sourceIds.size() > 0) {
  var sourceRows = db.table('db_table_t').where().in('tid', sourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  for (sourceRow in (sourceRows == null ? [] : sourceRows)) { sourceTableMap[text(sourceRow.tid)] = sourceRow }
}
var datasourceMap = {}
if (datasourceIds.size() > 0) {
  var datasourceRows = db.table('db_datasource_t').where().in('tid', datasourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  var visibleRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', datasourceRows == null ? [] : datasourceRows)
  for (datasource in visibleRows) { datasourceMap[text(datasource.tid)] = datasource }
}
var sourceDatasourceIds = []
for (sourceId in sourceIds) {
  var originalSource = sourceTableMap[sourceId]
  if (originalSource != null) {
    var originalDatasourceId = text(originalSource.datasourceId || originalSource.datasource_id)
    if (!blank(originalDatasourceId) && !sourceDatasourceIds.contains(originalDatasourceId)) { sourceDatasourceIds.add(originalDatasourceId) }
  }
}
var sourceDatasourceMap = {}
if (sourceDatasourceIds.size() > 0) {
  var sourceDatasourceRows = db.table('db_datasource_t').where().in('tid', sourceDatasourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  var visibleRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', sourceDatasourceRows == null ? [] : sourceDatasourceRows)
  for (sourceDatasource in visibleRows) { sourceDatasourceMap[text(sourceDatasource.tid)] = sourceDatasource }
}

// Hive 阶段任务以“OB 目标表”为 source_table_id；不能按 target_table_id 查询，否则会继承 OB 阶段任务。
var tasksBySource = {}
if (targetIds.size() > 0) {
  var taskRows = db.table('data_access_agg_task_t').where().in('source_table_id', targetIds).eq('tenant_id', tenantId).eq('is_del', 0).orderBy('updated_time', 'desc').select()
  for (task in (taskRows == null ? [] : taskRows)) {
    var hiveSourceId = text(task.sourceTableId || task.source_table_id)
    if (blank(hiveSourceId)) { continue }
    if (tasksBySource[hiveSourceId] == null) { tasksBySource[hiveSourceId] = [] }
    tasksBySource[hiveSourceId].add(task)
  }
}

// 已经由 Hive 阶段物化出来的下一跳目标表，同样以 source_table_id 关联当前行。
var nextTargetsBySource = {}
var nextTargetIds = []
var nextTargetDatasourceIds = []
if (targetIds.size() > 0) {
  var nextRows = db.table('db_table_t').where().in('source_table_id', targetIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  for (nextTarget in (nextRows == null ? [] : nextRows)) {
    var nextTargetId = text(nextTarget.tid)
    var hiveSourceId = text(nextTarget.sourceTableId || nextTarget.source_table_id)
    if (blank(nextTargetId) || blank(hiveSourceId) || nextTargetId == hiveSourceId) { continue }
    if (nextTargetsBySource[hiveSourceId] == null) { nextTargetsBySource[hiveSourceId] = [] }
    nextTargetsBySource[hiveSourceId].add(nextTarget)
    nextTargetIds.add(nextTargetId)
    var nextDatasourceId = text(nextTarget.datasourceId || nextTarget.datasource_id)
    if (!blank(nextDatasourceId) && !nextTargetDatasourceIds.contains(nextDatasourceId)) { nextTargetDatasourceIds.add(nextDatasourceId) }
  }
}
// 级联任务以真实上一跳表为来源；目标物化登记仍保留原来源事实关联，按任务目标ID一次补齐。
var chainedTargetIds = []
for (sourceId in tasksBySource.keySet()) {
  for (task in tasksBySource[sourceId]) {
    var id = text(task.targetTableId || task.target_table_id)
    if (!blank(id) && !chainedTargetIds.contains(id)) { chainedTargetIds.add(id) }
  }
}
var chainedTargets = chainedTargetIds.size() == 0 ? [] : db.table('db_table_t').where()
  .eq('tenant_id', tenantId).in('tid', chainedTargetIds).eq('is_del', 0).select()
var chainedTargetById = {}
for (target in chainedTargets) { chainedTargetById[text(target.tid)] = target }
for (sourceId in tasksBySource.keySet()) {
  if (nextTargetsBySource[sourceId] == null) { nextTargetsBySource[sourceId] = [] }
  var ownIds = []
  for (target in nextTargetsBySource[sourceId]) { ownIds.add(text(target.tid)) }
  for (task in tasksBySource[sourceId]) {
    var id = text(task.targetTableId || task.target_table_id)
    var target = chainedTargetById[id]
    if (target == null || ownIds.contains(id)) { continue }
    ownIds.add(id); nextTargetsBySource[sourceId].add(target)
    var dbId = text(target.datasourceId || target.datasource_id)
    if (!nextTargetDatasourceIds.contains(dbId)) { nextTargetDatasourceIds.add(dbId) }
  }
}
var nextTargetDatasourceMap = {}
if (nextTargetDatasourceIds.size() > 0) {
  var nextDatasourceRows = db.table('db_datasource_t').where().in('tid', nextTargetDatasourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  var visibleRows = dataScope.visibleDataSourcesFor('MENU:2081000000000000002', nextDatasourceRows == null ? [] : nextDatasourceRows)
  for (nextDatasource in visibleRows) { nextTargetDatasourceMap[text(nextDatasource.tid)] = nextDatasource }
}

var candidates = []
for (target in targetRows) {
  var targetId = text(target.tid)
  var sourceId = text(target.sourceTableId || target.source_table_id)
  var source = sourceTableMap[sourceId]
  var datasourceId = text(target.datasourceId || target.datasource_id)
  var datasource = datasourceMap[datasourceId]
  // 只有来源记录和目标库均存在，才能证明它是可追溯的物化产物。
  if (source == null || datasource == null) { continue }
  var originalDatasourceId = text(source.datasourceId || source.datasource_id)
  if (sourceDatasourceMap[originalDatasourceId] == null) { continue }
  if (requestedDatasourceIds.size() > 0 && !requestedDatasourceIds.contains(originalDatasourceId)) { continue }
  var displayName = text(first(target, ['tableNameCn','table_name_cn','tableComment','table_comment','tableName','table_name']))
  var physicalName = text(first(target, ['tableName','table_name','tableNameEn','table_name_en']))
  var sourceName = text(first(source, ['tableNameCn','table_name_cn','tableComment','table_comment','tableName','table_name']))
  if (!blank(keyword) && !((displayName + ' ' + physicalName + ' ' + sourceName).toLowerCase().contains(keyword))) { continue }
  var taskRows = tasksBySource[targetId] == null ? [] : tasksBySource[targetId]
  if (accessStatus == '0' && taskRows.size() > 0) { continue }
  if (accessStatus == '1' && taskRows.size() == 0) { continue }
  var nextTargetRows = nextTargetsBySource[targetId] == null ? [] : nextTargetsBySource[targetId]
  var targetTables = []
  for (nextTarget in nextTargetRows) {
    var nextDatasourceId = text(nextTarget.datasourceId || nextTarget.datasource_id)
    var nextDatasource = nextTargetDatasourceMap[nextDatasourceId]
    if (nextDatasource == null) { continue }
    targetTables.add({
      targetTableId: text(nextTarget.tid), targetTableName: first(nextTarget, ['tableName','table_name','tableNameEn','table_name_en']),
      targetDbId: nextDatasourceId, targetDbName: nextDatasource == null ? nextDatasourceId : (nextDatasource.dbName || nextDatasource.db_name),
      targetDbType: nextDatasource == null ? '' : (nextDatasource.dbType || nextDatasource.db_type)
    })
  }
  var tasks = []
  for (task in taskRows) { tasks.add({
    tid: task.tid, taskName: task.taskName || task.task_name || '接入任务', sourceTableId: targetId,
    targetTableId: text(task.targetTableId || task.target_table_id), targetTableName: '', targetDbId: '', targetDbName: '', targetDbType: '',
    scheduleCycle: task.scheduleCycle || task.schedule_cycle, lastRunning: task.endRunning || task.end_running || task.lastRunning || task.last_running,
    taskStatus: task.taskStatus || task.task_status, taskStatusText: taskStatusText(task.taskStatus || task.task_status)
  }) }
  for (taskItem in tasks) {
    for (targetTable in targetTables) {
      if (taskItem.targetTableId == targetTable.targetTableId) {
        taskItem.targetTableName = targetTable.targetTableName; taskItem.targetDbId = targetTable.targetDbId
        taskItem.targetDbName = targetTable.targetDbName; taskItem.targetDbType = targetTable.targetDbType
        taskItem.targetTables = [targetTable]
        taskItem.sourceDbName = datasource.dbName || datasource.db_name
      }
    }
  }
  candidates.add({
    tid: targetId, targetTableId: targetTables.size() > 0 ? targetTables[0].targetTableId : '', tableId: targetId,
    hiveCatalogId: nextTargetRows.size() > 0 ? text(nextTargetRows[0].sourceCatalogId || nextTargetRows[0].source_catalog_id) : '',
    tableName: physicalName, tableNameCn: displayName, tableComment: first(target, ['tableComment','table_comment']),
    businessType: first(target, ['businessType','business_type']), tableType: first(target, ['tableType','table_type']),
    datasourceId: datasourceId, dbId: datasourceId, datasourceName: datasource.dbName || datasource.db_name,
    dbName: datasource.dbName || datasource.db_name, targetDbId: datasourceId, targetDbName: datasource.dbName || datasource.db_name,
    targetDbType: datasource.dbType || datasource.db_type, sourceTableId: targetId, previousSourceTableId: sourceId,
    sourceTableName: first(source, ['tableName','table_name','tableNameEn','table_name_en']),
    sourceTableNameCn: sourceName, sourceDatasourceId: originalDatasourceId,
    createdTime: first(target, ['createdTime','created_time']), updatedTime: first(target, ['updatedTime','updated_time']),
    dataCount: first(target, ['recordCount','record_count']), fieldCount: first(target, ['fieldCount','field_count']),
    tasks: tasks, taskCount: tasks.size(), accessTaskId: tasks.size() > 0 ? tasks[0].tid : '',
    accessStatusText: tasks.size() > 0 ? '已接入' : '待接入',
    targetTables: targetTables
  })
}
var total = candidates.size()
var start = (pageNum - 1) * pageSize
var list = []
var itemIndex = 0
for (candidate in candidates) {
  if (itemIndex >= start && list.size() < pageSize) { list.add(candidate) }
  itemIndex = itemIndex + 1
}
return { pageNum: pageNum, pageSize: pageSize, total: total, list: list }
