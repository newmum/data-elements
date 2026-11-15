{
  "properties": {},
  "id": "nifi_map_template_head_v1",
  "script": null,
  "groupId": "94cec97bdd3858bdb107530b5facca22",
  "name": "04.核验映射模板详情",
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
    }
  ],
  "method": "HEAD",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiMappingTemplateDetail' as read

// HEAD /nifi/template/detail 核验映射模板详情。 查询参数 id。
// HEAD /nifi/template/detail 读取当前租户固定模板版本正文。
// GET 和 HEAD 共用 @/common/nifiMappingTemplateDetail 的业务读取。

return read(param.id)
