{
  "properties": {},
  "id": "6d9943c58a59d9245e57bfab82653c2d",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "02.删除编排数据源",
  "createTime": 1784826967899,
  "updateTime": 1784826967899,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources/{id}",
  "method": "DELETE",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "删除业务数据源并注销运行时连接。示例：DELETE /dws/flowserve/datasources/ds_demo；涉及 fs_datasource 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.删除编排数据源
// 路由：DELETE /dws/flowserve/datasources/{id}
// 业务说明：删除业务数据源并注销运行时连接。示例：DELETE /dws/flowserve/datasources/ds_demo；涉及 fs_datasource 等业务表。
// 删除编排数据源：处理 DELETE /datasources/{id} 的核心写入或状态变更逻辑。
import flowDataSource
var old = db.table('fs_datasource').where().eq('id',id).selectOne()
if (old != null) { flowDataSource.unregister(old.name) }
db.table('fs_datasource').where().eq('id',id).delete()
return {ok:true}
