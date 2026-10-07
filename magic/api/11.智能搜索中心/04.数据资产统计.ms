{
  "properties": {},
  "id": "13dc2c3a16fe321b898f2828770b7671",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "04.数据资产统计",
  "path": "/assets",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取当前租户的数据资产统计。"
}
================================
// 接口：04.数据资产统计
// 路由：GET /search/assets
// 业务说明：读取当前租户的数据资产统计。
import tenantRuntime;
var tenantId=tenantRuntime.id();
var countSafely=(tableName)=>{try{return db.table(tableName).where({is_del:0}).count();}catch(error){return 0;}};
return {tenantId:tenantId,metrics:{catalogs:countSafely('da_catalog_t'),tables:countSafely('db_table_t'),columns:countSafely('db_table_column_t')}};
