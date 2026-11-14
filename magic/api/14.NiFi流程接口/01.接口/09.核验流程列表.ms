{
  "properties": {},
  "id": "nifi_pipe_list_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "09.核验流程列表",
  "path": "/pipelines",
  "description": "核验本租户流程列表的状态与响应头，无响应体。",
  "method": "HEAD",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineListHttp' as readPipelines

// HEAD /nifi/api/pipelines，核验本租户流程列表的状态与响应头，无响应体。
// 无业务写入。GET、HEAD 共用 @/common/nifiPipelineListHttp。

return readPipelines()
