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
    if (references.isEmpty()) {
        throw new MappingException('INVALID_SCHEMA', '请选择已登记来源或目标表')
    }
    var bound = schemas.resolve(references)
    var verifyPhysical = input.path('verifyPhysical').asBoolean(false)
    var physical = verifyPhysical ? schemas.physicalBatch(bound) : {}
    var result = JsonNodeFactory.instance.objectNode()
        .put('allowAdditionalSourceFields', input.path('allowAdditionalSourceFields').asBoolean(true))
        .put('schemaOrigin', verifyPhysical ? 'PHYSICAL' : 'REGISTERED')
    for (side in bound.keySet()) {
        var table = bound.get(side)
        var fields = verifyPhysical ? physical.get(side) : table.fields()
        result.set(side, fields)
        result.put(side + 'Hash', MappingEngine.hash(fields))
        result.putObject(side + 'Ref').put('datasourceId', table.datasourceId()).put('tableId', table.tableId())
    }
    return ResponseEntity.ok(result)
})
