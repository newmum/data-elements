{
  "properties": {},
  "id": "3e7384111e1c432484ea4f68ce080d67",
  "groupId": "a6b70761965047169f1ce26f877ebac7",
  "name": "02.保存登记",
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"externalFlowId\":\"external-001\",\"pipelineId\":\"已保存NiFi流程ID\",\"sourceFormat\":\"CANVAS_DSL_V1\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/task/registry/save；将当前租户唯一外部ID绑定到已保存且版本为1的真实NiFi流程，重复请求幂等。",
  "script": null
}
================================
import tenantRuntime
import cn.hutool.core.util.IdUtil

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后登记流程'
}
var externalFlowId = body?.externalFlowId == null ? '' : ('' + body.externalFlowId).trim()
var pipelineId = body?.pipelineId == null ? '' : ('' + body.pipelineId).trim()
var sourceFormat = body?.sourceFormat == null ? 'CANVAS_DSL_V1' : ('' + body.sourceFormat).trim()
if (!externalFlowId.matches('^[A-Za-z0-9._:-]{1,128}$')) {
    exit 400, '外部流程ID只能包含字母、数字、点、下划线、冒号或连字符，最长128位'
}
if (!pipelineId.matches('^[A-Za-z0-9_-]{1,64}$')) {
    exit 400, 'NiFi流程ID无效'
}
if (sourceFormat != 'CANVAS_DSL_V1') {
    exit 400, '只能登记NiFi Canvas DSL v1；原海通Flow JSON不能直接转换或部署'
}
var pipeline = db.table('nifi_pipeline_t').where()
    .eq('tenant_id', tenantId).eq('id', pipelineId).eq('is_del', 0).selectOne()
if (pipeline == null) {
    exit 404, 'NiFi流程不存在或无权访问'
}
var field = (row, snake, camel) => {
    if (row == null) { return null }
    if (row[camel] != null) { return row[camel] }
    if (row[snake] != null) { return row[snake] }
    return row[snake.toUpperCase()]
}
var dslVersion = field(pipeline, 'dsl_version', 'dslVersion')
var dslHash = field(pipeline, 'dsl_hash', 'dslHash')
if (('' + dslVersion) != '1' || dslHash == null || ('' + dslHash).trim() == '') {
    exit 409, 'NiFi流程尚未保存有效的Canvas DSL v1'
}
var existing = db.table('nifi_pipeline_external_ref_t').where()
    .eq('tenant_id', tenantId).eq('external_flow_id', externalFlowId).selectOne()
if (existing != null) {
    if (field(existing, 'pipeline_id', 'pipelineId') != pipelineId) {
        exit 409, '此外部流程ID已绑定另一条NiFi流程'
    }
    return { tid: field(existing, 'tid', 'tid'), externalFlowId: externalFlowId, pipelineId: pipelineId, created: false, sourceFormat: field(existing, 'source_format', 'sourceFormat') }
}
var assigned = db.table('nifi_pipeline_external_ref_t').where()
    .eq('tenant_id', tenantId).eq('pipeline_id', pipelineId).selectOne()
if (assigned != null) {
    exit 409, '该NiFi流程已绑定另一外部流程ID'
}
var tid = IdUtil.getSnowflakeNextIdStr()
var nowTime = new Date()
try {
    db.table('nifi_pipeline_external_ref_t').primary('tid').insert({
        tid: tid,
        tenant_id: tenantId,
        external_flow_id: externalFlowId,
        pipeline_id: pipelineId,
        source_format: sourceFormat,
        dsl_hash_at_import: dslHash,
        created_time: nowTime,
        updated_time: nowTime
    })
} catch (e) {
    // 数据库唯一键是并发登记的最终裁决：相同绑定按幂等返回，异绑定明确冲突。
    var concurrent = db.table('nifi_pipeline_external_ref_t').where()
        .eq('tenant_id', tenantId).eq('external_flow_id', externalFlowId).selectOne()
    if (concurrent != null) {
        if (field(concurrent, 'pipeline_id', 'pipelineId') == pipelineId) {
            return { tid: field(concurrent, 'tid', 'tid'), externalFlowId: externalFlowId, pipelineId: pipelineId, created: false, sourceFormat: field(concurrent, 'source_format', 'sourceFormat') }
        }
        exit 409, '此外部流程ID已被并发登记到另一条NiFi流程'
    }
    var otherExternal = db.table('nifi_pipeline_external_ref_t').where()
        .eq('tenant_id', tenantId).eq('pipeline_id', pipelineId).selectOne()
    if (otherExternal != null) {
        exit 409, '该NiFi流程已被并发登记到另一外部流程ID'
    }
    throw e
}
return { tid: tid, externalFlowId: externalFlowId, pipelineId: pipelineId, created: true, sourceFormat: sourceFormat }
