{
  "properties": {},
  "id": "4137b68ac199eff6d7eda7c84e447a50",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "10.查询流程详情",
  "createTime": 1784826967456,
  "updateTime": 1784826967456,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按流程编号读取设计器 DSL。示例：GET /dws/flowserve/flows/fl_demo；涉及 fs_flow 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.查询流程详情
// 路由：GET /dws/flowserve/flows/{id}
// 业务说明：按流程编号读取设计器 DSL。示例：GET /dws/flowserve/flows/fl_demo；涉及 fs_flow 等业务表。
// 查询流程详情：处理 GET /flows/{id} 的核心查询和组装返回数据逻辑。
import jsons
var row = db.table('fs_flow').where().eq('id',id).eq('deleted',0).selectOne()
if (row == null) { exit 404, '流程不存在' }
var flow = jsons.toJSONObject(row.dsl)
return {flow:flow,status:row.status,
  lockVersion:row.lockVersion,updatedAt:row.updatedAt+''}
