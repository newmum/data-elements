{
  "properties": {},
  "id": "7d35c0cb9698421d9c9c408d21eab305",
  "script": null,
  "groupId": "9c4f2b37ec2e4c7f9c6b31cdb4de1201",
  "name": "05.登记字段",
  "createTime": 1788813874211,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/fields",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "祺智资源中心当前租户已登记表字段。只读；涉及 db_table_column_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.登记字段
// 路由：GET /dst/qizhi-resource/fields
// 业务说明：祺智资源中心当前租户已登记表字段。只读；涉及 db_table_column_t、db_table_t 等业务表。
// 祺智资源中心：读取当前租户已登记表字段。
// 读取 db_table_column_t 与 db_table_t；不写入任何表。
// 安全校验：租户仅由 tenantRuntime.id() 提供，物理库由租户路由选择。
import tenantRuntime;
var tenantId = tenantRuntime.id();
try {
  var items = db.select('select c.tid as id, c.table_id as tableId, t.table_name as tableName, t.table_name_cn as tableDisplayName, c.column_name as name, c.column_comment as description, c.data_type as dataType, c.column_type as columnType, c.length as length, c.nullable as nullable, c.primary_key as primaryKey, c.ordinal_position as position from db_table_column_t c left join db_table_t t on t.tid = c.table_id and t.is_del = 0 where c.is_del = 0 order by t.table_name, c.ordinal_position limit 500');
  return {available: true, items: items == null ? [] : items};
} catch (error) {
  return {available: false, items: []};
}
