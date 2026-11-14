{
  "properties": {},
  "id": "nifi_pipe_error_history_head_v1",
  "script": null,
  "groupId": "40ba1da5548648fdad7c449eb97fc033",
  "name": "13.核验流程错误历史",
  "path": "/{id}/errors",
  "method": "HEAD",
  "description": "按 GET 的租户校验和单流程历史读取确认 HTTP 状态及响应头。id 由路径传入，流程不可见时返回 404。此操作只读，HEAD 不发送响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors
// HEAD /nifi/api/pipelines/{id}/errors 按 GET 的租户校验和单流程历史读取确认 HTTP 状态及响应头。
// id 由路径传入，流程不可见时返回 404。此操作只读，HEAD 不发送响应体。

return flowErrors(path.id, 'history')
