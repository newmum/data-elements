{
  "properties": {},
  "id": "nifi_pipe_list_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "07.查询流程列表",
  "path": "/pipelines",
  "description": "读取本租户完整的已保存流程摘要，无 NiFi 请求。",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineListHttp' as readPipelines

// GET /nifi/api/pipelines，读取本租户完整的已保存流程摘要，无 NiFi 请求。
// 无业务写入。GET、HEAD 共用 @/common/nifiPipelineListHttp。

return readPipelines()
