{
  "properties": {},
  "id": "nifi_map_template_import_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "05.导入映射模板",
  "path": "/import",
  "description": "将已授权的固定模板版本转换成当前目标草稿。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as readHttp
import '@/common/nifiReadMappingTemplate' as readTemplate
import com.fasterxml.jackson.databind.JsonNode
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import java.util.UUID
import org.springframework.http.ResponseEntity
import tenantRuntime

// POST /nifi/template/import 导入映射模板。 查询参数 id。
// POST /nifi/template/import 将当前租户的固定模板导入为待确认草稿。
// 校验模板引用后替换本次字段绑定。新规则生成独立标识，旧版本内容原样保留。

return readHttp((text, numeric, missing, invalid) => {
    var oldType = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = oldType + ' com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.importTemplate(java.lang.String,' + oldType + ')'
    var input = readBody(body, JsonNode, oldType, signature)
    var templateId = param.id
    var template = readTemplate(templateId, tenantRuntime.id()).deepCopy()
    var bindings = input.path('fieldBindings')
    var rebind = node => {
        if (node.isArray()) {
            for (child in node.elements()) {
                rebind(child)
            }
            return null
        }
        if (!node.isObject()) {
            return null
        }
        for (key in ['name', 'expression']) {
            var value = node.get(key)
            if (value != null && value.isTextual()) {
                var field = value.asText()
                if (bindings.has(field)) {
                    field = bindings.path(field).asText()
                } else if (key == 'expression') {
                    for (name in bindings.fieldNames()) {
                        field = field.replace('${field:' + name + '}', '${field:' + bindings.path(name).asText() + '}')
                    }
                }
                node.put(key, field)
            }
        }
        for (child in node.elements()) {
            rebind(child)
        }
        return null
    }
    rebind(template)
    template.remove('schemaContract')
    for (rule in template.path('rules').elements()) {
        rule.put('ruleId', UUID.randomUUID().toString())
    }
    for (key in ['dictionaryBindings', 'schemaContract']) {
        if (input.path(key).isObject()) {
            template.set(key, input.path(key))
        }
    }
    var result = mapping.compile(template).plan().document()
    result.putObject('templateOrigin').put('templateId', templateId)
    return ResponseEntity.ok(result)
})
