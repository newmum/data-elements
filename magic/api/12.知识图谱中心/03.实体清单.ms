{
  "properties": {},
  "id": "e0ffa8c19d633605b338634cffa6d617",
  "script": null,
  "groupId": "f98b9807bf683fc6ac5d9f1233e876cc",
  "name": "03.实体清单",
  "createTime": null,
  "updateTime": 1789860655405,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/entities",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "知识图谱实体清单：读取达梦 linewell_ods 的统一实体主档。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.实体清单
// 路由：GET /graph/entities
// 业务说明：知识图谱实体清单：读取达梦 linewell_ods 的统一实体主档。
import tenantRuntime
import flowDataSource
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var size=pageSize==null?100:Integer.parseInt(''+pageSize);if(size<1){size=1};if(size>500){size=500}
return {tenantId:tenantId,items:ods.select(`select ID id,ENTITY_TYPE entity_type,NAME name,SHORT_NAME short_name,UPDATED_AT updated_at from JC_ENTITY order by UPDATED_AT desc fetch first ${size} rows only`),source:'linewell_ods'}
