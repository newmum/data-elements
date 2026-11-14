{
  "properties": {},
  "id": "nifi_flow_error_bulletin_head_v1",
  "script": null,
  "groupId": "nifi_flow_error_group_v1",
  "name": "04.核验流程实时公告",
  "path": "/{id}/bulletins/live",
  "method": "HEAD",
  "description": "按 GET 的租户校验和单流程原生公告读取确认 HTTP 状态及响应头。流程不可见返回 404，未部署返回 400。此操作只读，HEAD 不发送响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors
// HEAD /nifi/api/flows/{id}/bulletins/live 按 GET 的租户校验和单流程原生公告读取确认 HTTP 状态及响应头。
// 流程不可见返回 404，未部署返回 400。此操作只读，HEAD 不发送响应体。

return flowErrors(path.id, 'bulletins')
