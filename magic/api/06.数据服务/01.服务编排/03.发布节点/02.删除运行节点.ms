{
  "properties": {},
  "id": "b31185413cb1bcf42cb51da20061da7c",
  "script": null,
  "groupId": "9243108b5fe0f78c8fe28aac46f7ebf3",
  "name": "02.删除运行节点",
  "createTime": 1784826968131,
  "updateTime": 1784826968131,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/runtime-nodes/{id}",
  "method": "DELETE",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "删除非本机运行节点。示例：DELETE /dws/flowserve/runtime-nodes/rt_demo；涉及 fs_runtime_node 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.删除运行节点
// 路由：DELETE /dws/flowserve/runtime-nodes/{id}
// 业务说明：删除非本机运行节点。示例：DELETE /dws/flowserve/runtime-nodes/rt_demo；涉及 fs_runtime_node 等业务表。
// 删除运行节点：处理 DELETE /runtime-nodes/{id} 的核心写入或状态变更逻辑。
db.table('fs_runtime_node').where().eq('id',id).eq('local_node',0).delete()
return {ok:true}
