{
  "properties": {},
  "id": "nifi_pipe_runtime_list_head_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "20.核验流程运行列表",
  "path": "/runtime-list",
  "description": "按运行列表的完整契约返回状态和响应头，无响应体。",
  "method": "HEAD",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineRuntimeListHttp' as readPipelines

// HEAD /nifi/api/pipelines/runtime-list，按运行列表的完整契约返回状态和响应头，无响应体。
// 无业务写入。GET、HEAD 共用 @/common/nifiPipelineRuntimeListHttp。

return readPipelines()
