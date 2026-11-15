{
  "properties": {},
  "id": "nifi_pipeline_status_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "43.查询流程运行状态",
  "path": "/pipelines/{id}/status",
  "method": "GET",
  "description": "读取当前行业租户的画布运行状态、部署指纹和错误历史。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineStatus' as read
// GET /nifi/api/pipelines/{id}/status 读取当前行业租户的画布运行状态、部署指纹和错误历史。
// GET 与 HEAD 共用 @/common/nifiPipelineStatus，其中完成租户检查和响应组装。

return read(path.id)
