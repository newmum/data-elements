{
  "properties": {},
  "id": "nifi_mapping_plan_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "65.丽斐映射计划版本",
  "path": "/nifiMappingPlanDetail",
  "description": "读取并核对已授权流程的固定计划版本。",
  "parameters": [
    {
      "name": "planId",
      "description": "请求路径标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiMappingPlanScope' as planScope
import '@/common/nifiMappingReadHttp' as readHttp
import '@/common/nifiReadMappingPlans' as readPlans
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity
import tenantRuntime

// @/common/nifiMappingPlanDetail 为 GET 和 HEAD 读取当前流程不可变计划。
// 先核对请求和画布范围，再读取计划正文并重验现行引用。

return readHttp((text, numeric, missing, invalid) => {
    var pipelineId = text('pipelineId', null)
    if (pipelineId == null) {
        return missing('pipelineId')
    }
    if (planId == null || planId.isBlank()) {
        throw new MappingException('RESOURCE_FORBIDDEN', '计划引用无效')
    }
    var tenantId = tenantRuntime.id()
    planScope(pipelineId, tenantId)
    var document = readPlans(pipelineId, tenantId, [planId]).get(planId)
    mapping.compile(document)
    return ResponseEntity.ok(document)
})
