{
  "properties": {},
  "id": "f64eca6665d34a38b3f67fb1d7cf2703",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "05.查询编排数据源",
  "createTime": 1784826967806,
  "updateTime": 1784826967806,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询云梯编排可使用的数据源，敏感配置统一掩码。示例：GET /dws/flowserve/datasources。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.查询编排数据源
// 路由：GET /dws/flowserve/datasources
// 业务说明：查询云梯编排可使用的数据源，敏感配置统一掩码。示例：GET /dws/flowserve/datasources。
// 查询编排数据源：处理 GET /datasources 的核心查询和组装返回数据逻辑。
import flowDataSource
var rows = db.select("""
select id,name,type,config,date_format(created_at, '%Y-%m-%d %H:%i:%s') created_at
from fs_datasource
order by created_at desc
""")
for (row in rows) { row.config = flowDataSource.maskedConfig(row.config) }
return rows
