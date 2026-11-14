{
  "properties": {},
  "id": "nifi_pipe_runtime_list_get_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "19.查询流程运行列表",
  "path": "/runtime-list",
  "description": "读取本租户已保存流程及实际运行指标，共用一次 NiFi 根快照。",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineRuntimeListHttp' as readPipelines

// GET /nifi/api/pipelines/runtime-list，读取本租户已保存流程及实际运行指标，共用一次 NiFi 根快照。
// 无业务写入。GET、HEAD 共用 @/common/nifiPipelineRuntimeListHttp。

return readPipelines()
