{
  "properties": {},
  "id": "51600e0fb05fc4067ae3be75bc5d1a73",
  "script": null,
  "groupId": "252018678f71bd2962b4ed7e5c2b6b56",
  "name": "01.启停告警规则",
  "createTime": 1784826968306,
  "updateTime": 1784826968306,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/monitor/alert-rules/{id}/enabled",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "requestBody": "{\"enabled\":true}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "启用或停用指定告警规则。请求示例：{\"enabled\":true}；涉及 fs_alert_rule 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.启停告警规则
// 路由：PUT /dws/flowserve/monitor/alert-rules/{id}/enabled
// 业务说明：启用或停用指定告警规则。请求示例：{"enabled":true}；涉及 fs_alert_rule 等业务表；执行数据库写入并返回处理结果。
// 启停告警规则：处理 PUT /monitor/alert-rules/{id}/enabled 的核心写入或状态变更逻辑。
db.table('fs_alert_rule').where().eq('id',id).update({enabled:body.enabled?1:0})
var row = db.selectOne("""
select id,name,metric,
operator,threshold_value threshold_value, duration_minutes duration_minutes,
       notify_target notify_target,enabled,created_at created_at
from fs_alert_rule
where id=#{id}
""")
return row
