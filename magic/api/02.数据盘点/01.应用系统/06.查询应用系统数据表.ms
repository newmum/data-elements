{
  "properties": {},
  "id": "5d64f064de164fe89cc96509b518620e",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "06.查询应用系统数据表",
  "createTime": null,
  "updateTime": 1787153758483,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tables",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"appId\": \"2e83a7abf33140c7a3df27d0c79e98c1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "应用系统 06：查询应用系统关联的数据表；使用请求参数 appId；涉及 db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.查询应用系统数据表
// 路由：POST /dst/application/tables
// 业务说明：应用系统 06：查询应用系统关联的数据表；使用请求参数 appId；涉及 db_datasource_t、db_table_t 等业务表。
import java.util.Map

/*
 * 应用系统 06：查询应用系统关联的数据表。
 *
 * 通过 db_datasource_t.app_id -> db_table_t.datasource_id 直接查询主表，
 * 不读取 da_asset_t 或 da_prop_t。
 */

var blank = (value) => value == null || (value + '').trim() == ''
var first = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
// 解析请求体并为可选参数设置默认值。
var appId = body == null ? null : body.appId
if (blank(appId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'appId不能为空'
}
var dbIds = []
// 从 MySQL 业务表读取当前有效数据。
for (source in db.table('db_datasource_t').where()
    .eq('app_id', appId).eq('is_del', 0).select()) {
    dbIds.add(first(source, ['tid']))
}
if (dbIds.size() == 0) {
    // 返回当前业务分支的标准结果。
    return []
}
var result = []
// 从 MySQL 业务表读取当前有效数据。
for (table in db.table('db_table_t').where()
    .in('datasource_id', dbIds).eq('is_del', 0).select()) {
    var tid = first(table, ['tid'])
    result.add({
        tid: tid,
        id: tid,
        assetType: 'table',
        appId: appId,
        dbId: first(table, ['datasourceId', 'datasource_id']),
        datasourceId: first(table, ['datasourceId', 'datasource_id']),
        tableName: first(table, ['tableName', 'table_name']),
        tableNameEn: first(table, ['tableNameEn', 'table_name_en', 'tableName', 'table_name']),
        tableNameCn: first(table, ['tableNameCn', 'table_name_cn']),
        tableComment: first(table, ['tableComment', 'table_comment']),
        tableType: first(table, ['tableType', 'table_type']),
        businessType: first(table, ['businessType', 'business_type']),
        recordCount: first(table, ['recordCount', 'record_count']),
        fieldCount: first(table, ['fieldCount', 'field_count']),
        storageSize: first(table, ['storageSize', 'storage_size']),
        dataSourceType: first(table, ['dataSourceType', 'data_source_type']),
        annotated: first(table, ['annotated']),
        createdTime: first(table, ['createdTime', 'created_time']),
        updatedTime: first(table, ['updatedTime', 'updated_time'])
    })
}
// 返回当前业务分支的标准结果。
return result
