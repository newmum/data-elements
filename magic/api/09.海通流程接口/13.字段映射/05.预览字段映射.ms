{
  "properties": {},
  "id": "nifi_field_map_preview_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "05.预览字段映射",
  "path": "/preview",
  "description": "用样本试运行统一映射引擎。",
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
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService$PreviewResponse as PreviewResponse
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingRequestParameters$PreviewRequest as PreviewRequest
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import org.springframework.http.ResponseEntity

// POST /nifi/map/preview 预览字段映射。
// POST /nifi/map/preview 使用映射引擎预览调用方提供的样本。
// 引擎复核登记范围、限制样本规模并脱敏，返回可定位的逐行结果。

return withHttp(() => {
    var type = 'com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController$PreviewRequest'
    var signature = PreviewResponse.getName() + ' com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.preview(' + type + ')'
    var input = readBody(body, PreviewRequest, type, signature)
    return ResponseEntity.ok(mapping.preview(input.spec(), input.sampleRows()))
})
