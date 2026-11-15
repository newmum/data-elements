{
  "properties": {},
  "id": "nifi_pipe_detail_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "27.核验流程详情",
  "path": "/pipelines/{id}",
  "method": "HEAD",
  "description": "按 GET 的租户和流程校验确认已保存画布的 HTTP 状态及响应头。id 取自路径，不可见或无法解析时返回 404。操作只读，HEAD 不发送响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineDetailHttp' as pipelineDetail
// HEAD /nifi/api/pipelines/{id} 按详情契约核验当前租户的已保存画布，返回状态与响应头。
// id 来自路径。不可见或无法解析时返回 404。无业务写入或 NiFi 请求。

return pipelineDetail(path.id)
