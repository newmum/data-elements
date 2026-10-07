{
  "properties": {},
  "id": "cdf552bb6a86329bb82c74b5b04c94d2",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "06.门户版本",
  "path": "/portal-revision",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取达梦业务数据版本标识。"
}
================================
// 接口：06.门户版本
// 路由：GET /search/portal-revision
// 业务说明：读取达梦业务数据版本标识。
import flowDataSource;
var sourceKey=flowDataSource.ensureRuntime('linewell_ods');
var ods=db[sourceKey];
return {revision:ods.selectInt('select count(*) from JC_ENTITY'),generation:'linewell_ods'};
