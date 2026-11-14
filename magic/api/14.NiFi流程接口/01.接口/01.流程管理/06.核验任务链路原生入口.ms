{
  "properties": {},
  "id": "bf4107f30b0b479fabd2c236c186e340",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "06.核验任务链路原生入口",
  "path": "/task-chain/native",
  "method": "HEAD",
  "description": "按 accessTaskId 核验当前行业租户可访问的 NiFi 原生入口，需要时读取一次根组状态，按 HEAD 协议不返回响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain
import java.lang.String
import request

// HEAD /nifi/api/pipelines/task-chain/native 核验 accessTaskId 对应的本租户原生入口，只返回状态和响应头。
// 与 GET 共用同一权限和查询逻辑，按需读取 NiFi，无写入。

// Magic 按实际 HTTP 方法查找脚本，显式登记 HEAD 以保留客户端的探查请求。
var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, true)
