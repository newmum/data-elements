{
  "properties": {},
  "id": "nifi_mapping_dict_cols_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "70.海通字典候选字段",
  "path": "/nifiMappingDictionaryColumns",
  "description": "读取当前登记字典表的有效字段。",
  "parameters": [],
  "options": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService as schemas
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity

// @/common/nifiMappingDictionaryColumns 为 GET 和 HEAD 返回已登记字典表的字段。
// 使用部署和字段校验共用的登记 Schema 解析，验证选定数据源及表的对应关系。

return readHttp((text, numeric, missing, invalid) => {
    var datasourceId = text('datasourceId', null)
    if (datasourceId == null) {
        return missing('datasourceId')
    }
    var tableId = text('tableId', null)
    if (tableId == null) {
        return missing('tableId')
    }
    if (tableId.isBlank()) {
        throw new MappingException('RESOURCE_FORBIDDEN', '请选择已登记码表')
    }
    var reference = JsonNodeFactory.instance.objectNode().put('datasourceId', datasourceId).put('tableId', tableId)
    return ResponseEntity.ok(schemas.resolve({dictionary: reference}).get('dictionary').fields())
})
