{
  "properties" : { },
  "id" : "a1000000000000000000000000000014",
  "script" : null,
  "groupId" : "d5ce0d4911e94d7bb3c22a9f67547406",
  "name" : "15.字段选项",
  "createTime" : 1785628800000,
  "updateTime" : 1791111501368,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/options/columns",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"tableId\":\"dq_demo_population_meta\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "POST /dwm/quality/options/columns；只读；查询指定登记数据表字段、主键和索引属性；规则表单选项接口。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：15.字段选项
// 业务说明：POST /dwm/quality/options/columns；只读；单表 tableId 兼容旧调用，tableIds 批量查询最多 50 张登记表的字段、主键和索引属性；规则表单选项接口。

import tenantRuntime
import dataQuality
import db

// 业务租户始终来自当前登录会话。
var tenantId = tenantRuntime.id()
if (body?.tableIds != null) {
    var submittedIds = body.tableIds
    if (!(submittedIds instanceof List) || submittedIds.size() == 0 || submittedIds.size() > 50) {
        exit 400, '数据表标识必须是 1 至 50 项的数组'
    }
    var tableIds = []
    for (rawId in submittedIds) {
        var id = ('' + rawId).trim()
        if (id == '' || id.length() > 32) { exit 400, '数据表标识不合法' }
        if (!tableIds.contains(id)) { tableIds.add(id) }
    }
    var rows = db.select("""
        select c.table_id, c.tid, c.column_name, c.column_comment, c.data_type,
               c.column_type, c.nullable, c.primary_key, c.is_unique,
               c.indexed, c.ordinal_position
          from db_table_column_t c
          join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
          join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
         where c.tenant_id=#{tenantId} and c.is_del=0 and c.table_id in
               <foreach collection="tableIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
         order by c.table_id, c.ordinal_position, c.column_name
    """)
    var result = {}
    for (id in tableIds) { result[id] = [] }
    for (row in rows) {
        var id = '' + row.tableId
        if (result[id] != null) {
            var item = {
                tid: row.tid, column_name: row.columnName, column_comment: row.columnComment,
                data_type: row.dataType, column_type: row.columnType, nullable: row.nullable,
                is_unique: row.isUnique, indexed: row.indexed,
                ordinal_position: row.ordinalPosition
            }
            if (row.primaryKey != null) { item.primary_key = row.primaryKey }
            result[id].add(item)
        }
    }
    return result
}
// 根据已登记数据表读取字段，供规则表单选择。
var tableId = body?.tableId == null ? '' : ('' + body.tableId).trim()
if (tableId == '') { exit 400, '数据表ID不能为空' }
return dataQuality.columns(tableId)
