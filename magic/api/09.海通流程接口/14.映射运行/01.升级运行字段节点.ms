{
  "properties": {},
  "id": "nifi_map_upgrade_post_v1",
  "script": null,
  "groupId": "701972acc05f55a89609ca99c376725c",
  "name": "01.升级运行字段节点",
  "path": "/upgrade",
  "description": "按版本和原生边界升级字段治理、来源连接、工作簿读取及 JDBC 精确读取。",
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
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyExactValueUpgradeService as exactUpgrade
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacySourceShapeUpgradeService as shapeUpgrade
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyTransportUpgradeService as transportUpgrade
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingUpgradeService as nativeUpgrade
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.integration.nifi.canvas.mapping.WorkbookReaderUpgradeService as workbookReaderUpgrade
import com.linewell.dataelement.mapping.core.MappingException
import java.lang.String
import java.util.Objects
import org.springframework.http.ResponseEntity
import tenantRuntime

// POST /nifi/maprun/upgrade 升级运行字段节点。
// 持有共享锁后校验租户、画布版本和节点范围。来源升级保留原生来源组件和水位，并核验队列及治理计划。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = 'java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.upgrade(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    return mutation.withMutationLock(() => {
        var tenantId = tenantRuntime.id()
        var pipelineId = input.path('pipelineId').asText()
        var pipeline = planScope(pipelineId, tenantId)
        if (!Objects.equals(pipeline.updatedAt(), input.path('expectedUpdatedAt').asLong(-1))) {
            throw new MappingException('DRAFT_VERSION_CONFLICT', '画布已变化，请重新加载后升级')
        }
        var nodeId = input.path('nodeId').asText()
        var mode = input.path('mode').asText()
        if (['mysql-read-pool', 'source-normalization', 'exact-value-readers', 'jdbc-timestamp-codecs', 'workbook-record-policy', 'workbook-reader'].contains(mode)) {
            if (input.path('start').asBoolean(false)) {
                throw new MappingException('MIGRATION_UNAVAILABLE', '来源原位升级必须保持流程停止')
            }
            var dryRun = input.path('dryRun').asBoolean(false)
            var fingerprint = input.path('expectedFingerprint').asText()
            if (mode == 'jdbc-timestamp-codecs') {
                return ResponseEntity.ok(exactUpgrade.upgradeTimestampCodecs(pipeline, tenantId, nodeId, dryRun, fingerprint))
            }
            if (mode == 'mysql-read-pool') {
                return ResponseEntity.ok(transportUpgrade.upgradeMySqlReadPool(pipeline, tenantId, nodeId, dryRun, fingerprint))
            }
            if (mode == 'source-normalization') {
                return ResponseEntity.ok(shapeUpgrade.upgrade(pipeline, tenantId, nodeId, dryRun, fingerprint))
            }
            if (mode == 'workbook-record-policy') {
                return ResponseEntity.ok(shapeUpgrade.upgradeWorkbookRows(pipeline, tenantId, nodeId, dryRun, fingerprint))
            }
            if (mode == 'workbook-reader') {
                var retainedFailure = input.path('retainedFailureConnectionId').asText()
                return ResponseEntity.ok(workbookReaderUpgrade.upgrade(pipeline, tenantId, nodeId, retainedFailure, dryRun, fingerprint))
            }
            return ResponseEntity.ok(exactUpgrade.upgrade(pipeline, tenantId, nodeId, dryRun, fingerprint))
        }
        var selected = null
        for (node in pipeline.dsl().nodes()) {
            if (node.id() == nodeId && ['transform.field-mapping', 'transform.field-enrichment'].contains(node.manifestKey())) {
                selected = node
                break
            }
        }
        if (selected == null) {
            throw new MappingException('RESOURCE_FORBIDDEN', '节点不属于当前流程的字段映射')
        }

        // 原生图校验完成后、停流之前，用已保存规则编译并确认调用方看过同一计划。
        var prepare = () => {
            var mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            var saved = selected.config().get('mappings')
            var spec = null
            try {
                spec = saved instanceof String ? mapper.readTree(saved) : mapper.valueToTree(saved)
            } catch (failure) {
                throw new MappingException('INVALID_JSON', '字段草稿无法解析')
            }
            var compiled = mapping.compile(spec)
            if (input.path('expectedPlanHash').asText() != compiled.plan().hash()) {
                throw new MappingException('DRAFT_NOT_SAVED', '当前规则与已保存草稿不一致，请先保存画布再升级')
            }
            return compiled
        }
        return ResponseEntity.ok(nativeUpgrade.replaceNode(pipeline, selected, tenantId, input.path('start').asBoolean(false), prepare))
    })
})
