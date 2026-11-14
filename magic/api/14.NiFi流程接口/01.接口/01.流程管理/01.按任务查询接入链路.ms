{
  "properties": {},
  "id": "62c1acd0237345fab3b9817f6bc1b4c4",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "01.按任务查询接入链路",
  "path": "/task-chain",
  "method": "GET",
  "description": "按 accessTaskId 查询当前认证行业租户的任务链路及当前画布，保留原始响应和关联状态。仅在当前租户读取，无写入。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain
import java.lang.String
import request

// GET /nifi/api/pipelines/task-chain 接收 accessTaskId，读取当前行业租户的任务链路。
// 使用原画布响应格式，按任务定位，无写入或 NiFi 请求。

// 保留 Servlet 查询参数的缺省、空值和重复值语义。
var values = request.getValues('accessTaskId')
return taskChain(values == null ? null : String.join(',', values), false, false)
