{
  "properties": {},
  "id": "b1f927b6b5e134f2be07d52381540b7c",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "03.数据源清单",
  "path": "/sources",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取当前租户已登记的数据源；涉及 db_datasource_t 等业务表。"
}
================================
// 接口：03.数据源清单
// 路由：GET /search/sources
// 业务说明：读取当前租户已登记的数据源；涉及 db_datasource_t 等业务表。
import tenantRuntime;
var tenantId=tenantRuntime.id();
try{var items=db.table('db_datasource_t').where({is_del:0}).select();return {tenantId:tenantId,items:items,total:items.size()};}catch(error){return {tenantId:tenantId,items:[],total:0};}
