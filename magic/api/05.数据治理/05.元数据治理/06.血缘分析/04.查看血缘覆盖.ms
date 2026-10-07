{"properties":{},"id":"dwm_lineage_coverage_01","script":null,"groupId":"dwm_lineage_analysis_group_01","name":"04.查看血缘覆盖","path":"/coverage","method":"POST","parameters":[],"options":[],"requestBody":null,"headers":[],"paths":[],"description":"统计当前租户可分析的元数据和有依据的设计、部署依赖。"}
================================
import tenantRuntime
import lineageAnalysis
var tenantId = tenantRuntime.id()
return lineageAnalysis.coverage()