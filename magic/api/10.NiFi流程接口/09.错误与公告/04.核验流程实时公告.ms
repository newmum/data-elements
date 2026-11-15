{
  "properties": {},
  "id": "nifi_pipe_error_bulletin_head_v1",
  "script": null,
  "groupId": "65f6980fe7f15f87b5db13b102130ac7",
  "name": "04.核验流程实时公告",
  "path": "/live",
  "method": "HEAD",
  "description": "按 GET 的租户校验和单流程原生公告读取确认 HTTP 状态及响应头。流程不可见返回 404，未部署返回 400。此操作只读，HEAD 不发送响应体。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFlowErrorHttp' as flowErrors

// HEAD /nifi/event/live 核验流程实时公告。 查询参数 id。
// HEAD /nifi/event/live 按 GET 的租户校验和单流程原生公告读取确认 HTTP 状态及响应头。
// 流程不可见返回 404，未部署返回 400。此操作只读，HEAD 不发送响应体。

return flowErrors(param.id, 'bulletins')
