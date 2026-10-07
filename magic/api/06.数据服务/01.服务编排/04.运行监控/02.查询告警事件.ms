{
  "properties": {},
  "id": "7deb41d78fe4d9a46457fe2a5b4a33ac",
  "script": null,
  "groupId": "252018678f71bd2962b4ed7e5c2b6b56",
  "name": "02.查询告警事件",
  "createTime": 1784826968370,
  "updateTime": 1784826968370,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/monitor/alert-events",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询最近告警事件；limit 默认 50。示例：GET /dws/flowserve/monitor/alert-events?limit=50。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.查询告警事件
// 路由：GET /dws/flowserve/monitor/alert-events
// 业务说明：查询最近告警事件；limit 默认 50。示例：GET /dws/flowserve/monitor/alert-events?limit=50。
// 查询告警事件：处理 GET /monitor/alert-events 的核心查询和组装返回数据逻辑。
var rowLimit = limit == null ? 50 : Math.min(200,Math.max(1,limit::int))
var rows = db.select("""
select id,rule_id rule_id,rule_name rule_name,metric, actual_value actual_value,
       threshold_value threshold_value,message, notify_target notify_target,status,
       date_format(triggered_at, '%Y-%m-%d %H:%i:%s') triggered_at
from fs_alert_event
order by triggered_at desc
limit #{rowLimit}
""")
return rows
