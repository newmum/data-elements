{
  "properties": {},
  "id": "0f92ce81bd7741378f287047353e5a61",
  "script": null,
  "groupId": "ed37113428464a0686dfafbe52429f2f",
  "name": "01.同步删除es与库数据",
  "createTime": 1782955343467,
  "updateTime": 1784437771640,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/syncEsDbData",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"esTotal\": 6527,\n        \"dbTotal\": 6527,\n        \"deleted\": 0,\n        \"message\": \"数据一致，无需删除\"\n    }\n}",
  "description": "清理 ES 中已经没有数据库主记录的资产。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.同步删除es与库数据
// 路由：GET /scene/fix/syncEsDbData
// 业务说明：清理 ES 中已经没有数据库主记录的资产。
/*
 * 清理 ES 中已经没有数据库主记录的资产
 *
 * 数据库有效 ID 来自应用、数据源、数据表和通用资产四张主表。脚本只删除
 * ES 孤立文档；数据库存在但 ES 缺失的 ID 会返回给调用方，由盘点维护的
 * batchRefresh 接口统一重建，避免在修复脚本中复制索引组装逻辑。
 */
import tenantEs
import '@/common/configData' as configData
import java.util.HashSet

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var index = tenantEs.index(configData().es_dataassets)
var response = index.search({
    size: 10000,
    query: {match_all: {}},
    _source: false
})
var esIds = new HashSet()
for (hit in response.hits.hits || []) {
    esIds.add(hit._id + '')
}

var dbIds = new HashSet()
for (tableName in ['sym_application_t', 'db_datasource_t', 'db_table_t', 'da_asset_t']) {
    // 从 MySQL 业务表读取当前有效数据。
    for (row in db.table(tableName).columns('tid').where().eq('is_del', 0).select()) {
        dbIds.add(row.tid + '')
    }
}

var deletedIds = []
for (id in esIds) {
    if (!dbIds.contains(id)) {
        index.deleteById(id)
        deletedIds.add(id)
    }
}
var dbOnlyIds = []
for (id in dbIds) {
    if (!esIds.contains(id)) {
        dbOnlyIds.add(id)
    }
}

// 返回当前业务分支的标准结果。
return {
    esTotal: esIds.size(),
    dbTotal: dbIds.size(),
    deleted: deletedIds.size(),
    deletedIds: deletedIds,
    dbOnlyCount: dbOnlyIds.size(),
    dbOnlyIds: dbOnlyIds
}
