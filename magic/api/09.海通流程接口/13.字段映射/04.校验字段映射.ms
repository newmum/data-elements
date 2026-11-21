{
  "properties": {},
  "id": "nifi_field_map_validate_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "04.校验字段映射",
  "path": "/check",
  "description": "校验字段映射规则和登记结构。",
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
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService$MappingIssue as MappingIssue
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService$SchemaCompare as SchemaCompare
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService$ValidationResponse as ValidationResponse
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingRequestParameters$ValidateRequest as ValidateRequest
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.MappingException
import org.springframework.http.ResponseEntity
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// POST /nifi/map/check 校验字段映射。
// POST /nifi/map/check 校验规则及字段绑定。
// 编辑阶段复核当前登记资源。规则编译失败时返回表单可定位的校验项。

return withHttp(() => {
    var type = 'com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController$ValidateRequest'
    var signature = ValidationResponse.getName() + ' com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.validate(' + type + ')'
    var input = readBody(body, ValidateRequest, type, signature)
    var source = input.sourceSchema() == null ? [] : input.sourceSchema()
    var target = input.targetSchema() == null ? [] : input.targetSchema()
    var plan = null
    try {
        plan = mapping.compileForEditing(input.spec()).plan()
    } catch (failure) {
        var cause = failure
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (!(cause instanceof MappingException)) {
            throw failure
        }
        return ResponseEntity.ok(new ValidationResponse(false,
            [new MappingIssue('spec', null, cause.getMessage(), 'ERROR', cause.code(), [])], [],
            new SchemaCompare(source, target)))
    }
    return ResponseEntity.ok(mapping.validateSchemas(plan, input.sourceSchema(), input.targetSchema()))
})
