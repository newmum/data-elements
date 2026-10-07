{
  "properties": {},
  "id": "dwm_standard_landing_list_01",
  "groupId": "dwm_standard_landing_group_01",
  "name": "01.字段标准引用清单",
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{}",
  "description": "只读当前租户已采集字段及 da_metadata_t 标准引用，统计有效已发布标准覆盖率；涉及 db_table_t、db_datasource_t、db_table_column_t、da_metadata_t 等业务表。",
  "script": null
}
================================
// 接口：01.字段标准引用清单
// 路由：POST /dwm/standard/landing/list
// 业务说明：只读当前租户已采集字段及 da_metadata_t 标准引用，统计有效已发布标准覆盖率；涉及 db_table_t、db_datasource_t、db_table_column_t、da_metadata_t 等业务表。
import tenantRuntime
var tenantId = tenantRuntime.id()
// Read-only. No physical datasource probe, registration change, or catalog creation.
var tables = db.select("""
 select t.tid,t.table_name,t.table_name_cn,t.table_comment,d.db_name
 from db_table_t t join db_datasource_t d on d.tid=t.datasource_id
   and d.tenant_id=#{tenantId} and d.is_del=0
 where t.tenant_id=#{tenantId} and t.is_del=0 order by t.table_name,t.tid
""")
var columns = db.select("""
 select c.tid,c.table_id,c.column_name,c.column_comment,c.data_type,c.length,
        c.data_standard_id,m.meta_name,m.meta_code,m.publish_status,m.is_del as standard_deleted
 from db_table_column_t c
 join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 left join da_metadata_t m on m.tid=c.data_standard_id and m.tenant_id=#{tenantId}
 where c.tenant_id=#{tenantId} and c.is_del=0 order by t.table_name,c.ordinal_position,c.tid
""")
var entities = []
var byId = {}
var items = []
var mapped = 0
var bound = 0
for (table in tables) {
 var entity = { id:table.tid, name:table.tableName, displayName:table.tableNameCn || table.tableComment || table.tableName, sourceName:table.dbName, fields:[] }
 entities.add(entity)
 byId[table.tid] = entity
}
for (column in columns) {
 var standardId = column.dataStandardId == null ? null : (column.dataStandardId + '').trim()
 if (standardId == '') { standardId = null }
 var valid = standardId != null && column.metaName != null && column.publishStatus == 1 && column.standardDeleted == 0
 if (standardId != null) { bound = bound + 1 }
 if (valid) { mapped = mapped + 1 }
 var entity = byId[column.tableId]
 if (entity != null) {
  entity.fields.add({id:column.tid,name:column.columnName,displayName:column.columnComment || column.columnName,type:column.dataType,length:column.length,standardId:standardId})
  items.add({id:column.tid,entity_id:column.tableId,field_id:column.tid,mapping_mode:'STANDARD',target_id:standardId,status:standardId == null ? 'UNBOUND' : (valid ? 'CONFIRMED' : 'STALE')})
 }
}
return {entities:entities,items:items,coverage:{totalFields:items.size(),mappedFields:mapped,boundFields:bound,coverage:items.size() == 0 ? 0 : mapped * 100.0 / items.size()}}
