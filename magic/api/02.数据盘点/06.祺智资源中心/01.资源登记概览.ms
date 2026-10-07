{
  "properties": {},
  "id": "7d35c0cb9698421d9c9c408d21eab301",
  "script": null,
  "groupId": "9c4f2b37ec2e4c7f9c6b31cdb4de1201",
  "name": "01.资源登记概览",
  "createTime": 1788813873852,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/summary",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "祺智资源中心当前租户的应用、数据源、数据表和字段登记摘要。只读。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.资源登记概览
// 路由：GET /dst/qizhi-resource/summary
// 业务说明：祺智资源中心当前租户的应用、数据源、数据表和字段登记摘要。只读。
// 祺智资源中心：读取当前租户的登记摘要。
// 读取 sym_application_t、db_datasource_t、db_table_t、db_table_column_t；不写入任何表。
// 安全校验：租户仅由 tenantRuntime.id() 提供。
import tenantRuntime;
var tenantId = tenantRuntime.id();
var activeCount = (tableName) => {
  try {
    var rows = db.select(`select count(1) as total from ${tableName} where is_del = 0`);
    return {available: true, count: rows == null || rows.size() == 0 ? 0 : rows[0].total};
  } catch (error) {
    return {available: false, count: 0};
  }
};
return {
  tenantId: tenantId,
  tenantCode: tenantId + '',
  tenantName: '当前租户',
  metrics: {
    applicationSystems: activeCount('sym_application_t'),
    registeredDataSources: activeCount('db_datasource_t'),
    registeredDataTables: activeCount('db_table_t'),
    registeredFields: activeCount('db_table_column_t')
  }
};
