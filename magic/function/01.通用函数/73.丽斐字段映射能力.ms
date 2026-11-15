{
  "properties": {},
  "id": "nifi_field_map_capabilities_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "73.丽斐字段映射能力",
  "path": "/nifiFieldMappingCapabilities",
  "description": "GET 和 HEAD 共用引擎能力和参数描述。",
  "parameters": [],
  "options": []
}
================================
import com.linewell.dataelement.mapping.core.FunctionArgumentSchema
import com.linewell.dataelement.mapping.core.FunctionRegistry
import com.linewell.dataelement.mapping.core.MappingEngine
import org.springframework.http.ResponseEntity

// @/common/nifiFieldMappingCapabilities 为 GET 和 HEAD 返回字段映射引擎支持的操作及参数。

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
