{
  "properties": {},
  "id": "flowserve_canvas_flow_head_v1",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "07.核验画布流程详情",
  "path": "/detail",
  "method": "HEAD",
  "description": "按流程标识核验云梯画布详情，沿用 GET 的租户、服务范围、参数校验及 HTTP 状态和响应头，无传输体。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的编排流程标识"
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
import '@/common/flowServeCanvasReadHttp' as readCanvas

// HEAD /dws/flowserve/detail?id={id} 沿用详情 GET 的认证、范围和校验。
// 由 Spring 返回原 HTTP 状态与响应头并抑制响应体，业务库只读。

return readCanvas(true, param.id)
