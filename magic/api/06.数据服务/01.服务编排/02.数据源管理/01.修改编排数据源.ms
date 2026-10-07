{
  "properties": {},
  "id": "a7231e129cab9ec4567e78167ae977bc",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "01.修改编排数据源",
  "createTime": 1784826967869,
  "updateTime": 1784826967869,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources/{id}",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "requestBody": "{\"name\":\"order_db\",\"type\":\"jdbc\",\"config\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/demo\",\"username\":\"root\",\"password\":\"******\"}}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "修改配置并刷新具名运行时数据源。请求示例与新增接口一致；使用请求参数 config、name、type；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.修改编排数据源
// 路由：PUT /dws/flowserve/datasources/{id}
// 业务说明：修改配置并刷新具名运行时数据源。请求示例与新增接口一致；使用请求参数 config、name、type；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。
// 修改编排数据源：处理 PUT /datasources/{id} 的核心写入或状态变更逻辑。
import flowDataSource
var old = db.table('fs_datasource').where().eq('id',id).selectOne()
if (old == null) { exit 404, '数据源不存在' }
var configJson = flowDataSource.mergeConfig(id,body.config)
db.table('fs_datasource').where().eq('id',id).update({name:body.name,type:body.type,config:configJson})
if (old.name != body.name) { flowDataSource.unregister(old.name) }
flowDataSource.register(body.name,body.type,configJson)
var saved = db.table('fs_datasource').where().eq('id',id).selectOne()
saved.config = flowDataSource.maskedConfig(saved.config)
saved.createdAt = saved.createdAt+''
return saved
