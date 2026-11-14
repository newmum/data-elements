{
  "properties": {},
  "id": "nifi_canvas_ping_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "06.查询画布接口健康",
  "path": "/ping",
  "method": "GET",
  "description": "返回画布 API 的固定可达状态。沿用现有登录校验，无输入，无数据库或原生 NiFi 请求。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import org.springframework.http.ResponseEntity

// GET /nifi/api/ping 核验画布 API 能否响应已认证会话，无请求参数。
// 返回原始 JSON 状态。该接口不访问业务库，也不检测原生 NiFi 节点。

return ResponseEntity.ok({status: 'ok'})
