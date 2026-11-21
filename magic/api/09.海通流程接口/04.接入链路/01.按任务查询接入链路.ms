{
  "properties": {},
  "id": "62c1acd0237345fab3b9817f6bc1b4c4",
  "script": null,
  "groupId": "dc910e69a395588a85505154089816f4",
  "name": "01.按任务查询接入链路",
  "path": "/task",
  "method": "GET",
  "description": "按 accessTaskId 查询当前认证行业租户的任务链路及当前画布，保留原始响应和关联状态。仅在当前租户读取，无写入。",
  "parameters": [],
  "options": [
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
import java.lang.String
import request

// GET /nifi/chain/task。按接入任务定位当前行业链路，与按流程查询及原生入口共用链路校验。

var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, false)
