{
  "properties": {},
  "id": "nifi_field_map_legacy_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "83.转换旧字段映射",
  "path": "/field-mapping/migrate-legacy",
  "description": "将旧规则转换成可编辑格式。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService as mapping
import org.springframework.http.ResponseEntity

// POST /nifi/api/field-mapping/migrate-legacy 将旧字段映射格式转换为可编辑规则。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = type + ' com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.migrate(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    return ResponseEntity.ok(mapping.normalizeSpec(input.has('spec') ? input.get('spec') : input))
})
