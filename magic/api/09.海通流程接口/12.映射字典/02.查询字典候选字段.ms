{
  "properties": {},
  "id": "nifi_map_dict_cols_get_v1",
  "script": null,
  "groupId": "e156354f7d79565aa8ed30f16bf35ed1",
  "name": "02.查询字典候选字段",
  "path": "/fields",
  "description": "读取当前登记字典表的有效字段。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService as schemas
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity

// GET /nifi/dict/fields。按共享登记 Schema 校验数据源与表的关联，并读取字段。

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
