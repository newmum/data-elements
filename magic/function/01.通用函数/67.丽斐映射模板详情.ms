{
  "properties": {},
  "id": "nifi_mapping_template_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "67.丽斐映射模板详情",
  "path": "/nifiMappingTemplateDetail",
  "description": "读取当前租户固定模板版本正文。",
  "parameters": [
    {
      "name": "templateId",
      "description": "请求路径标识",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiMappingReadHttp' as readHttp
import '@/common/nifiReadMappingTemplate' as readTemplate
import org.springframework.http.ResponseEntity
import tenantRuntime

// @/common/nifiMappingTemplateDetail 为 GET 和 HEAD 提供当前租户的不可变模板内容。
// 与模板导入共用模板读取和引用权限复核。

return readHttp((text, numeric, missing, invalid) => ResponseEntity.ok(readTemplate(templateId, tenantRuntime.id())))
