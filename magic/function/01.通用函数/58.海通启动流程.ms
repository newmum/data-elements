{
  "properties": {},
  "id": "nifi_start_canvas_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "58.海通启动流程",
  "path": "/nifiStartCanvas",
  "description": "画布和登记表共用启动，校验清空规则、部署和运行状态。",
  "parameters": [
    {
      "name": "pipelineId",
      "description": "画布标识",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "force",
      "description": "是否显式重建",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "confirmCleanup",
      "description": "是否确认清空",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiDeployCanvas' as deploy
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleResponse' as respond
import '@/common/nifiOneShotSources' as oneShotSources
import '@/common/nifiTaskState' as syncTask
import '@/common/nifiWriteRuntime' as writeRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler as compiler
import com.linewell.dataelement.integration.nifi.canvas.compile.SyncPolicy
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorLevel as ErrorLevel
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorPhase as ErrorPhase
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineRuntimeStatusResolver as statusResolver
import com.linewell.dataelement.integration.nifi.canvas.mapping.AccessTaskFieldMappingBinder as mappingBinder
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalStateException
import java.time.Instant
import java.util.LinkedHashSet
import java.util.Objects
import java.util.Set
import org.springframework.http.ResponseEntity
import tenantRuntime

// @/common/nifiStartCanvas 为画布启动和登记表单任务启动共用。
// 锁内读取可信租户的保存态，先确认已有部署及清空策略，再按部署记录执行启动或显式重建。

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
    var currentHash = DslHasher.deploymentHash(canvas.dsl(), DslHasher.CURRENT_COMPILER_REVISION)
    var needsDeployment = message => ResponseEntity.status(409).body({error: 'deployment-required', message: message,
        deployed: groupId != null && canvas.lastDeployedHash() != null, currentHash: currentHash,
        lastDeployedHash: canvas.lastDeployedHash()})
    if (!force && (groupId == null || groupId.isBlank())) {
        return needsDeployment('请先部署流程，再启动 NiFi')
    }

    var update = changes => writeRuntime(pipelineId, tenantId, changes)
    if (force) {
        var bound = mappingBinder.bind(canvas)
        if (bound != canvas) {
            canvas = update({expectedDesignHash: canvas.designHash(), dsl: bound.dsl()})
            currentHash = DslHasher.deploymentHash(canvas.dsl(), DslHasher.CURRENT_COMPILER_REVISION)
        }
    }
    // 普通启动以已部署版本的运行策略为准，不用未发布草稿推断清空行为。
    var deployedCleanup = canvas.nodeMapping()?.cleanupConfirmationRequired()
    var requiresCleanup = force ? SyncPolicy.needsCleanupConfirmation(canvas)
        : deployedCleanup != null ? deployedCleanup
        : Objects.equals(canvas.lastDeployedHash(), currentHash) && SyncPolicy.needsCleanupConfirmation(canvas)
    if (requiresCleanup && !confirmCleanup) {
        return ResponseEntity.status(409).body({error: 'cleanup-confirmation-required',
            message: '请核对已部署版本的目标表及清空范围，再确认启动。'})
    }
    if (force) {
        return deploy(pipelineId, {forceRebuild: true, startAfterDeploy: true}, true)
    }

    var nifiFailure = cause => {
        var status = cause.status() > 0 ? cause.status() : 502
        return ResponseEntity.status(status).body({error: FlowFailureDetails.describe(cause), status: status,
            body: FlowFailureDetails.safe(cause.body())})
    }
    var runtime = null
    try {
        runtime = nifi.bindRuntimeNode()
        var primary = canvas.nodeMapping()?.primaryProcessorIds()
        if (canvas.dsl() != null && primary != null) {
            var fullSources = new LinkedHashSet()
            for (node in canvas.dsl().nodes()) {
                var processorId = primary.get(node.id())
                if (processorId != null && compiler.usesStandardJdbcFullFetch(node)) {
                    fullSources.add(processorId)
                }
            }
            if (!fullSources.isEmpty()) {
                var graph = nifi.get('/process-groups/' + groupId + '/processors?includeDescendantGroups=true', JsonNode)
                for (processor in graph.path('processors').elements()) {
                    var component = processor.path('component')
                    var props = component.path('config').path('properties')
                    if (fullSources.contains(component.path('id').asText()) && component.path('type').asText() == DateTableFetchPolicy.TYPE
                        && props.path('Maximum-value Columns').asText('').isBlank()
                        && !props.path(DateTableFetchPolicy.PARTITION_COLUMN).asText('').isBlank()) {
                        return needsDeployment('当前 NiFi 流程仍使用旧版全量时间分片。请从保存旁菜单选择“重新部署流程”，再启动。')
                    }
                }
            }
        }
        update({status: PipelineStatus.DEPLOYING})
        try {
            var oneShot = canvas.nodeMapping() == null ? Set.of() : canvas.nodeMapping().oneShotSources()
            if (oneShot == null) {
                oneShot = Objects.equals(canvas.lastDeployedHash(), currentHash) ? oneShotSources(canvas, primary) : Set.of()
            }
            var ranOnce = !oneShot.isEmpty() && nifi.startWithOneShotSourcesIfPresent(groupId, oneShot)
            var alreadyRunning = false
            if (!ranOnce) {
                // 请求重试时识别 NiFi 已启动的组，只补齐保存状态与任务关联。
                try {
                    var raw = nifi.getProcessGroupStatus(groupId)
                    alreadyRunning = statusResolver.resolve(canvas.status(), raw, groupId) == PipelineStatus.RUNNING
                } catch (failure) {
                    alreadyRunning = false
                }
                if (alreadyRunning) {
                    errorStore.replacePhase(pipelineId, ErrorPhase.DEPLOYMENT, [])
                } else {
                    nifi.setProcessGroupState(groupId, 'RUNNING')
                }
            }
            var updated = update({status: PipelineStatus.RUNNING})
            syncTask(pipelineId, 'started', null)
            var result = {pipeline: updated, processGroupId: groupId, started: true,
                mode: alreadyRunning ? 'already-running' : 'fast-restart'}
            if (!alreadyRunning) {
                result.oneShot = ranOnce
            }
            return ResponseEntity.ok(result)
        } catch (failure) {
            var cause = rootCause(failure)
            if (cause instanceof NifiException) {
                update({status: PipelineStatus.DEPLOY_FAILED})
                syncTask(pipelineId, 'status', 2)
                errorStore.add(pipelineId, new FlowError(null, ErrorLevel.ERROR, ErrorPhase.DEPLOYMENT,
                    null, null, null, '快速启动失败', FlowFailureDetails.describe(cause), null, Instant.now()))
                return nifiFailure(cause)
            }
            if (cause instanceof IllegalStateException) {
                update({status: canvas.status()})
                return ResponseEntity.status(409).body({error: 'full-source-not-ready', message: cause.getMessage()})
            }
            throw failure
        }
    } catch (failure) {
        var cause = rootCause(failure)
        if (cause instanceof NifiException) {
            return nifiFailure(cause)
        }
        throw failure
    } finally {
        if (runtime != null) {
            runtime.close()
        }
    }
}))
