{
  "properties": {},
  "id": "1af61950c8a97d6bd1c7ffe510184de0",
  "script": null,
  "groupId": "9243108b5fe0f78c8fe28aac46f7ebf3",
  "name": "05.检查运行节点",
  "createTime": 1784826968163,
  "updateTime": 1784826968163,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/runtime-nodes/{id}/health",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "检查本机或远程 Magic 节点健康状态。示例：GET /dws/flowserve/runtime-nodes/local_magic_api/health；涉及 fs_runtime_node 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.检查运行节点
// 路由：GET /dws/flowserve/runtime-nodes/{id}/health
// 业务说明：检查本机或远程 Magic 节点健康状态。示例：GET /dws/flowserve/runtime-nodes/local_magic_api/health；涉及 fs_runtime_node 等业务表。
// 检查运行节点：处理 GET /runtime-nodes/{id}/health 的核心查询和组装返回数据逻辑。
import flowRuntime
var node = db.table('fs_runtime_node').where().eq('id',id).selectOne()
if (node == null) { exit 404, '运行节点不存在' }
var result = flowRuntime.health(node)
return {nodeId:result.nodeId,ok:result.ok,
  statusCode:result.status,latencyMs:result.latencyMs,message:result.message}
