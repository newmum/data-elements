{
  "properties": {},
  "id": "nifi_map_schema_post_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "01.构造映射字段结构",
  "path": "/schema",
  "description": "批量核验来源与目标登记字段结构。",
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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService as schemas
import com.linewell.dataelement.mapping.core.MappingEngine
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity

// POST /nifi/map/schema 构造映射字段结构。
// POST /nifi/map/schema 构造来源和目标的字段结构契约。
// 批量验证两端登记权限。打开编辑器仅使用登记元数据，显式锁定时才读取物理结构。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = type + ' com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.schemaContract(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    var references = {}
    for (side in ['source', 'target']) {
        var reference = input.path(side + 'Ref')
        if (reference.isObject()) {
            references[side] = reference
        }
    }
    // 单次治理可分写多个目标：合并全部引用后批量鉴权、读取，不能按目标逐个查库。
    var targets = input.path('targetRefs')
    if (!targets.isMissingNode() && !targets.isNull() && !targets.isArray()) {
        throw new MappingException('INVALID_SCHEMA', '共享目标引用必须是数组')
    }
    if (targets.isArray() && targets.size() > 16) {
        throw new MappingException('BUDGET_EXCEEDED', '共享治理最多关联16个目标')
    }
    var targetIds = {}
    if (targets.isArray()) {
        for (index, target in targets) {
            var nodeId = target.path('nodeId').asText().trim()
            var reference = target.path('reference')
            if (nodeId.isEmpty() || targetIds[nodeId] != null || !reference.isObject()) {
                throw new MappingException('INVALID_SCHEMA', '目标分支标识须唯一且包含登记表引用')
            }
            targetIds[nodeId] = true
            references['target:' + index] = reference
        }
    }
    if (references.isEmpty()) {
        throw new MappingException('INVALID_SCHEMA', '请选择已登记来源或目标表')
    }
    var bound = schemas.resolve(references)
    var verifyPhysical = input.path('verifyPhysical').asBoolean(false)
    var physical = verifyPhysical ? schemas.physicalBatch(bound) : {}
    var result = JsonNodeFactory.instance.objectNode()
        .put('allowAdditionalSourceFields', input.path('allowAdditionalSourceFields').asBoolean(true))
        .put('schemaOrigin', verifyPhysical ? 'PHYSICAL' : 'REGISTERED')
    for (side in ['source', 'target']) {
        if (!bound.containsKey(side)) { continue }
        var table = bound.get(side)
        var fields = verifyPhysical ? physical.get(side) : table.fields()
        result.set(side, fields)
        result.put(side + 'Hash', MappingEngine.hash(fields))
        result.putObject(side + 'Ref').put('datasourceId', table.datasourceId()).put('tableId', table.tableId())
    }
    if (targets.isArray()) {
        var branches = result.putArray('targets')
        for (index, target in targets) {
            var key = 'target:' + index
            var table = bound.get(key)
            var fields = verifyPhysical ? physical.get(key) : table.fields()
            var branch = branches.addObject().put('nodeId', target.path('nodeId').asText())
                .put('label', target.path('label').asText()).put('hash', MappingEngine.hash(fields))
            branch.set('fields', fields)
            branch.putObject('reference').put('datasourceId', table.datasourceId()).put('tableId', table.tableId())
        }
    }
    return ResponseEntity.ok(result)
})
