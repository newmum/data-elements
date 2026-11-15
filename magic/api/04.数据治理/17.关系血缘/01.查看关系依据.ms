{
  "properties": {},
  "id": "dwm_lineage_evidence_01",
  "script": null,
  "groupId": "43712e2ff2134664bc85724de61230d8",
  "name": "01.查看关系依据",
  "path": "/evidence",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "按关系 ID 查看明确的源、目标、任务和字段规则依据。"
}
================================
import tenantRuntime
import lineageAnalysis
// POST /dwm/lineage/evidence 查看关系依据
// 按关系 ID 查看明确的源、目标、任务和字段规则依据。

var tenantId = tenantRuntime.id()
return lineageAnalysis.evidence(body)
