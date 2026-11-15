{
  "properties": {},
  "id": "nifi_pipeline_undeploy_post_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "51.卸载流程",
  "path": "/pipelines/{id}/undeploy",
  "method": "POST",
  "description": "清理当前租户画布的原生组和队列，保留画布设计并清除部署关联。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiDeletionGroupScope' as groupScope
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiLifecycleResponse' as respond
import '@/common/nifiTaskState' as syncTask
import '@/common/nifiWriteRuntime' as writeRuntime
import com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService as lineage
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService as errorStore
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorLevel as ErrorLevel
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError$ErrorPhase as ErrorPhase
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowFailureDetails
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer as organizer
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalStateException
import java.time.Instant
import java.util.ArrayList
import java.util.LinkedHashSet
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.support.TransactionTemplate as transactions
import org.springframework.web.server.ResponseStatusException
import tableRead
import tenantRuntime

// POST /nifi/api/pipelines/{id}/undeploy 卸载可信租户画布的原生运行组。
// 原生清理成功后清除运行关联并保留设计。清理异常时保留数据库记录供重试。

return respond(rootCause => mutation.withMutationLock(() => {
    var log = LoggerFactory.getLogger('com.linewell.dataelement.integration.nifi.canvas.Undeploy')
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var pipelineId = path.id
    var canvas = findCanvas(pipelineId, tenantId)
    if (canvas == null) {
        return ResponseEntity.notFound().build()
    }
    // 批量读取当前租户已绑定的任务组，再校验原生根、父链和整个子树的归属。
    var groupId = canvas.nifiProcessGroupId()
    var candidateId = canvas.nodeMapping()?.candidate()?.processGroupId()
    var query = tableRead.from('data_access_agg_task_t', 't', tenantId)
    query.where(tableRead.equal(tableRead.column('t', 'pipeline_id', 'TEXT'), pipelineId))
    query.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    var taskId = tableRead.column('t', 'tid', 'TEXT')
    var taskGroups = query.rows({groupId: tableRead.column('t', 'process_group_id', 'TEXT')}, [taskId], ['asc'], 1001, true)
    if (taskGroups.size() > 1000) {
        throw new IllegalStateException('流程关联任务超过卸载上限')
    }
    var linked = new LinkedHashSet()
    for (group in [groupId, candidateId]) {
        if (group != null && !group.isBlank()) {
            linked.add(group)
        }
    }
    for (task in taskGroups) {
        if (task.groupId != null && !task.groupId.isBlank()) {
            linked.add(task.groupId)
        }
    }
    try {
        if (!linked.isEmpty() || canvas.lastDeployedAt() != null || canvas.lastDeployedHash() != null
            || canvas.nodeMapping() != null || !['SAVED', 'DRAFT'].contains(canvas.status().name())) {
            var runtime = nifi.bindRuntimeNode()
            try {
                var ids = organizer.existingIds(canvas, new ArrayList(linked))
                if (!ids.isEmpty()) {
                    var scopes = []
                    for (id in ids) {
                        scopes.add({id: id, linked: linked.contains(id)})
                    }
                    var rootId = nifi.getRootProcessGroupId()
                    var groups = groupScope(scopes, rootId, pipelineId)
                    var parents = new LinkedHashSet()
                    for (group in groups) {
                        nifi.cleanupProcessGroup(group.id)
                        if (group.parentId != null && !group.parentId.isBlank() && group.parentId != rootId) {
                            parents.add(group.parentId)
                        }
                    }
                    organizer.pruneEmptyAncestors(new ArrayList(parents), null)
                }
            } finally {
                if (runtime != null) {
                    runtime.close()
                }
            }
        }
    } catch (failure) {
        var cause = rootCause(failure)
        if (cause instanceof ResponseStatusException) {
            throw failure
        }
        var detail = cause.getMessage()
        log.warn('NiFi 流程组卸载失败，pipeline={}', pipelineId)
        errorStore.add(pipelineId, new FlowError(null, ErrorLevel.ERROR, ErrorPhase.DEPLOYMENT, null, null, null,
            '清理 NiFi Process Group 失败', FlowFailureDetails.safe('PG ' + groupId + ' 清理失败：' + detail
                + '。请到 NiFi 原生 UI 手动停止并删除该 Process Group。'), null, Instant.now()))
        return ResponseEntity.status(500).body({undeployed: false, processGroupId: groupId == null ? '' : groupId, error: detail})
    }
    // 清除画布和接入任务的运行关联，提交后清理错误记录及已部署血缘。
    transactions.execute(transaction => {
        writeRuntime(pipelineId, tenantId, {groupId: null, status: PipelineStatus.SAVED,
            deployedHash: null, deployedAt: null, mapping: null, lastBulletinId: null})
        syncTask(pipelineId, 'group', null)
        return syncTask(pipelineId, 'status', 0)
    })
    errorStore.clear(pipelineId)
    try {
        lineage.retireDeployment(pipelineId)
    } catch (failure) {
        log.warn('已卸载流程的血缘清理待恢复，pipeline={}', pipelineId)
    }
    return ResponseEntity.ok({undeployed: true})
}))
