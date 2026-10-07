{
  "properties": {},
  "id": "0436eed8163f5ff0aa263f0cec1da6cb",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "04.发布流程",
  "createTime": 1784826967681,
  "updateTime": 1784826967681,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/publish",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"remark\":\"首次发布\",\"targetNodeId\":\"local_magic_api\",\"script\":\"return {ok:true}\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "创建版本并发布脚本到选定运行节点。请求示例含 remark、targetNodeId、script；涉及 fs_flow、fs_runtime_node、fs_flow_version、fs_publish_record 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.发布流程
// 路由：POST /dws/flowserve/flows/{id}/publish
// 业务说明：创建版本并发布脚本到选定运行节点。请求示例含 remark、targetNodeId、script；涉及 fs_flow、fs_runtime_node、fs_flow_version、fs_publish_record 等业务表。
import flowRuntime
import jsons
var flowRow = db.table('fs_flow').where().eq('id',id).eq('deleted',0).selectOne()
if (flowRow == null) { exit 404, '流程不存在' }
var flow = jsons.toJSONObject(flowRow.dsl)
var script = body == null ? null : body.script
if (script == null || script.trim() == '') { exit 400, '发布脚本不能为空' }
var node = body == null || body.targetNodeId == null
  ? db.table('fs_runtime_node').where().eq('default_node',1).eq('enabled',1).selectOne()
  : db.table('fs_runtime_node').where().eq('id',body.targetNodeId).eq('enabled',1).selectOne()
if (node == null) { exit 400, '未配置可用的运行节点' }
var nextVersion = db.selectInt("""
select coalesce(max(version), 0)+1
from fs_flow_version
where flow_id=#{id}
""")
var deployed = flowRuntime.deploy(id,flow,script,node)
// 先成功部署，再落版本与发布记录，避免数据库显示已发布但路由不存在。
db.table('fs_flow_version').insert({flow_id:id,version:nextVersion,dsl:jsons.toJSONStr(flow),
  script:script,meta:'{}',publisher:'flowserve',remark:body.remark,status:'published'})
db.table('fs_flow').where().eq('id',id).update({status:'published',current_version:nextVersion,updated_at:new Date()})
db.table('fs_publish_record').where().eq('flow_id',id).eq('status','published').update({status:'offline'})
db.table('fs_publish_record').insert({flow_id:id,version:nextVersion,runtime_node_id:node.id,
  magic_resource_id:deployed.resourceId,url:deployed.url,status:'published'})
return {version:nextVersion,url:deployed.url,status:'published',
  runtimeNodeId:node.id,runtimeNodeName:node.name}
