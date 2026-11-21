{
  "properties": {},
  "id": "nifi_map_plan_get_v1",
  "script": null,
  "groupId": "64f711bbc78e59fd86e03d6f0ba9a1c9",
  "name": "02.查询映射计划版本",
  "path": "/detail",
  "description": "读取并核对已授权流程的固定计划版本。",
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
    },
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
import '@/common/nifiMappingPlanScope' as planScope
import '@/common/nifiMappingReadHttp' as readHttp
import '@/common/nifiReadMappingPlans' as readPlans
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity
import tenantRuntime

// GET /nifi/plan/detail。读取当前行业流程的不可变映射版本，再复核其所属流程的授权范围。

var planId = param.id

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
