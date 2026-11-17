{
  "id": "dws_push_registry_head_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "05.核验已发布推送契约",
  "path": "/contracts",
  "method": "HEAD",
  "description": "按原契约查询的鉴权和数据库规则核验，响应体由 HTTP HEAD 语义抑制。",
  "parameters": [
    {
      "name": "datasourceId",
      "dataType": "String",
      "required": false,
      "description": "已登记数据源标识"
    }
  ],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/pushregistry/read' as readContracts
import request

// HEAD /dws/push/contracts 与 GET 共用 /pushregistry/read 的权限和响应对象，Spring 按 HEAD 语义抑制响应体。
return readContracts(request.getValues('datasourceId'))
