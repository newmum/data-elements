{
  "properties": {},
  "id": "750e867eccab5a73439927cd6ecd6019",
  "script": null,
  "groupId": "252018678f71bd2962b4ed7e5c2b6b56",
  "name": "04.查询调用日志",
  "createTime": 1784826968236,
  "updateTime": 1784826968236,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/monitor/calls",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询最近调用日志；limit 默认 50。示例：GET /dws/flowserve/monitor/calls?limit=50。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.查询调用日志
// 路由：GET /dws/flowserve/monitor/calls
// 业务说明：查询最近调用日志；limit 默认 50。示例：GET /dws/flowserve/monitor/calls?limit=50。
// 查询调用日志：处理 GET /monitor/calls 的核心查询和组装返回数据逻辑。
var rowLimit = limit == null ? 50 : Math.min(500,Math.max(1,limit::int))
var rows = db.select("""
select id,flow_id flow_id,service_name service_name,method,path,status, success,duration_ms duration_ms,
       error_message error_message, date_format(created_at,
        '%Y-%m-%d %H:%i:%s') created_at
from fs_call_log
order by created_at desc
limit #{rowLimit}
""")
return rows
