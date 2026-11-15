{
  "properties": {},
  "id": "datapd_asset_refresh_01",
  "script": null,
  "groupId": "datapd_maintenance_group_01",
  "name": "01.刷新单个资产索引",
  "createTime": null,
  "updateTime": 1790434056285,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refresh",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"2e83a7abf33140c7a3df27d0c79e98c1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"refreshed\":true}",
  "description": "按 tid 核对当前租户有效主记录并委托定向重建；目录直接读取主表，不存在时仅移除当前租户残留索引。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@post:/dst/maintenance/batchRefresh' as batchRefresh
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import tenantRuntime

// POST /dst/maintenance/refresh：按 tid 刷新当前租户的应用、数据源、数据表或目录投影。
// 主记录不存在时移除当前租户残留投影；存在时委托有界批量重建，返回刷新结果。
var tenantId = tenantRuntime.id()
var tid = body?.tid == null ? '' : (body.tid + '').trim()
if (tid == '') { exit 400, 'tid不能为空' }
var type = null
// 按各业务主表核对当前租户的有效记录，确定要重建的投影类型。
if (db.table('sym_application_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'app'
} else if (db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'db'
} else if (db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'table'
} else if (db.table('da_catalog_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'catalog'
}
if (type == null) {
    esCommonService.deleteById('dataassets', tid)
    return {tid: tid, removed: true, refreshed: false}
}
var submitted = body
var result = null
try {
    body = {tids: [tid], assetType: [type], purgeStale: false}
    result = batchRefresh()
} finally {
    body = submitted
}
return {tid: tid, assetType: type, removed: false, refreshed: true, result: result}
