{
  "properties": {},
  "id": "nifi_map_restore_post_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "74.恢复映射计划草稿",
  "path": "/field-mapping/plans/{id}/restore-draft",
  "description": "恢复当前画布节点的已归档计划。",
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
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService as projections
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as CanvasDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Node as CanvasNode
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository as pipelines
import com.linewell.dataelement.mapping.core.MappingException
import db
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Objects
import org.springframework.http.ResponseEntity
import tenantRuntime

// POST /nifi/api/field-mapping/plans/{id}/restore-draft 把一个固定计划复制到当前画布节点草稿。
// 锁内校验租户、画布版本、计划权限和节点归属，草稿与字段投影同一事务保存。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = Pipeline.getName() + ' com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.restore(java.lang.String,' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    return mutation.withMutationLock(() => db.transaction(() => {
        var tenantId = tenantRuntime.id()
        var pipelineId = input.path('pipelineId').asText()
        var nodeId = input.path('nodeId').asText()
        var current = planScope(pipelineId, tenantId)
        if (!input.has('expectedUpdatedAt') || current.updatedAt() == null
            || !Objects.equals(current.updatedAt(), input.path('expectedUpdatedAt').asLong())) {
            throw new MappingException('DRAFT_VERSION_CONFLICT', '流程已变化，请重新加载后恢复')
        }
        var planId = path.id
        var restored = readPlans(pipelineId, tenantId, [planId]).get(planId)
        mapping.compile(restored)
        var found = false
        for (node in current.dsl().nodes()) {
            if (node.id() == nodeId) {
                found = true
                break
            }
        }
        if (!found) {
            throw new MappingException('RESOURCE_FORBIDDEN', '目标节点不属于当前流程')
        }

        // 仓库以更新时间作数据库写入条件，再次防止后台入口并发修改草稿。
        var saved = pipelines.update(pipelineId, value => {
            if (!Objects.equals(current.updatedAt(), value.updatedAt())) {
                throw new MappingException('DRAFT_VERSION_CONFLICT', '流程已被其他入口修改')
            }
            var nodes = new ArrayList()
            for (node in value.dsl().nodes()) {
                if (node.id() == nodeId) {
                    var config = new LinkedHashMap(node.config())
                    config.put('mappings', restored.toString())
                    nodes.add(new CanvasNode(node.id(), node.manifestKey(), node.label(), node.category(), node.x(), node.y(), config))
                } else {
                    nodes.add(node)
                }
            }
            var next = new Pipeline(value.id(), value.name(), value.description(), value.createdAt(), value.updatedAt(),
                new CanvasDsl(value.dsl().version(), nodes, value.dsl().edges()), value.nifiProcessGroupId(), value.status(),
                value.lastDeployedHash(), value.lastDeployedAt(), value.lastStoppedAt(), value.nodeMapping(), value.lastBulletinId())
            projections.persist(next)
            return next
        })
        return ResponseEntity.ok(saved)
    }))
})
