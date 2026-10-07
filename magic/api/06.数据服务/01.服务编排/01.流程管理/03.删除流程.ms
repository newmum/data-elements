{
  "properties": {},
  "id": "d50275ba88c6ec91747f783379ccaada",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "03.删除流程",
  "createTime": 1784826967524,
  "updateTime": 1784826967524,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}",
  "method": "DELETE",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "软删除流程并下线已发布服务。示例：DELETE /dws/flowserve/flows/fl_demo；涉及 fs_flow、fs_publish_record 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.删除流程
// 路由：DELETE /dws/flowserve/flows/{id}
// 业务说明：软删除流程并下线已发布服务。示例：DELETE /dws/flowserve/flows/fl_demo；涉及 fs_flow、fs_publish_record 等业务表。
// 删除流程：处理 DELETE /flows/{id} 的核心写入或状态变更逻辑。
import flowRuntime
var record = db.selectOne("""
select magic_resource_id as resource_id
from fs_publish_record
where flow_id=#{id}
    and status='published'
order by publish_time desc
limit 1
""")
if (record != null) { flowRuntime.undeploy(record.resourceId,{localNode:true}) }
db.table('fs_flow').where().eq('id',id).update({deleted:1,status:'deleted',updated_at:new Date()})
db.table('fs_publish_record').where().eq('flow_id',id).update({status:'deleted'})
return {ok:true}
