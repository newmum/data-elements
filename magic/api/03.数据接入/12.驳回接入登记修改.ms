{
  "properties": {},
  "id": "ods_data_agg_reject_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "12.驳回接入登记修改",
  "path": "/dataAggReject",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tableId\":\"来源表ID\",\"datasourceId\":\"数据源ID\"}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"tableId\":\"来源表ID\",\"assetStatus\":0}",
  "description": "接入发现登记信息有误时驳回修改。仅无接入任务的数据表允许驳回；只重置来源表的标注与登记状态，数据源保持已注册，并刷新资产索引；使用请求参数 tableId、datasourceId；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null,
  "script": null
}
================================
// 接口：12.驳回接入登记修改
// 路由：POST /ods/dataAggReject
// 业务说明：接入发现登记信息有误时驳回修改。仅无接入任务的数据表允许驳回；只重置来源表的标注与登记状态，数据源保持已注册，并刷新资产索引；使用请求参数 tableId、datasourceId；涉及 db_table_t、db_datasource_t、data_access_agg_task_t 等业务表；执行数据库写入并返回处理结果。
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import tenantRuntime
import metadataAsset

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var tenantId = tenantRuntime.id()
var nowTime = new Date()
var tableId = body == null ? null : body.tableId
var requestedDatasourceId = body == null ? null : body.datasourceId
if (blank(tableId)) {
    exit 400, '来源表不能为空'
}
// db.table 使用后端命名表租户拦截器，查询与更新天然限定在当前登录租户；
// 请求中不接受 tenantId，避免由前端决定租户范围。
var sourceTable = db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0).selectOne()
if (sourceTable == null) {
    exit 404, '来源表不存在或无访问权限'
}
var datasourceId = sourceTable.datasourceId == null ? sourceTable.datasource_id : sourceTable.datasourceId
if (!blank(requestedDatasourceId) && text(requestedDatasourceId) != text(datasourceId)) {
    exit 400, '来源表与数据源不匹配'
}
var datasource = db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).selectOne()
if (datasource == null) {
    exit 404, '数据源不存在或无访问权限'
}
var taskCount = db.table('data_access_agg_task_t').where()
    .eq('source_table_id', tableId).eq('is_del', 0).count()
if (taskCount > 0) {
    exit 409, '该表已创建接入任务，不能驳回修改'
}
db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0).update({
    asset_status: 0,
    flow_status: 0,
    updated_time: nowTime
})
metadataAsset.refreshRegistrationStatus(datasourceId, false)
body = { tid: tableId }
refreshAssetIndex()
return { tableId: tableId, datasourceId: datasourceId, assetStatus: 0 }
