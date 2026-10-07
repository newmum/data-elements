{
  "properties": {},
  "id": "5926ed16750f381d8a3cddb00f4d919f",
  "script": null,
  "groupId": "f98b9807bf683fc6ac5d9f1233e876cc",
  "name": "02.数据集清单",
  "createTime": null,
  "updateTime": 1789860655283,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/datasets",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "知识图谱数据集：读取达梦 linewell_ods。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据集清单
// 路由：GET /graph/datasets
// 业务说明：知识图谱数据集：读取达梦 linewell_ods。
import tenantRuntime
import flowDataSource
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
return {tenantId:tenantId,items:ods.select("""
select *
from JC_KG_DATASET
"""),source:'linewell_ods'}
