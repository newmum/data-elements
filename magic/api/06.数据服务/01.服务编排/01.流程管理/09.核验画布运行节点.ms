{
  "properties": {},
  "id": "flowserve_canvas_nodes_head_v1",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "09.核验画布运行节点",
  "path": "/runtime-nodes",
  "method": "HEAD",
  "description": "核验当前已认证行业租户的运行节点列表，沿用 GET 的节点上限、HTTP 状态和响应头，无请求参数或传输体。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/flowServeCanvasReadHttp' as readCanvas

// HEAD /dws/flowserve/runtime-nodes 沿用列表 GET 的认证与节点上限校验。
// 由 Spring 返回原 HTTP 状态与响应头并抑制响应体，业务库只读。

return readCanvas(false, null)
