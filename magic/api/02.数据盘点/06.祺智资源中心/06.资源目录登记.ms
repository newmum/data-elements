{
  "properties": {},
  "id": "7d35c0cb9698421d9c9c408d21eab306",
  "script": null,
  "groupId": "9c4f2b37ec2e4c7f9c6b31cdb4de1201",
  "name": "06.资源目录登记",
  "createTime": 1788813874260,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/catalogs",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "祺智资源中心当前租户已登记资源目录。只读；涉及 da_catalog_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.资源目录登记
// 路由：GET /dst/qizhi-resource/catalogs
// 业务说明：祺智资源中心当前租户已登记资源目录。只读；涉及 da_catalog_t 等业务表。
// 祺智资源中心：读取当前租户已登记资源目录。
// 读取 da_catalog_t；不写入、不审核、不发布目录。
// 安全校验：租户仅由 tenantRuntime.id() 提供，物理库由租户路由选择。
import tenantRuntime;
var tenantId = tenantRuntime.id();
try {
  var items = db.select('select tid as id, catalog_name as name, catalog_name_en as code, asset_desc as description, app_name as applicationName, db_name as databaseName, source_table_id as sourceTableId, source_table_name as sourceTableName, business_type as businessType, data_source_type as dataSourceType, asset_status as assetStatus, flow_status as flowStatus, updated_time as updatedTime from da_catalog_t where is_del = 0 order by updated_time desc limit 100');
  return {available: true, items: items == null ? [] : items};
} catch (error) {
  return {available: false, items: []};
}
