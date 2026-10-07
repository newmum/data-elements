{
  "properties": {},
  "id": "e7c9a88d31e9e0f8d123b1cefd8d17db",
  "script": null,
  "groupId": "c1b0007a981f09175abefdcb0770ca37",
  "name": "01.查询编排审计日志",
  "createTime": 1784826968402,
  "updateTime": 1784826968402,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/audit",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询云梯流程及数据源操作审计记录。示例：GET /dws/flowserve/audit。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.查询编排审计日志
// 路由：GET /dws/flowserve/audit
// 业务说明：查询云梯流程及数据源操作审计记录。示例：GET /dws/flowserve/audit。
// 查询编排审计日志：处理 GET /audit 的核心查询和组装返回数据逻辑。
var rows = db.select("""
select id,action,target,detail,date_format(created_at,
        '%Y-%m-%d %H:%i:%s') created_at
from fs_audit_log
order by created_at desc
limit 200
""")
return rows
