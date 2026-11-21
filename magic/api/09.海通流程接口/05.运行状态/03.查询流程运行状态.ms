{
  "properties": {},
  "id": "nifi_pipeline_status_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "03.查询流程运行状态",
  "path": "/status",
  "method": "GET",
  "description": "从当前行业租户读取指定流程的保存态、部署指纹和错误历史。已部署流程的原生状态读取使用所属节点快照。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
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

// 从当前行业租户读取指定流程的保存态、部署指纹和错误历史。
// 已部署流程的原生状态读取使用所属节点快照。

var flowId = param.id

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
    var nodeId = canvas.nodeMapping()?.runtimeNodeId()
    var snapshot = nodeId == null || groupId == null ? runtimeStatus.snapshot() : runtimeStatus.snapshotForNode(nodeId)
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
