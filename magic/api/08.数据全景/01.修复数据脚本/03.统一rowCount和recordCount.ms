{
  "properties": {},
  "id": "4fcea3331dc44d808464c76609faa343",
  "script": null,
  "groupId": "ed37113428464a0686dfafbe52429f2f",
  "name": "03.统一rowCount和recordCount",
  "createTime": 1782955343485,
  "updateTime": 1787153756964,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/execDsl_copy",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"result\": []\n    }\n}",
  "description": "统一表资产 rowCount 与 recordCount；涉及 db_table_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.统一rowCount和recordCount
// 路由：GET /scene/fix/execDsl_copy
// 业务说明：统一表资产 rowCount 与 recordCount；涉及 db_table_t 等业务表；执行数据库写入并返回处理结果。
/*
 * 统一表资产 rowCount 与 recordCount
 *
 * db_table_t.record_count 是唯一持久化字段。脚本取 ES 两个历史字段的较大值
 * 回写主表，并重建当前表的 ES 投影字段；不再向 da_prop_t 写 table 属性。
 */
import tenantEs
import '@/common/configData' as configData
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var index = tenantEs.index(configData().es_dataassets)
var response = index.search({
    size: 10000,
    query: {term: {assetType: 'table'}},
    _source: true
})
var changed = []
for (hit in response.hits.hits || []) {
    // 从 MySQL 业务表读取当前有效数据。
    var table = db.table('db_table_t').where().eq('tid', hit._id).eq('is_del', 0).selectOne()
    if (table == null) {
        continue
    }
    var rowCount = hit._source?.rowCount == null ? 0 : hit._source.rowCount::long
    var recordCount = hit._source?.recordCount == null ? 0 : hit._source.recordCount::long
    var count = Math.max(rowCount, recordCount)
    if ((table.recordCount || 0)::long != count) {
        // 从 MySQL 业务表读取当前有效数据。
        db.table('db_table_t').where().eq('tid', hit._id)
            .update({record_count: count, updated_time: new Date()})
    }
    var document = hit._source || {}
    document.tid = hit._id
    document.assetType = 'table'
    document.rowCount = count
    document.recordCount = count
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.saveOrUpdate('dataassets', hit._id + '', jsons.toJSONStr(document), false)
    if (rowCount != count || recordCount != count) {
        changed.add({tid: hit._id, rowCount: count, recordCount: count})
    }
}
// 返回当前业务分支的标准结果。
return {changed: changed.size(), list: changed}
