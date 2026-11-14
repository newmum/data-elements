{
  "properties": {},
  "id": "nifi_runtime_overview_head_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "11.核验流程运行总览",
  "path": "/runtime-overview",
  "method": "HEAD",
  "description": "按 sortField 和 sortDir 核验认证行业租户的流程运行总览请求。参数校验、状态和响应头沿用 GET，按 HEAD 协议不传输响应体。只读业务库，不请求 NiFi。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineReadHttp' as readPipeline

// HEAD /nifi/api/pipelines/runtime-overview 按相同排序参数核验本租户总览状态和响应头。
// 查询当前租户的已保存状态，无业务写入或 NiFi 调用。

// Magic 按实际 HTTP 方法匹配路由，HTTP 容器按 HEAD 协议抑制响应体。
return readPipeline(false)
