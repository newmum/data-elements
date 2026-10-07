{
  "properties": {},
  "id": "ef8ea881acfd14f2171a3cd2e50c1b87",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "04.新增编排数据源",
  "createTime": 1784826967837,
  "updateTime": 1784826967837,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"name\":\"order_db\",\"type\":\"jdbc\",\"config\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/demo\",\"username\":\"root\",\"password\":\"123456\"}}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "新增并注册运行时数据源。请求示例：{\"name\":\"order_db\",\"type\":\"jdbc\",\"config\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/demo\",\"username\":\"root\",\"password\":\"***\"}}；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.新增编排数据源
// 路由：POST /dws/flowserve/datasources
// 业务说明：新增并注册运行时数据源。请求示例：{"name":"order_db","type":"jdbc","config":{"url":"jdbc:mysql://127.0.0.1:3306/demo","username":"root","password":"***"}}；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。
// 新增编排数据源：处理 POST /datasources 的核心写入或状态变更逻辑。
import flowDataSource
var newId = flowDataSource.newId('ds')
var configJson = flowDataSource.encryptConfig(body.config)
db.table('fs_datasource').insert({id:newId,name:body.name,type:body.type,config:configJson,created_by:'flowserve'})
flowDataSource.register(body.name,body.type,body.config)
var saved = db.table('fs_datasource').where().eq('id',newId).selectOne()
saved.config = flowDataSource.maskedConfig(saved.config)
saved.createdAt = saved.createdAt+''
return saved
