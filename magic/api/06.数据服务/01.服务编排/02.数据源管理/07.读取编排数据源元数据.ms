{
  "properties": {},
  "id": "ea9f4aee04564b8d97c8daa3d4b8522c",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "07.读取编排数据源元数据",
  "createTime": 1784826968000,
  "updateTime": 1784826968000,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources/{id}/meta",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取 JDBC 数据源真实表和字段元数据。示例：GET /dws/flowserve/datasources/ds_demo/meta；涉及 fs_datasource 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.读取编排数据源元数据
// 路由：GET /dws/flowserve/datasources/{id}/meta
// 业务说明：读取 JDBC 数据源真实表和字段元数据。示例：GET /dws/flowserve/datasources/ds_demo/meta；涉及 fs_datasource 等业务表。
// 读取编排数据源元数据：处理 GET /datasources/{id}/meta 的核心查询和组装返回数据逻辑。
import flowDataSource
var source = db.table('fs_datasource').where().eq('id',id).selectOne()
if (source == null) { exit 404, '数据源不存在' }
return flowDataSource.metadata(source.name)
