{
  "properties": {},
  "id": "a0e07289fcee3edc9c4227df25e871e5",
  "script": null,
  "groupId": "f98b9807bf683fc6ac5d9f1233e876cc",
  "name": "01.当前租户概览",
  "createTime": null,
  "updateTime": 1789860655218,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/overview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "知识图谱中心概览：达梦 linewell_ods 的图谱配置、实体与真实业务关系；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.当前租户概览
// 路由：GET /graph/overview
// 业务说明：知识图谱中心概览：达梦 linewell_ods 的图谱配置、实体与真实业务关系；读取或计算后返回业务结果。
import tenantRuntime
import flowDataSource
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var datasets=ods.selectInt("""
select count(1)
from JC_KG_DATASET
""")
var themes=ods.selectInt("""
select count(1)
from JC_KG_THEME
""")
var entities=ods.selectInt("""
select count(1)
from JC_ENTITY
""")
var relations=ods.selectInt("""
select count(1)
from JC_DRAMA_ORG
""")+ods.selectInt("""
select count(1)
from JC_AWARD_RECORD
""")+ods.selectInt("""
select count(1)
from JC_AWARD_ORG
""")
return {tenantId:tenantId,metrics:{repositories:themes,datasets:datasets,entities:entities,relations:relations,source:'linewell_ods'}}
