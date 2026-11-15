{
  "properties": {},
  "id": "nifi_pipe_runtime_list_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "03.查询流程运行列表",
  "path": "/list",
  "description": "读取本租户已保存流程及实际运行指标，共用一次 NiFi 根快照。",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineRuntimeListHttp' as readPipelines

// GET /nifi/run/list 查询流程运行列表。
// GET /nifi/run/list，读取本租户已保存流程及实际运行指标，共用一次 NiFi 根快照。
// 无业务写入。GET、HEAD 共用 @/common/nifiPipelineRuntimeListHttp。

return readPipelines()
