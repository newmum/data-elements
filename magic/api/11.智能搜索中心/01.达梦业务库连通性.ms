{
  "properties": {},
  "id": "3f8671cf46bf332cbc87d93f0874d648",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "01.达梦业务库连通性",
  "path": "/ods-health",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "验证智能搜索中心原有达梦业务库连接与剧目数据量。"
}
================================
// 接口：01.达梦业务库连通性
// 路由：GET /search/ods-health
// 业务说明：验证智能搜索中心原有达梦业务库连接与剧目数据量。
import tenantRuntime;
import flowDataSource;
var tenantId=tenantRuntime.id();
var sourceKey=flowDataSource.ensureRuntime('linewell_ods');
var ods=db[sourceKey];
return {tenantId:tenantId,dataSource:sourceKey,dramaCount:ods.selectInt('select count(*) from JC_DRAMA')};
