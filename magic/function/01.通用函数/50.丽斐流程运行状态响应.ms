{
  "properties": {},
  "id": "nifi_pipeline_status_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "50.丽斐流程运行状态响应",
  "path": "/nifiPipelineStatus",
  "description": "为运行状态 GET 和 HEAD 汇总当前租户画布、部署指纹、本地错误和 NiFi 运行快照。",
  "parameters": [
    {
      "name": "flowId",
      "description": "请求路径中的画布标识",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiPipelineReadHttp' as withHttpResponse
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService as runtimeStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.ResponseEntity
import tenantRuntime

// @/common/nifiPipelineStatus 为 GET 和 HEAD 汇总保存态和 NiFi 运行快照。
// 只在确认画布属于当前行业租户后读取错误历史和运行快照，状态读取不会回写生命周期。

return withHttpResponse((parameterValues, textParameter, rootCause) => {
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var canvas = findCanvas(flowId, tenantId)
    if (canvas == null) {
        return ResponseEntity.notFound().build()
    }

    var groupId = canvas.nifiProcessGroupId()
    var storedStatus = canvas.status().name()
    var result = {
        id: flowId,
        name: canvas.name(),
        status: storedStatus,
        deployed: groupId != null,
        processGroupId: groupId,
        lastDeployedHash: canvas.lastDeployedHash(),
        currentHash: DslHasher.deploymentHash(canvas.dsl(), DslHasher.CURRENT_COMPILER_REVISION),
        lastDeployedAt: canvas.lastDeployedAt(),
        lastStoppedAt: canvas.lastStoppedAt(),
        errors: errorStore.list(flowId),
        nodeMapping: canvas.nodeMapping()
    }

    // 只读一次租户运行快照。明确状态覆盖保存态，未知或缺失状态沿用保存态。
    var snapshot = runtimeStatus.snapshot()
    var runtime = runtimeStatus.describe(snapshot, groupId, storedStatus)
    result.putAll(runtime)
    result.runtimeRevision = snapshot.revision
    var state = runtime.runtimeState
    var effective = {RUNNING: 'RUNNING', STOPPING: 'STOPPING', STOPPED: 'STOPPED', EMPTY: 'STOPPED',
        INVALID: 'RUN_ERROR', RUN_ERROR: 'RUN_ERROR', UNDEPLOYED: storedStatus == 'DRAFT' ? 'DRAFT' : 'SAVED'}
    result.status = effective.containsKey(state) ? effective[state] : storedStatus
    if (snapshot.index != null && groupId != null) {
        result.nifiStatus = snapshot.index.rawStatus(groupId)
    }
    if (runtime.runtimeFresh != true || state == 'MISSING') {
        result.nifiStatusError = state == 'MISSING' ? 'NiFi 中未找到已关联的流程' : snapshot.message
    }
    return ResponseEntity.ok(result)
})
