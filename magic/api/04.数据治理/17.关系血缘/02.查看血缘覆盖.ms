{
  "properties": {},
  "id": "dwm_lineage_coverage_01",
  "script": null,
  "groupId": "43712e2ff2134664bc85724de61230d8",
  "name": "02.查看血缘覆盖",
  "path": "/coverage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "统计当前租户可分析的元数据和有依据的设计、部署依赖。"
}
================================
import tenantRuntime
import lineageAnalysis
// POST /dwm/lineage/coverage 查看血缘覆盖
// 统计当前租户可分析的元数据和有依据的设计、部署依赖。

var tenantId = tenantRuntime.id()
return lineageAnalysis.coverage()
