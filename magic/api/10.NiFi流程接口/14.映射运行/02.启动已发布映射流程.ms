{
  "properties": {},
  "id": "nifi_map_start_pub_post_v1",
  "script": null,
  "groupId": "701972acc05f55a89609ca99c376725c",
  "name": "02.启动已发布映射流程",
  "path": "/start",
  "description": "复核计划后启动已发布版本。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import '@/common/nifiMappingPlanScope' as planScope
import '@/common/nifiReadMappingPlans' as readPlans
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.mapping.PublishedMappingLifecycleService as nativeLifecycle
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Objects
import org.springframework.http.ResponseEntity
import tableRead
import tenantRuntime

// POST /nifi/maprun/start 启动已发布映射流程。
// POST /nifi/maprun/start 启动当前租户的已发布运行图。
// 锁内核对当前画布版本，批量加载并重验原计划。启动只使用已发布的原生组件。

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
        if (pipeline.nifiProcessGroupId() == null || pipeline.nodeMapping() == null) {
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
        // 现行登记权限及版本正文都通过后，才可触碰原生运行图。
        var plans = readPlans(pipelineId, tenantId, new ArrayList(published.values()))
        mapping.compileBatch(plans)
        return ResponseEntity.ok(nativeLifecycle.start(pipeline, published, tenantId))
    })
})
