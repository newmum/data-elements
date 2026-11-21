{
  "properties": {},
  "id": "nifi_map_sample_post_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "02.取样预览映射",
  "path": "/sample",
  "description": "读取和预览登记来源样本，返回脱敏数据。",
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
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService as schemas
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import com.linewell.dataelement.mapping.core.PreviewMasking
import java.util.LinkedHashMap
import java.util.Set
import org.springframework.http.ResponseEntity

// POST /nifi/map/sample 取样预览映射。
// POST /nifi/map/sample 预览当前租户已登记来源的最多 100 条样本。
// 先核对已登记对象和数据范围，物理取样与映射结果均脱敏后返回。

return withHttp(() => {
    var type = 'com.fasterxml.jackson.databind.JsonNode'
    var signature = 'java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.integration.nifi.canvas.controller.MappingAssetController.sample(' + type + ')'
    var input = readBody(body, JsonNode, type, signature)
    var resolved = schemas.resolve({source: input.path('sourceRef')}).get('source')
    var limit = input.path('limit').asInt(20)
    if (limit < 1 || limit > 100) {
        throw new MappingException('BUDGET_EXCEEDED', '样本数量须为1至100')
    }
    var rows = schemas.sample(resolved, limit)
    var preview = mapping.preview(input.path('spec'), rows)
    var stats = new LinkedHashMap(preview.stats())
    stats.put('sampleMode', 'REGISTERED_SOURCE_MASKED')
    stats.put('masked', true)
    return ResponseEntity.ok({
        sampleRows: PreviewMasking.mask(rows, Set.of(), true),
        resultRows: PreviewMasking.mask(preview.resultRows(), Set.of(), true),
        rowResults: preview.rowResults(),
        stats: stats
    })
})
