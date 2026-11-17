{
  "properties": {},
  "id": "nifi_stop_canvas_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "59.海通停止流程",
  "path": "/nifiStopCanvas",
  "description": "画布和登记表共用停止并同步流程和任务状态。",
  "parameters": [
    {
      "name": "pipelineId",
      "description": "画布标识",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleResponse' as respond
import '@/common/nifiTaskState' as syncTask
import '@/common/nifiWriteRuntime' as writeRuntime
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorLevel as ErrorLevel
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorPhase as ErrorPhase
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.System
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.springframework.transaction.support.TransactionTemplate as transactions
import tenantRuntime

// @/common/nifiStopCanvas 为画布停止和登记表单任务停止共用。
// 仅停止当前租户已关联的原生组。等待超时保留 STOPPING，后续请求可继续核验。

return respond(rootCause => mutation.withMutationLock(() => {
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var canvas = findCanvas(pipelineId, tenantId)
    if (canvas == null) {
        return ResponseEntity.notFound().build()
    }
    var groupId = canvas.nifiProcessGroupId()
    if (groupId == null) {
        return ResponseEntity.badRequest().body({error: 'Pipeline has not been deployed yet'})
    }
    var nodeId = canvas.nodeMapping()?.runtimeNodeId()
    var runtime = nodeId == null ? null : nifi.bindAssignedNode(nodeId)
    try {
        try {
            nifi.setProcessGroupState(groupId, 'STOPPED')
        } catch (failure) {
            var cause = rootCause(failure)
            if (!(cause instanceof NifiException)) {
                throw failure
            }
            var detail = FlowFailureDetails.describe(cause)
            errorStore.add(pipelineId, new FlowError(null, ErrorLevel.ERROR, ErrorPhase.RUNTIME, null, null, null,
                '停止流程组失败', detail, null, Instant.now()))
            var status = cause.status() > 0 ? cause.status() : 502
            return ResponseEntity.status(status).body({error: detail, status: status, body: FlowFailureDetails.safe(cause.body())})
        }
        writeRuntime(pipelineId, tenantId, {status: PipelineStatus.STOPPING})
        try {
            nifi.waitForAllProcessorsStopped(groupId, 30000L)
        } catch (failure) {
            if (!(rootCause(failure) instanceof NifiException)) {
                throw failure
            }
            return ResponseEntity.accepted().body({processGroupId: groupId, state: 'STOPPING',
                warning: 'NiFi 已接受停止请求，正在等待运行中的任务结束'})
        }
        // 停止时间和接入任务状态一起写入。写入失败可用原生组现状重试。
        var stoppedAt = System.currentTimeMillis()
        transactions.execute(transaction => {
            writeRuntime(pipelineId, tenantId, {status: PipelineStatus.STOPPED, stoppedAt: stoppedAt})
            return syncTask(pipelineId, 'stopped', null)
        })
        return ResponseEntity.ok({processGroupId: groupId, state: 'STOPPED', stoppedAt: stoppedAt})
    } finally {
        if (runtime != null) { runtime.close() }
    }
}))
