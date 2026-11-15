{
  "properties": {},
  "id": "nifi_field_map_compile_v1",
  "script": null,
  "groupId": "d49f0cc7292f543b89a960eed54923c1",
  "name": "08.编译字段映射",
  "path": "/compile",
  "description": "编译并返回公开执行计划。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiLifecycleBody' as readBody
import '@/common/nifiMappingReadHttp' as withHttp
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingRequestParameters$CompileRequest as CompileRequest
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService as mapping
import com.linewell.dataelement.mapping.core.FunctionRegistry
import com.linewell.dataelement.mapping.core.MappingEngine
import java.util.LinkedHashMap
import org.springframework.http.ResponseEntity

// POST /nifi/map/compile 编译字段映射。
// POST /nifi/map/compile 编译当前编辑规则并返回公开的执行计划。
// 编辑阶段验证登记元数据及权限。物理字段校验由运行发布入口执行。

return withHttp(() => {
    var type = 'com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController$CompileRequest'
    var signature = 'java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.integration.nifi.canvas.controller.FieldMappingController.compile(' + type + ')'
    var input = readBody(body, CompileRequest, type, signature)
    var plan = mapping.compileForEditing(input.spec()).plan()
    return ResponseEntity.ok({mode: 'ENGINE_PLAN', query: null, finalQuery: null, lookups: [], executable: true,
        schemaVerification: 'REGISTERED_ONLY',
        plan: {planVersion: '1.0', engineVersion: MappingEngine.engineVersion(),
            functionLibraryVersion: FunctionRegistry.libraryVersion(), planHash: plan.hash(),
            outputFields: plan.outputFields(), outputTypes: plan.outputTypes(),
            dictionaryGroupCount: new LinkedHashMap(plan.dictionaries()).size(),
            executionPlan: plan.document()},
        nifiComponentConfig: {processorType: 'com.linewell.dataelement.nifi.HaitongTransformRecord', planHash: plan.hash()}})
})
