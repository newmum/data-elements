{
  "properties": {},
  "id": "1087e21423643d4cad7d20e6364a4216",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "02.当前租户概览",
  "path": "/overview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取当前租户的应用、数据源和数据表数量。"
}
================================
// 接口：02.当前租户概览
// 路由：GET /search/overview
// 业务说明：读取当前租户的应用、数据源和数据表数量。
import tenantRuntime;
var tenantId=tenantRuntime.id();
var countSafely=(tableName)=>{try{return db.table(tableName).where({is_del:0}).count();}catch(error){return 0;}};
return {tenantId:tenantId,metrics:{applications:countSafely('sym_application_t'),datasources:countSafely('db_datasource_t'),tables:countSafely('db_table_t')}};
