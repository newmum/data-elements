{
  "properties": {},
  "id": "nifi_attribute_sync_head_v1",
  "script": null,
  "groupId": "32618128861a58318b8524b74df21a23",
  "name": "03.核验原生属性差异",
  "path": "/attrs",
  "method": "HEAD",
  "description": "读取当前行业租户流程的原生属性差异及组件版本。",
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
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAttributePreview' as preview

// HEAD /nifi/node/attrs 核验原生属性差异。 查询参数 id。
// HEAD /nifi/node/attrs 读取当前行业租户流程的原生属性差异及组件版本。
// GET 和 HEAD 共用 @/common/nifiAttributePreview 的租户校验及原生快照。

return preview(param.id)
