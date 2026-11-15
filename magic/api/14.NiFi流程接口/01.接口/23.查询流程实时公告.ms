{
  "properties": {},
  "id": "nifi_pipe_error_bulletin_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "23.查询流程实时公告",
  "path": "/pipelines/{id}/bulletins/live",
  "method": "GET",
  "description": "读取当前认证行业租户指定流程的实时错误和警告，按 NiFi 原顺序返回数组。按路径 id 一次核对流程归属，流程不可见返回 404，未部署返回 400。此操作只读。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors
// GET /nifi/api/pipelines/{id}/bulletins/live 读取当前认证行业租户指定流程的实时错误和警告，按 NiFi 原顺序返回数组。
// 按路径 id 一次核对流程归属，流程不可见返回 404，未部署返回 400。此操作只读。

return flowErrors(path.id, 'bulletins')
