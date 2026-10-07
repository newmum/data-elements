{
  "properties": {},
  "id": "428694dbe01d733ddc0b6415a6d6835b",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "06.复制流程",
  "createTime": 1784826967578,
  "updateTime": 1784826967578,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/copy",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "复制现有流程为独立草稿。示例：POST /dws/flowserve/flows/fl_demo/copy；涉及 fs_flow 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.复制流程
// 路由：POST /dws/flowserve/flows/{id}/copy
// 业务说明：复制现有流程为独立草稿。示例：POST /dws/flowserve/flows/fl_demo/copy；涉及 fs_flow 等业务表；执行数据库写入并返回处理结果。
// 复制流程：处理 POST /flows/{id}/copy 的核心写入或状态变更逻辑。
import flowDataSource
import jsons
var source = db.table('fs_flow').where().eq('id',id).eq('deleted',0).selectOne()
if (source == null) { exit 404, '流程不存在' }
var newId = flowDataSource.newId('fl')
var flow = jsons.toJSONObject(source.dsl)
flow.flowId = newId
flow.name = flow.name + ' 副本'
flow.version = 1
db.table('fs_flow').insert({id:newId,name:flow.name,flow_group:flow.group,status:'draft',
  current_version:1,dsl:jsons.toJSONStr(flow),lock_version:1,deleted:0})
return {flow:flow,status:'draft',lockVersion:1,updatedAt:new Date()+''}
