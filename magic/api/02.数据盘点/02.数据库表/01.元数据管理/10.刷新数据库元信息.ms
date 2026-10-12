{
  "properties": {},
  "id": "metadata_refresh_db_info_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "10.刷新数据库元信息",
  "createTime": 1783639496542,
  "updateTime": 1787156377648,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refreshDbInfo",
  "method": "GET",
  "parameters": [
    {
      "name": "tid",
      "value": "8b0a98de552b4bc68a203293fadfff33",
      "description": "数据库资产ID",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tableId",
      "description": "可选：只刷新指定表；tid 必须是该表所属数据源 ID",
      "required": false,
      "dataType": "String"
    },
    {
      "name": "dryRun",
      "description": "true 时仅探查，不写入元数据和搜索索引",
      "required": false,
      "dataType": "String"
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户和角色范围刷新元数据概览。默认更新 db_datasource_t；传 tableId 更新当前 db_table_t 的统计字段，不修改字段治理和来源业务数据。支持 dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.刷新数据库元信息
// 路由：GET /dst/database/metadata/refreshDbInfo
// 业务说明：刷新数据库元信息（GET /dst/database/metadata/refreshDbInfo）。读取物理数据库概览，将表数量、视图数量、存储过程数量、用户数量、容量等元数据写入 db_datasource_t 独立字段，并刷新单条数据源 ES 投影；connection extension property 只保留数据库连接信息，不作为元数据存储。
import tenantRuntime
import '@get:/dst/database/metadata/dbInfo' as queryDatabaseInfo
import '@post:/dst/maintenance/refresh' as refreshAssetIndex

/*
 * 元数据管理 10：刷新并持久化数据库概览。
 *
 * 实时概览来自 09，落库到 db_datasource_t 的物理概览字段；
 * 连接扩展属性仅保存连接参数，不能再写容量、视图数、过程数等元数据。
 * 本接口只刷新数据源概览，不写 da_prop_t，也不隐式登记数据表。
 */

var blank = (value) => value == null || (value + '').trim() == ''
if (blank(tid)) { exit 400, 'tid 不能为空' }
var currentTenantId = tenantRuntime.id()
if (blank(currentTenantId)) { exit 401, '当前登录租户无效，请重新登录' }
// 09 validates the tenant, role datasource scope and table/datasource association before probing.
var info = queryDatabaseInfo()
if (dryRun == true || dryRun == 'true') { return info }
var requestedTableId = tableId
if (!blank(requestedTableId)) {
    var affected = db.update("""
        UPDATE db_table_t
           SET record_count = #{info.recordCount}, field_count = #{info.fieldCount},
               storage_size = #{info.storageSize}, total_size_formatted = #{info.totalSizeFormatted},
               total_size_bytes = #{info.totalSizeBytes}, ddl_create_time = #{info.ddlCreateTime},
               ddl_update_time = #{info.ddlUpdateTime}, updated_time = CURRENT_TIMESTAMP
         WHERE tid = #{requestedTableId} AND datasource_id = #{tid}
           AND tenant_id = #{currentTenantId} AND is_del = 0
    """)
    if (affected == 0) { exit 404, '数据表不存在或已删除' }
    try {
        body = { tid: requestedTableId, assetType: 'table' }
        refreshAssetIndex()
    } catch (e) { /* The database summary remains authoritative when the search index is unavailable. */ }
    return info
}
var affected = db.update("""
    UPDATE db_datasource_t
       SET table_num = #{info.tableCount}, db_version = #{info.dbVersion},
           view_count = #{info.viewCount}, procedure_count = #{info.procedureCount},
           user_count = #{info.userCount}, data_size = #{info.dataSize},
           total_size = #{info.totalSize}, updated_time = CURRENT_TIMESTAMP
     WHERE tid = #{tid} AND tenant_id = #{currentTenantId} AND is_del = 0
""")
if (affected == 0) { exit 404, '数据源不存在或已删除' }
try {
    body = { tid: tid, assetType: 'db' }
    refreshAssetIndex()
} catch (e) { /* Index refresh can be retried; the committed summary is retained. */ }
return {
    tid: tid, dbVersion: info.dbVersion, db_version: info.dbVersion,
    dataSize: info.dataSize, data_size: info.dataSize,
    tableCount: info.tableCount, tableNum: info.tableCount, table_num: info.tableCount,
    viewCount: info.viewCount, view_count: info.viewCount,
    procedureCount: info.procedureCount, procedure_count: info.procedureCount,
    totalSize: info.totalSize, total_size: info.totalSize,
    userCount: info.userCount, user_count: info.userCount
}
