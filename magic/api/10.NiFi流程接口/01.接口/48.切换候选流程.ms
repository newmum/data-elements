{
  "properties": {},
  "id": "nifi_candidate_activate_post_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "48.切换候选流程",
  "path": "/pipelines/{id}/activate-candidate",
  "method": "POST",
  "description": "校验当前画布候选关联与原生运行状态，在同一事务切换流程组和映射版本。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiBindCandidate' as bindCandidate
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiLifecycleResponse' as respond
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiDeploymentParameters$CandidateRequest as CandidateRequest
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import tenantRuntime

// POST /nifi/api/pipelines/{id}/activate-candidate 切换当前租户保存的候选流程组。
// 先校验入参、租户和候选 ID，再确认旧组和候选组已停止，最后事务内切换部署关联。

return respond(rootCause => {
    var oldType = 'com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController$CandidateRequest'
    var signature = 'java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.integration.nifi.canvas.controller.PipelineRunController.activateCandidate(java.lang.String,'
        + oldType + ')'
    var request = readBody(body, CandidateRequest, oldType, signature)
    return mutation.withMutationLock(() => {
        // 仅按可信租户和路径画布查询，入参只能确认已保存的候选组，不能指定任意 NiFi 组。
        var tenantId = tenantRuntime.id()
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
        }
        var canvas = findCanvas(path.id, tenantId)
        if (canvas == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, '流程不存在')
        }
        var candidate = canvas.nodeMapping()?.candidate()
        if (candidate == null || candidate.processGroupId() != request.processGroupId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, '候选流程已变化，请刷新')
        }

        var requireStopped = (groupId, allowMissing) => {
            if (groupId == null || groupId.isBlank()) {
                return null
            }
            try {
                var aggregate = nifi.getProcessGroupStatus(groupId).path('processGroupStatus').path('aggregateSnapshot')
                var group = nifi.getProcessGroup(groupId)
                var running = group.path('runningCount').asInt(group.path('component').path('runningCount').asInt(-1))
                if (aggregate.path('activeThreadCount').asInt(-1) == 0 && running == 0) {
                    return null
                }
            } catch (failure) {
                var cause = rootCause(failure)
                if (allowMissing && cause instanceof NifiException && cause.status() == 404) {
                    return null
                }
                throw failure
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, '请先停止对应 NiFi 流程组并等待运行任务结束，再切换关联')
        }
        requireStopped(canvas.nifiProcessGroupId(), true)
        requireStopped(candidate.processGroupId(), false)
        var updated = bindCandidate(canvas, candidate, tenantId)
        return ResponseEntity.ok({pipeline: updated, processGroupId: candidate.processGroupId(), mode: 'candidate-activated', started: false})
    })
})
