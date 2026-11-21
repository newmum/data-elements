{
  "properties": {},
  "id": "4c8f2cc3d01446059d4d6cd4fd19570a",
  "script": null,
  "groupId": "dc910e69a395588a85505154089816f4",
  "name": "02.按流程查询接入链路",
  "path": "/flow",
  "method": "GET",
  "description": "按查询参数 id 查询当前认证行业租户的流程及其接入链路，保留画布的原始 JSON 与关联状态。仅在当前租户读取，无写入。",
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
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain

// GET /nifi/chain/flow。按已保存画布定位当前行业链路，与按任务查询及原生入口共用链路校验。

return taskChain(param.id, true, false)
