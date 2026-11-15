{
  "properties": {},
  "id": "nifi_pipeline_page_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "05.核验流程分页",
  "path": "/page",
  "method": "HEAD",
  "description": "按 page、size、sortField、sortDir 和 q 核验认证行业租户的流程分页请求。参数校验、状态和响应头沿用 GET，按 HEAD 协议不传输响应体。只读业务库，不请求 NiFi。 GET 与 HEAD 共用 @/common/nifiPipelinePage。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelinePage' as readPipelinePage

// HEAD /nifi/flow/page 核验流程分页。
// HEAD /nifi/flow/page 按 GET 的分页参数校验和数据库查询返回状态与响应头，无响应体。
// GET 与 HEAD 共用 @/common/nifiPipelinePage，无写入或 NiFi 调用。

return readPipelinePage()
