{
  "properties": {},
  "id": "nifi_pipe_error_history_get_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "12.查询流程错误历史",
  "path": "/{id}/errors",
  "method": "GET",
  "description": "读取当前认证行业租户指定流程的本地错误历史，按既有顺序返回原始快照数组。按路径 id 一次查询流程归属，流程不可见时返回空体 404。此操作只读。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors
// GET /nifi/api/pipelines/{id}/errors 读取当前认证行业租户指定流程的本地错误历史，按既有顺序返回原始快照数组。
// 按路径 id 一次查询流程归属，流程不可见时返回空体 404。此操作只读。

return flowErrors(path.id, 'history')
