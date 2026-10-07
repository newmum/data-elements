{
  "properties": {},
  "id": "2532fe8d7e1cf618d32ab62d8512d4f6",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "09.查询流程版本",
  "createTime": 1784826967713,
  "updateTime": 1784826967713,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/versions",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询发布版本、脚本和 DSL。示例：GET /dws/flowserve/flows/fl_demo/versions。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.查询流程版本
// 路由：GET /dws/flowserve/flows/{id}/versions
// 业务说明：查询发布版本、脚本和 DSL。示例：GET /dws/flowserve/flows/fl_demo/versions。
// 查询流程版本：处理 GET /flows/{id}/versions 的核心查询和组装返回数据逻辑。
import jsons
var rows = db.select("""
select version,dsl,script,meta,date_format(publish_time,
        '%Y-%m-%d %H:%i:%s') publish_time, remark,status
from fs_flow_version
where flow_id=#{id}
order by version desc
""")
for (row in rows) {
  row.dsl = jsons.toJSONObject(row.dsl)
  row.meta = row.meta == null || row.meta == '' ? {} : jsons.toJSONObject(row.meta)
}
return rows
