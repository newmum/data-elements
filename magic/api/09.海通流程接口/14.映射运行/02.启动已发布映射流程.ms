{
  "properties": {},
  "id": "nifi_map_start_pub_post_v1",
  "script": null,
  "groupId": "701972acc05f55a89609ca99c376725c",
  "name": "02.启动已发布映射流程",
  "path": "/start",
  "description": "按当前行业会话、流程标识及期望版本核验登记权限和已发布计划，校对原生图后启动已发布版本。一次性来源运行一次，保留未发布草稿。启动失败尝试停止原生组并返回原因。",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "EXACT_TREE"
    }
  ],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import '@/common/nifiMappingPlanScope' as planScope
import '@/common/nifiReadMappingPlans' as readPlans
import '@/common/nifiWriteRuntime' as writeRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.mapping.core.MappingException
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Objects
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// POST /nifi/maprun/start 接收 pipelineId 和 expectedUpdatedAt，仅启动当前行业租户的已发布运行图。
// 在共享 NiFi 锁内核对保存版本、计划权限和原生边界，仅保存运行状态。失败时尝试停组，草稿继续保留。
// 复用映射请求错误契约和画布运行记录写入，不改变流程定义及已发布计划引用。

var readFlow = groupId => nifi.get('/flow/process-groups/' + groupId, JsonNode).path('processGroupFlow').path('flow')

var getPlanId = processor => {
    var properties = processor.path('component').path('config').path('properties')
    var legacyPlanId = properties.path('plan-id').asText()
    try {
        var document = mapper.readTree(properties.path('execution-plan').asText('{}'))
        var embedded = document.path('_deployment').path('planId').asText()
        return embedded.isBlank() ? legacyPlanId : embedded
    } catch (malformed) {
        return legacyPlanId
    }
}

var oneShot = processor => {
    var component = processor.path('component')
    var type = component.path('type').asText()
    var queryTable = type.endsWith('.QueryDatabaseTable') || type.endsWith('.QueryDatabaseTableRecord')
    return DateTableFetchPolicy.isTableFetch(type) || type.endsWith('.GenerateFlowFile')
        || (queryTable && component.path('config').path('properties').path('Maximum-value Columns').asText().isBlank())
}

