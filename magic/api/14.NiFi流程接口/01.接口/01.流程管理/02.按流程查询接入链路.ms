{
  "properties": {},
  "id": "4c8f2cc3d01446059d4d6cd4fd19570a",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "02.按流程查询接入链路",
  "path": "/{id}/chain",
  "method": "GET",
  "description": "按路径 id 查询当前认证行业租户的流程及其接入链路，保留画布的原始 JSON 与关联状态。仅在当前租户读取，无写入。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiAccessTaskChainHttp' as taskChain

// GET /nifi/api/pipelines/{id}/chain 接收路径 id，从当前行业租户的流程定位其接入链路。
// 使用原画布响应格式，按流程定位，无写入或 NiFi 请求。

return taskChain(path.id, true, false)
