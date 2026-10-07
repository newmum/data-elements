{
  "properties": {},
  "id": "datapd_db_table_delete_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "06.删除数据表",
  "createTime": 1784454007655,
  "updateTime": 1786436400000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"00e4a8e8cc254c708925a853200bf5b9\",\n    \"tid\": \"00e4a8e8cc254c708925a853200bf5b9\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "删除数据表（POST /dst/database/table/delete）。仅删除登记元数据、字段快照和 Elasticsearch 索引，不删除物理业务库中的表；使用请求参数 tableId、tid；涉及 db_table_t、db_table_column_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.删除数据表
// 路由：POST /dst/database/table/delete
// 业务说明：删除数据表（POST /dst/database/table/delete）。仅删除登记元数据、字段快照和 Elasticsearch 索引，不删除物理业务库中的表；使用请求参数 tableId、tid；涉及 db_table_t、db_table_column_t 等业务表。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import log

/*
 * 数据表删除接口：仅删除已登记的元数据资产。
 *
 * 只软删除 db_table_t、db_table_column_t 中的登记元数据并同步 ES 文档；
 * 不操作物理业务数据库，不执行 DROP TABLE，避免误删实际业务数据。
 */

var blank = (value) => value == null || (value + '').trim() == ''

// 兼容 tableId 和 tid 两种入参，优先使用 tableId。
var tableId = body == null ? null : (body.tableId == null ? body.tid : body.tableId)
if (blank(tableId)) {
    exit 400, 'tableId 参数不能为空'
}

// 从控制库读取当前有效的表资产。
var table = db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0).selectOne()
if (table == null) {
    exit 404, '数据表不存在或已删除'
}

var nowText = new Date()

// 先删除字段快照，再删除表资产，避免保留孤儿字段。
var columnDeleted = db.table('db_table_column_t').where()
    .eq('table_id', tableId).eq('is_del', 0).update({ is_del: 1, updated_time: nowText })
var tableDeleted = db.table('db_table_t').where()
    .eq('tid', tableId).eq('is_del', 0).update({ is_del: 1, updated_time: nowText })

try {
    // ES 同步失败不回滚元数据删除，避免索引异常阻塞管理操作。
    esCommonService.delete('dataassets', tableId)
} catch (e) {
    log.warn('删除数据表 ES 文档失败, tid: {}, error: {}', tableId, e.message)
}

return {
    tableId: tableId,
    tableDeleted: tableDeleted,
    columnDeleted: columnDeleted
}
