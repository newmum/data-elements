{
  "properties": {},
  "id": "nifi_canvas_ping_head_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "07.核验画布接口健康",
  "path": "/ping",
  "method": "HEAD",
  "description": "返回画布 API 可达检查的 HTTP 状态和响应头，沿用登录校验，无请求参数或响应体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import org.springframework.http.ResponseEntity

// HEAD /nifi/api/ping 核验画布 API 状态和响应头，无请求参数或传输体。
// 沿用 GET 的认证和响应对象，由 Spring 按 HEAD 语义抑制响应体。

return ResponseEntity.ok({status: 'ok'})
