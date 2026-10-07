{
  "properties": {},
  "id": "cc82b6b0f5db45439f01f14f5e3309de",
  "groupId": "a6b70761965047169f1ce26f877ebac7",
  "name": "01.登记预检",
  "path": "/validate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"externalFlowId\":\"external-001\",\"pipelineId\":\"已有NiFi流程ID（可选）\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/task/registry/validate；只检查当前租户外部流程ID和已有NiFi流程绑定，不转换原海通本地Flow。",
  "script": null
}
================================
import tenantRuntime

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后登记流程'
}
var externalFlowId = body?.externalFlowId == null ? '' : ('' + body.externalFlowId).trim()
var pipelineId = body?.pipelineId == null ? '' : ('' + body.pipelineId).trim()
if (!externalFlowId.matches('^[A-Za-z0-9._:-]{1,128}$')) {
    exit 400, '外部流程ID只能包含字母、数字、点、下划线、冒号或连字符，最长128位'
}
if (pipelineId != '' && !pipelineId.matches('^[A-Za-z0-9_-]{1,64}$')) {
    exit 400, 'NiFi流程ID无效'
}
var existing = db.table('nifi_pipeline_external_ref_t').where()
    .eq('tenant_id', tenantId).eq('external_flow_id', externalFlowId).selectOne()
var assigned = pipelineId == '' ? null : db.table('nifi_pipeline_external_ref_t').where()
    .eq('tenant_id', tenantId).eq('pipeline_id', pipelineId).selectOne()
var pipeline = pipelineId == '' ? null : db.table('nifi_pipeline_t').where()
    .eq('tenant_id', tenantId).eq('id', pipelineId).eq('is_del', 0).selectOne()
var field = (row, snake, camel) => {
    if (row == null) { return null }
    if (row[camel] != null) { return row[camel] }
    if (row[snake] != null) { return row[snake] }
    return row[snake.toUpperCase()]
}
return {
    externalFlowId: externalFlowId,
    exists: existing != null,
    pipelineId: field(existing, 'pipeline_id', 'pipelineId'),
    sameBinding: existing != null && pipelineId != '' && field(existing, 'pipeline_id', 'pipelineId') == pipelineId,
    pipelineExists: pipeline != null,
    pipelineAssignedElsewhere: assigned != null && field(assigned, 'external_flow_id', 'externalFlowId') != externalFlowId,
    sourceFormat: 'CANVAS_DSL_V1'
}
