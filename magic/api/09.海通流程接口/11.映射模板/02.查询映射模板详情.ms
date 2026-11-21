{
  "properties": {},
  "id": "nifi_map_template_get_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "02.查询映射模板详情",
  "path": "/detail",
  "description": "读取当前租户固定模板版本正文。",
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
import '@/common/nifiMappingReadHttp' as readHttp
import '@/common/nifiReadMappingTemplate' as readTemplate
import org.springframework.http.ResponseEntity
import tenantRuntime

// GET /nifi/template/detail。按可信行业读取不可变模板内容。模板读取与导入共用同一授权逻辑。

var templateId = param.id

return readHttp((text, numeric, missing, invalid) => ResponseEntity.ok(readTemplate(templateId, tenantRuntime.id())))
