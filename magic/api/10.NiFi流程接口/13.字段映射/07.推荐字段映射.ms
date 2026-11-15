{
  "properties": {},
  "id": "nifi_field_map_recommend_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "07.推荐字段映射",
  "path": "/recommend",
  "description": "生成字段匹配建议。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService as mapping
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService$RecommendResponse as RecommendResponse
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingRequestParameters$RecommendRequest as RecommendRequest
import org.springframework.http.ResponseEntity

// POST /nifi/map/recommend 推荐字段映射。
// POST /nifi/map/recommend 根据来源和目标字段结构推荐匹配规则。

return withHttp(() => {
    var type = 'com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController$RecommendRequest'
    var signature = RecommendResponse.getName() + ' com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.recommend(' + type + ')'
    var input = readBody(body, RecommendRequest, type, signature)
    return ResponseEntity.ok(mapping.recommend(input.sourceSchema(), input.targetSchema()))
})
