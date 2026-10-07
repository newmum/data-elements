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
  "description": "兼容旧地址，原样转发请求到资产索引单条刷新接口并返回其结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.刷新单个资产索引
// 路由：POST /dst/maintenance/refresh
// 业务说明：兼容旧地址，原样转发请求到资产索引单条刷新接口并返回其结果。
import '@post:/dst/maintenance/batchRefresh' as batchRefresh
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import tenantRuntime
var tenantId = tenantRuntime.id()

// 单资产索引刷新。主表不存在时只删除 ES 残留；存在时委托批量重建逻辑生成一致文档。
var tid = body?.tid
if (tid == null || (tid + '').trim() == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tid不能为空'
}
var type = null
// 从 MySQL 业务表读取当前有效数据。
if (db.table('sym_application_t').where().eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'app'
// 从 MySQL 业务表读取当前有效数据。
} else if (db.table('db_datasource_t').where().eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'db'
// 从 MySQL 业务表读取当前有效数据。
} else if (db.table('db_table_t').where().eq('tid', tid).eq('is_del', 0).selectOne() != null) {
    type = 'table'
} else {
    // 从 MySQL 业务表读取当前有效数据。
    var asset = db.table('da_asset_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
    if (asset != null) {
        type = asset.assetType == null ? asset.asset_type : asset.assetType
    }
}
// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
if (type == null) {
    esCommonService.deleteById('dataassets', tid)
    // 返回当前业务分支的标准结果。
    return { tid: tid, removed: true, refreshed: false }
}
body = { tids: [tid], assetType: [type], purgeStale: false }
var result = batchRefresh()
// 返回当前业务分支的标准结果。
return { tid: tid, assetType: type, removed: false, refreshed: true, result: result }
