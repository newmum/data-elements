{
  "properties": {},
  "id": "nifi_pipe_error_history_head_v1",
  "script": null,
  "groupId": "65f6980fe7f15f87b5db13b102130ac7",
  "name": "02.核验流程错误历史",
  "path": "/errors",
  "method": "HEAD",
  "description": "按 GET 的租户校验和单流程历史读取确认 HTTP 状态及响应头。id 由路径传入，流程不可见时返回 404。此操作只读，HEAD 不发送响应体。",
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

// HEAD /nifi/event/errors 核验流程错误历史。 查询参数 id。
// HEAD /nifi/event/errors 按 GET 的租户校验和单流程历史读取确认 HTTP 状态及响应头。
// id 由路径传入，流程不可见时返回 404。此操作只读，HEAD 不发送响应体。

return flowErrors(param.id, 'history')
