{
  "properties": {},
  "id": "bc2c6a9145453e7aa5eb5621a191c891",
  "script": null,
  "groupId": "f98b9807bf683fc6ac5d9f1233e876cc",
  "name": "04.关系清单",
  "path": "/relations",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取当前租户的知识图谱关系；涉及 kg_relation 等业务表。"
}
================================
// 接口：04.关系清单
// 路由：GET /graph/relations
// 业务说明：读取当前租户的知识图谱关系；涉及 kg_relation 等业务表。
import tenantRuntime;
var tenantId=tenantRuntime.id();
try{return {tenantId:tenantId,items:db.table('kg_relation').where({is_del:0}).select()};}catch(error){return {tenantId:tenantId,items:[]};}
