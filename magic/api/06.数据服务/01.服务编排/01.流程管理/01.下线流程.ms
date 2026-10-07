{
  "properties": {},
  "id": "034139b7e1590d1beeb7bd62c66f57b8",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "01.下线流程",
  "createTime": 1784826967773,
  "updateTime": 1784826967773,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/offline",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "注销已发布 Magic 路由并更新流程状态。示例：POST /dws/flowserve/flows/fl_demo/offline；涉及 fs_flow、fs_flow_version、fs_publish_record 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.下线流程
// 路由：POST /dws/flowserve/flows/{id}/offline
// 业务说明：注销已发布 Magic 路由并更新流程状态。示例：POST /dws/flowserve/flows/fl_demo/offline；涉及 fs_flow、fs_flow_version、fs_publish_record 等业务表。
// 下线流程：处理 POST /flows/{id}/offline 的核心写入或状态变更逻辑。
import flowRuntime
var record = db.selectOne("""
select magic_resource_id resource_id
from fs_publish_record
where flow_id=#{id}
    and status='published'
order by publish_time desc
limit 1
""")
if (record != null) { flowRuntime.undeploy(record.resourceId,{localNode:true}) }
db.table('fs_flow').where().eq('id',id).update({status:'offline',updated_at:new Date()})
db.table('fs_flow_version').where().eq('flow_id',id).eq('status','published').update({status:'offline'})
db.table('fs_publish_record').where().eq('flow_id',id).eq('status','published').update({status:'offline'})
return {status:'offline'}
