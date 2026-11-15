{
  "properties": {},
  "id": "nifi_mapping_template_read_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "76.NiFi读取映射模板",
  "path": "/nifiReadMappingTemplate",
  "description": "模板详情和导入共用版本读取及授权。",
  "parameters": [
    {
      "name": "templateId",
      "description": "模板标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "tenantId",
      "description": "可信租户标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingEngine
import com.linewell.dataelement.mapping.core.MappingException
import tableRead

// @/common/nifiReadMappingTemplate 供模板详情和导入共用，验证租户、版本摘要及现行资源权限。

var query = tableRead.from('data_access_mapping_resource_t', 'r', tenantId)
query.where(tableRead.equal(tableRead.column('r', 'tid', 'TEXT'), templateId))
query.where(tableRead.equal(tableRead.column('r', 'resource_kind', 'TEXT'), 'TEMPLATE'))
var rows = query.rows({content: tableRead.column('r', 'resource_content', 'LOB'),
    definition: tableRead.column('r', 'definition_json', 'LOB'), hash: tableRead.column('r', 'resource_hash', 'TEXT')}, 2)
if (rows.size() != 1) {
    throw new MappingException('RESOURCE_FORBIDDEN', '包含不存在或其他租户的版本资源')
}
var mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
var parse = value => {
    try {
        return mapper.readTree(value == null ? 'null' : value)
    } catch (failure) {
        throw new MappingException('RESOURCE_CORRUPTED', '版本内容无法解析')
    }
}
var stored = rows[0]
var document = parse(stored.content)
if (MappingEngine.hash(document) != stored.hash) {
    throw new MappingException('RESOURCE_CORRUPTED', '版本资源摘要不一致')
}
parse(stored.definition)
mapping.compile(document)
return document
