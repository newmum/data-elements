{
  "properties": {},
  "id": "18367b4d53bf1b20a94aa7b17255e18e",
  "script": null,
  "groupId": "252018678f71bd2962b4ed7e5c2b6b56",
  "name": "03.查询告警规则",
  "createTime": 1784826968271,
  "updateTime": 1784826968271,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/monitor/alert-rules",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询数据服务监控告警规则。示例：GET /dws/flowserve/monitor/alert-rules。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.查询告警规则
// 路由：GET /dws/flowserve/monitor/alert-rules
// 业务说明：查询数据服务监控告警规则。示例：GET /dws/flowserve/monitor/alert-rules。
// 查询告警规则：处理 GET /monitor/alert-rules 的核心查询和组装返回数据逻辑。
var rows = db.select("""
select id,name,metric,
operator,threshold_value threshold_value, duration_minutes duration_minutes,
       notify_target notify_target,enabled, date_format(created_at,
        '%Y-%m-%d %H:%i:%s') created_at
from fs_alert_rule
order by created_at desc
""")
return rows
