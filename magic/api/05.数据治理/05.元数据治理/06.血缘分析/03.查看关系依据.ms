{"properties":{},"id":"dwm_lineage_evidence_01","script":null,"groupId":"dwm_lineage_analysis_group_01","name":"03.查看关系依据","path":"/evidence","method":"POST","parameters":[],"options":[],"requestBody":null,"headers":[],"paths":[],"description":"按关系 ID 查看明确的源、目标、任务和字段规则依据。"}
================================
import tenantRuntime
import lineageAnalysis
var tenantId = tenantRuntime.id()
return lineageAnalysis.evidence(body)