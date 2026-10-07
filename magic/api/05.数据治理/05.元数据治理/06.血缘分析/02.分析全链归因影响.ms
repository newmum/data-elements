{"properties":{},"id":"dwm_lineage_analyze_01","script":null,"groupId":"dwm_lineage_analysis_group_01","name":"02.分析全链归因影响","path":"/analyze","method":"POST","parameters":[],"options":[],"requestBody":null,"headers":[],"paths":[],"description":"按照全链、归因或影响模式遍历当前租户的设计或部署依赖；无运行事实冒用。"}
================================
import tenantRuntime
import lineageAnalysis
var tenantId = tenantRuntime.id()
return lineageAnalysis.analyze(body)