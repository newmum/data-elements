{
  "properties": {},
  "id": "7d35c0cb9698421d9c9c408d21eab302",
  "script": null,
  "groupId": "9c4f2b37ec2e4c7f9c6b31cdb4de1201",
  "name": "02.应用系统登记",
  "createTime": 1788813874034,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applications",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "祺智资源中心当前租户已登记应用系统。只读；涉及 sym_application_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.应用系统登记
// 路由：GET /dst/qizhi-resource/applications
// 业务说明：祺智资源中心当前租户已登记应用系统。只读；涉及 sym_application_t 等业务表。
// 祺智资源中心：读取当前租户已登记应用系统。
// 读取 sym_application_t；不写入任何表。
// 安全校验：租户仅由 tenantRuntime.id() 提供，物理库由租户路由选择。
import tenantRuntime;
var tenantId = tenantRuntime.id();
try {
  var items = db.select('select tid as id, app_name as name, app_desc as description, is_use as enabled, asset_status as assetStatus, updated_time as updatedTime from sym_application_t where is_del = 0 order by updated_time desc limit 100');
  return {available: true, items: items == null ? [] : items};
} catch (error) {
  return {available: false, items: []};
}
