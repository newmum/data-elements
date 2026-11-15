{
  "properties": {},
  "id": "nifi_map_templates_post_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "06.保存映射模板",
  "path": "/save",
  "description": "为授权规则追加新的不可变模板版本。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import '@/common/nifiSaveMappingVersion' as saveVersion
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingEngine
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity

// POST /nifi/template/save 保存映射模板。
// POST /nifi/template/save 校验并保存当前租户的规则模板新版本。
// 拒绝保存凭据及样本，模板保留编译确认后的规则配置。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = type + ' com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.saveTemplate(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    var specification = mapping.compile(input.path('spec')).plan().document()
    var blocked = ['password', 'jdbcURL', 'jdbcUrl', 'token', 'keytab', 'privateKey', 'sampleRows', 'snapshots']
    var rejectSecrets = node => {
        if (node.isObject()) {
            for (name in node.fieldNames()) {
                if (blocked.contains(name)) {
                    throw new MappingException('TEMPLATE_SECRET_FORBIDDEN', '模板不能保存凭据或业务样本')
                }
                rejectSecrets(node.path(name))
            }
        } else if (node.isArray()) {
            for (child in node.elements()) {
                rejectSecrets(child)
            }
        }
        return null
    }
    rejectSecrets(specification)
    specification.remove(['planId', 'sampleRows', 'sourceConfig'])
    var definition = JsonNodeFactory.instance.objectNode().put('schemaVersion', '2.0').put('engineVersion', MappingEngine.engineVersion())
    return ResponseEntity.ok(saveVersion('TEMPLATE', input.path('name').asText(), definition, specification))
})
