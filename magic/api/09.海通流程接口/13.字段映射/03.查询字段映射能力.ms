{
  "properties": {},
  "id": "nifi_field_map_caps_get_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "03.查询字段映射能力",
  "path": "/caps",
  "description": "读取字段映射引擎的能力和参数。",
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
import com.linewell.dataelement.mapping.core.FunctionArgumentSchema
import com.linewell.dataelement.mapping.core.FunctionRegistry
import com.linewell.dataelement.mapping.core.MappingEngine
import org.springframework.http.ResponseEntity

// GET /nifi/map/caps。返回字段映射引擎支持的操作、参数、版本和取样上限。

var functions = []
var operators = {uuid: 'SYSTEM_VALUE', codeTableLookup: 'LOOKUP', expression: 'EXPRESSION'}
for (name in FunctionRegistry.NAMES) {
    var operator = operators[name] == null ? 'FUNCTION' : operators[name]
    functions.add({name: name, version: FunctionRegistry.libraryVersion(), previewSupported: true,
        runtimeSupported: true, implementation: 'JAVA_RECORD', operator: operator,
        directFunction: operator == 'FUNCTION', parameterSchema: FunctionArgumentSchema.parameters(name),
        additionalParametersAllowed: ['coalesce', 'nvl'].contains(name)})
}
return ResponseEntity.ok({engineVersion: MappingEngine.engineVersion(), planVersion: '1.0',
    functions: functions, maxPreviewRows: 100, runtimeType: 'com.linewell.dataelement.nifi.HaitongTransformRecord'})
