{
  "properties": {},
  "id": "nifi_pipe_detail_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "26.查询流程详情",
  "path": "/pipelines/{id}",
  "method": "GET",
  "description": "按路径 id 读取当前认证行业租户的已保存画布，以原始 JSON 返回设计、部署记录和保存版本。不可见或无法解析的流程返回空体 404。只读业务库，无 NiFi 请求。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineDetailHttp' as pipelineDetail
// GET /nifi/api/pipelines/{id} 读取当前行业租户的已保存流程，返回原始画布和保存版本。
// id 来自路径。流程不可见或保存内容无法解析时返回空体 404。无写入和 NiFi 请求。

return pipelineDetail(path.id)
