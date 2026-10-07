{"properties":{},"id":"dwm_lineage_objects_01","script":null,"groupId":"dwm_lineage_analysis_group_01","name":"01.搜索血缘对象","path":"/objects","method":"POST","parameters":[],"options":[],"requestBody":null,"headers":[],"paths":[],"description":"搜索当前租户已登记的数据表、字段和模型；只接受规范对象 ID。"}
================================
import tenantRuntime
import lineageAnalysis
var tenantId = tenantRuntime.id()
return lineageAnalysis.objects(body)