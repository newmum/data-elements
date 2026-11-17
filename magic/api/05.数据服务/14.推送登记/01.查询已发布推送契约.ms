{
  "id": "dws_push_registry_contracts_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "01.查询已发布推送契约",
  "path": "/contracts",
  "method": "GET",
  "description": "查询当前角色可访问的数据源下已登记的数据表，以及对外公布的推送参数和字段定义。",
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

// GET /dws/push/contracts 查询当前租户已发布的契约。与 HEAD 共用 /pushregistry/read 的权限与读取实现。
return readContracts(request.getValues('datasourceId'))
