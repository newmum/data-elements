{
  "properties": {},
  "id": "2b8dd89d130a4738a8aa1d0c8584e401",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "06.Hive原始库组织树接口",
  "path": "/hiveTargetTree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "Hive原始库组织树（POST /ods/hiveTargetTree）。只读；按来源表所属部门、应用和数据源统计OB阶段已物化的目标表，供Hive阶段作为新的来源表使用；涉及 db_table_t、db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.Hive原始库组织树接口
// 路由：POST /ods/hiveTargetTree
// 业务说明：Hive原始库组织树（POST /ods/hiveTargetTree）。只读；按来源表所属部门、应用和数据源统计OB阶段已物化的目标表，供Hive阶段作为新的来源表使用；涉及 db_table_t、db_datasource_t 等业务表。
// Hive 原始库组织树：只读。统计 OB 阶段已物化的目标表；其原始来源的组织、应用和数据源仅用于导航。
import tenantRuntime

var text = (value) => value == null ? '' : value + ''
var blank = (value) => text(value).trim() == ''
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) { exit 401, '登录状态已失效，请重新登录' }

var rows = db.table('db_table_t').where()
  .eq('tenant_id', tenantId).eq('is_del', 0)
  .ne('source_table_id', '').select()
var targetRows = []
var sourceIds = []
for (row in (rows == null ? [] : rows)) {
  var targetId = text(row.tid)
  var sourceId = text(row.sourceTableId || row.source_table_id)
  // source_table_id 指向另一条已登记表，才是物化目标表；排除来源表自身和历史脏数据。
  if (blank(targetId) || blank(sourceId) || targetId == sourceId) { continue }
  targetRows.add(row)
  if (!sourceIds.contains(sourceId)) { sourceIds.add(sourceId) }
}
var sourceTableMap = {}
if (sourceIds.size() > 0) {
  var sourceRows = db.table('db_table_t').where().in('tid', sourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  for (sourceRow in (sourceRows == null ? [] : sourceRows)) { sourceTableMap[text(sourceRow.tid)] = sourceRow }
}

var sourceDatasourceIds = []
for (sourceId in sourceIds) {
  var sourceTable = sourceTableMap[sourceId]
  if (sourceTable != null) {
    var sourceDatasourceId = text(sourceTable.datasourceId || sourceTable.datasource_id)
    if (!blank(sourceDatasourceId) && !sourceDatasourceIds.contains(sourceDatasourceId)) { sourceDatasourceIds.add(sourceDatasourceId) }
  }
}
var sourceDatasourceMap = {}
if (sourceDatasourceIds.size() > 0) {
  var sourceDatasources = db.table('db_datasource_t').where()
    .in('tid', sourceDatasourceIds).eq('tenant_id', tenantId).eq('is_del', 0).select()
  for (sourceDatasource in (sourceDatasources == null ? [] : sourceDatasources)) { sourceDatasourceMap[text(sourceDatasource.tid)] = sourceDatasource }
}

var orgCounts = {}
var appMaps = {}
var total = 0
for (row in targetRows) {
  var sourceId = text(row.sourceTableId || row.source_table_id)
  var sourceTable = sourceTableMap[sourceId]
  if (sourceTable == null) { continue }
  var sourceDatasourceId = text(sourceTable.datasourceId || sourceTable.datasource_id)
  var sourceDatasource = sourceDatasourceMap[sourceDatasourceId]
  // 历史来源数据源可能已被注销；仍保留其已物化目标表，避免左树与总表数量不一致。
  if (blank(sourceDatasourceId)) { sourceDatasourceId = 'history:' + sourceId }
  var orgId = text(sourceTable.orgId || sourceTable.org_id || row.orgId || row.org_id)
  var appId = sourceDatasource == null ? '' : text(sourceDatasource.appId || sourceDatasource.app_id || sourceDatasource.applicationId || sourceDatasource.application_id)
  var appLabel = sourceDatasource == null ? '' : text(sourceDatasource.appName || sourceDatasource.app_name || sourceDatasource.applicationName || sourceDatasource.application_name)
  if (blank(appId)) { appId = 'datasource:' + sourceDatasourceId }
  if (blank(appLabel)) { appLabel = sourceDatasource == null ? '历史来源数据源' : (sourceDatasource.dbName || sourceDatasource.db_name || '未归属应用') }
  orgCounts[orgId] = (orgCounts[orgId] || 0) + 1
  if (appMaps[orgId] == null) { appMaps[orgId] = {} }
  if (appMaps[orgId][appId] == null) { appMaps[orgId][appId] = { label: appLabel, value: appId, count: 0, datasourceMap: {} } }
  var app = appMaps[orgId][appId]
  app.count = app.count + 1
  if (app.datasourceMap[sourceDatasourceId] == null) {
    app.datasourceMap[sourceDatasourceId] = {
      label: sourceDatasource == null ? sourceDatasourceId : (sourceDatasource.dbName || sourceDatasource.db_name || sourceDatasourceId),
      value: sourceDatasourceId, count: 0,
      dbType: sourceDatasource == null ? '' : (sourceDatasource.dbType || sourceDatasource.db_type || ''),
      connectionStatus: sourceDatasource == null ? '' : (sourceDatasource.connectionStatus || sourceDatasource.connection_status || ''),
      showConnect: sourceDatasource == null ? null : (sourceDatasource.showConnect || sourceDatasource.show_connect), children: []
    }
  }
  app.datasourceMap[sourceDatasourceId].count = app.datasourceMap[sourceDatasourceId].count + 1
  total = total + 1
}
var orgApps = {}
for (orgId in appMaps.keySet()) {
  var apps = []
  for (appId in appMaps[orgId].keySet()) {
    var app = appMaps[orgId][appId]
    var datasources = []
    for (sourceDatasourceId in app.datasourceMap.keySet()) { datasources.add(app.datasourceMap[sourceDatasourceId]) }
    apps.add({ label: app.label, value: app.value, count: app.count, datasources: datasources })
  }
  orgApps[orgId] = apps
}
return {
  total: total, orgCounts: orgCounts, orgApps: orgApps
}
