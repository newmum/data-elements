{
  "properties": {},
  "id": "e847115bc8794012a94eda07750a7403",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "04.按任务核验接入链路",
  "path": "/task-chain",
  "method": "HEAD",
  "description": "按 accessTaskId 核验当前行业租户的任务链路，响应状态和头与原 GET 路由一致，按 HEAD 协议不返回响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain
import java.lang.String
import request

// HEAD /nifi/api/pipelines/task-chain 核验 accessTaskId 对应的本租户链路，只返回状态和响应头。
// 与 GET 共用同一权限和查询逻辑，无写入或 NiFi 请求。

// Magic 按实际 HTTP 方法查找脚本，显式登记 HEAD 以保留客户端的探查请求。
var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, false)
