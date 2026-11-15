{
  "properties": {},
  "id": "flowserve_canvas_nodes_get_v1",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "08.查询画布运行节点",
  "path": "/nodes",
  "method": "GET",
  "description": "读取当前已认证行业租户的云梯运行节点配置，无请求参数。保留原有节点顺序和最多 100 项边界，只返回部署令牌是否设置，无外部探测或业务写入。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/flowServeCanvasReadHttp' as readCanvas

// GET /dws/flowserve/nodes 一次读取当前租户的运行节点，无请求参数。
// 返回有序节点配置及部署令牌设置状态，最多 100 项，业务库只读。

return readCanvas(false, null)
