{
  "properties": {},
  "id": "3654e9c8061c4f5e9c34bb3dce0fec11",
  "script": null,
  "groupId": "dc910e69a395588a85505154089816f4",
  "name": "05.按流程核验接入链路",
  "path": "/flow",
  "method": "HEAD",
  "description": "按查询参数 id 核验当前行业租户的流程及接入链路，响应状态和头与原 GET 路由一致，按 HEAD 协议不返回响应体。",
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
import '@/common/nifiAccessTaskChainHttp' as taskChain

// HEAD /nifi/chain/flow 按流程核验接入链路。 查询参数 id。
// HEAD /nifi/chain/flow 核验路径 id 对应的本租户链路，只返回状态和响应头。
// 与 GET 共用同一权限和查询逻辑，无写入或 NiFi 请求。

// 使用路径变量，忽略查询字符串或请求头中的同名参数。
return taskChain(param.id, true, false)
