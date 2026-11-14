{
  "properties": {},
  "id": "nifi_pipeline_page_head_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "09.核验流程分页",
  "path": "/page",
  "method": "HEAD",
  "description": "按 page、size、sortField、sortDir 和 q 核验认证行业租户的流程分页请求。参数校验、状态和响应头沿用 GET，按 HEAD 协议不传输响应体。只读业务库，不请求 NiFi。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineReadHttp' as readPipeline

// HEAD /nifi/api/pipelines/page 按相同查询参数核验本租户流程分页的状态和响应头。
// 共用原有校验和只读路径，无业务写入或 NiFi 调用。

// Magic 按实际 HTTP 方法匹配路由，HTTP 容器按 HEAD 协议抑制响应体。
return readPipeline(true)
