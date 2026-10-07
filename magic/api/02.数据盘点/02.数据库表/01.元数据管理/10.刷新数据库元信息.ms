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
      "name": "waitSeconds",
      "value": "10",
      "description": "等待表刷新完成的秒数，可选，默认由服务端控制",
      "required": false,
      "dataType": "Integer",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 400,\n    \"msg\": \"参数[waitSeconds]不合法\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "刷新数据库元信息（GET /dst/database/metadata/refreshDbInfo）。读取物理数据库概览，将表数量、视图数量、存储过程数量、用户数量、容量等元数据写入 db_datasource_t 独立字段，并刷新单条数据源 ES 投影；connection extension property 只保留数据库连接信息，不作为元数据存储。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.刷新数据库元信息
// 路由：GET /dst/database/metadata/refreshDbInfo
// 业务说明：刷新数据库元信息（GET /dst/database/metadata/refreshDbInfo）。读取物理数据库概览，将表数量、视图数量、存储过程数量、用户数量、容量等元数据写入 db_datasource_t 独立字段，并刷新单条数据源 ES 投影；connection extension property 只保留数据库连接信息，不作为元数据存储。
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
if (blank(tid)) {
    // 缺少数据源主键时直接终止，避免误更新其他记录。
    exit 400, 'tid 不能为空'
}
// 数据源主表是概览信息的唯一持久化目标。
var datasource = db.table('db_datasource_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
if (datasource == null) {
    exit 404, '数据源不存在'
}
// 从物理数据库读取容量、表、视图、存储过程和用户统计。
var info = queryDatabaseInfo()
// 元数据概览写入独立字段，详情页和 ES 重建均以这些字段为准。
var affected = db.update("""
    UPDATE db_datasource_t
       SET table_num = #{info.tableCount},
           db_version = #{info.dbVersion},
           view_count = #{info.viewCount},
           procedure_count = #{info.procedureCount},
           user_count = #{info.userCount},
           data_size = #{info.dataSize},
           total_size = #{info.totalSize},
           updated_time = CURRENT_TIMESTAMP
     WHERE tid = #{tid}
       AND is_del = 0
""")
if (affected == 0) {
    exit 404, '数据源不存在或已删除'
}
// 主表保存成功后同步刷新单条 ES 投影；索引异常后续可通过批量重建补偿，不回滚主表元数据。
try {
    body = { tid: tid, assetType: 'db' }
    refreshAssetIndex()
} catch (e) {
    // ES 刷新失败不影响元数据主表保存。
}
// 仅返回可直接序列化的标量字段；同时提供前端常用 camelCase 与数据库 snake_case 名称。
return {
    tid: tid,
    dbVersion: info.dbVersion,
    db_version: info.dbVersion,
    dataSize: info.dataSize,
    data_size: info.dataSize,
    tableCount: info.tableCount,
    tableNum: info.tableCount,
    table_num: info.tableCount,
    viewCount: info.viewCount,
    view_count: info.viewCount,
    procedureCount: info.procedureCount,
    procedure_count: info.procedureCount,
    totalSize: info.totalSize,
    total_size: info.totalSize,
    userCount: info.userCount,
    user_count: info.userCount
}