var needsStart = processor => {
    var state = processor.path('component').path('state').asText()
    return state != 'DISABLED' && state != 'RUNNING'
}

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = 'java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.startPublished(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    return mutation.withMutationLock(() => {
        var tenantId = tenantRuntime.id()
        var pipelineId = input.path('pipelineId').asText()
        var pipeline = planScope(pipelineId, tenantId)
        if (!Objects.equals(pipeline.updatedAt(), input.path('expectedUpdatedAt').asLong(-1))) {
            throw new MappingException('DRAFT_VERSION_CONFLICT', '流程已变化，请重新读取后启动运行版本')
        }
        var group = pipeline.nifiProcessGroupId()
        var nodeMapping = pipeline.nodeMapping()
        if (group == null || nodeMapping == null) {
            throw new MappingException('RUNTIME_UNAVAILABLE', '流程没有可核验的已发布运行图')
        }
        var query = tableRead.from('data_access_mapping_publication_t', 'p', tenantId)
        query.where(tableRead.equal(tableRead.column('p', 'pipeline_id', 'TEXT'), pipelineId))
        var rows = query.rows({nodeId: tableRead.column('p', 'node_id', 'TEXT'),
            planId: tableRead.column('p', 'plan_id', 'TEXT')}, 101)
        if (rows.size() > 100) {
            throw new MappingException('BUDGET_EXCEEDED', '已发布映射节点超过100项')
        }
        if (rows.isEmpty()) {
            throw new MappingException('RUNTIME_UNAVAILABLE', '没有字段映射发布引用，不能绕过整图部署检查')
        }
        var published = new LinkedHashMap()
        for (row in rows) {
            if (row.nodeId == null || row.nodeId.isBlank() || row.planId == null || row.planId.isBlank()
                || published.putIfAbsent(row.nodeId, row.planId) != null) {
                throw new MappingException('RESOURCE_CORRUPTED', '发布引用缺失或节点重复')
            }
        }
        // 现行登记权限及版本正文都通过后，才读取原生图。
        var plans = readPlans(pipelineId, tenantId, new ArrayList(published.values()))
        mapping.compileBatch(plans)
        var owner = nifi.getProcessGroup(group)
        var expectedOwner = 'data-elements:v1:' + tenantId + ':pipeline:' + pipeline.id()
        if (owner.path('component').path('comments').asText() != expectedOwner) {
            throw new MappingException('RESOURCE_FORBIDDEN', '原生运行图归属标记不一致')
        }

        // 按原生作用域读取修订和连接，根组最多含一个已核验的子组。
        var flows = new LinkedHashMap()
        var root = readFlow(group)
        flows.put(group, root)
        var children = root.path('processGroups')
        if (children.size() > 1) {
            throw new MappingException('MIGRATION_SCOPE_UNSUPPORTED', '当前启动入口仅支持已核验的单层运行图')
        }
        for (child in children.elements()) {
            var childId = child.path('id').asText()
            var nested = readFlow(childId)
            if (!nested.path('processGroups').isEmpty()) {
                throw new MappingException('MIGRATION_SCOPE_UNSUPPORTED', '运行图嵌套范围未被核验')
            }
            flows.put(childId, nested)
        }
        var processors = new LinkedHashMap()
        var connections = new LinkedHashMap()
        for (scope in flows.values()) {
            var nativeProcessors = scope.path('processors')
            if (nativeProcessors.size() > 512) {
                throw new MappingException('BUDGET_EXCEEDED', '启动核验最多512个原生处理器')
            }
            for (processor in nativeProcessors.elements()) {
                processors.put(processor.path('id').asText(), processor)
            }
            for (connection in scope.path('connections').elements()) {
                connections.put(connection.path('id').asText(), connection.path('component'))
            }
        }
        var savedProcessors = nodeMapping.processorIdToCanvasNode()
        if (savedProcessors == null || !processors.keySet().containsAll(savedProcessors.keySet())) {
            throw new MappingException('RUNTIME_UNAVAILABLE', '来源、目标或辅助处理器缺失，未启动运行图')
        }
        var savedEdges = nodeMapping.edgeProcessorEndpoints()
        if (savedEdges == null) {
            throw new MappingException('RUNTIME_UNAVAILABLE', '运行图缺少边界连接索引')
        }

        // 逐个核对已登记边界，拦截指向未登记处理器的成功分支。
        var boundarySources = new LinkedHashSet()
        var expectedEdges = new LinkedHashSet()
        for (endpoint in savedEdges.values()) {
            var edge = connections.get(endpoint.connectionId())
            if (edge == null || endpoint.sourceProcessorId() != edge.path('source').path('id').asText()
                || endpoint.targetProcessorId() != edge.path('destination').path('id').asText()) {
                throw new MappingException('RUNTIME_UNAVAILABLE', '来源或目标边界连接发生变化，未启动运行图')
            }
            boundarySources.add(endpoint.sourceProcessorId())
            expectedEdges.add(endpoint.connectionId())
        }
        for (connectionId in connections.keySet()) {
            var edge = connections.get(connectionId)
            var source = edge.path('source').path('id').asText()
            if (boundarySources.contains(source) && !expectedEdges.contains(connectionId)) {
                for (relation in edge.path('selectedRelationships').elements()) {
                    if (relation.asText() == 'success') {
                        throw new MappingException('MAPPING_UPGRADE_RECOVERY_REQUIRED', '发现未登记的成功分支连接，请先完成运行图恢复')
                    }
                }
            }
        }
        for (nodeId in published.keySet()) {
            var processorId = nodeMapping.primaryProcessorIds().get(nodeId)
            var processor = processors.get(processorId)
            if (processor == null || published.get(nodeId) != getPlanId(processor)) {
                throw new MappingException('PLAN_VERSION_CONFLICT', '当前原生字段节点与已发布计划引用不一致')
            }
        }

        var once = 0
        try {
            // 连续处理器按组提交当前修订，全量来源在运行图就绪后单次触发。
            for (scopeId in flows.keySet()) {
                var command = mapper.createObjectNode().put('id', scopeId).put('state', 'RUNNING')
                    .put('disconnectedNodeAcknowledged', false)
                var components = command.putObject('components')
                for (processor in flows.get(scopeId).path('processors').elements()) {
                    if (needsStart(processor) && !oneShot(processor)) {
                        components.set(processor.path('id').asText(), processor.path('revision'))
                    }
                }
                if (!components.isEmpty()) {
                    nifi.put('/flow/process-groups/' + scopeId, command, JsonNode)
                }
            }
            for (processor in processors.values()) {
                if (needsStart(processor) && oneShot(processor)) {
                    var command = mapper.createObjectNode().put('state', 'RUN_ONCE').put('disconnectedNodeAcknowledged', false)
                    command.set('revision', processor.path('revision'))
                    nifi.put('/processors/' + processor.path('id').asText() + '/run-status', command, JsonNode)
                    once += 1
                }
            }
            var updated = writeRuntime(pipelineId, tenantId, {status: PipelineStatus.RUNNING})
            return ResponseEntity.ok({pipeline: updated, mode: 'PUBLISHED_NATIVE_GRAPH', draftPublished: false,
                oneShotSourceCount: once})
        } catch (failure) {
            // 启动失败时先确认原生组已停止，再记录停止状态。未确认停止时提示人工检查。
            var stopped = false
            try {
                nifi.setProcessGroupState(group, 'STOPPED')
                nifi.waitForAllProcessorsStopped(group, 30000L)
                stopped = true
                writeRuntime(pipelineId, tenantId, {status: PipelineStatus.STOPPED})
            } catch (recovery) {
                if (failure != recovery) {
                    failure.addSuppressed(recovery)
                }
            }
            var code = stopped ? 'PUBLISHED_START_FAILED' : 'MAPPING_UPGRADE_RECOVERY_REQUIRED'
            var reason = stopped ? '已发布运行图启动失败，流程已停止，未发布草稿保留' : '启动及停止确认未完成，请核对原生运行状态'
            throw new MappingException(code, reason, failure)
        }
    })
})
