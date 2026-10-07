{
  "properties": {},
  "id": "94faaf01bd5af5e349fb1a8903db447f",
  "script": null,
  "groupId": "ac4cd10ea9538c94e502352f188a6fe7",
  "name": "03.复制编排数据源",
  "createTime": 1784826967933,
  "updateTime": 1784826967933,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/datasources/{id}/clone",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "复制数据源配置为新记录。示例：POST /dws/flowserve/datasources/ds_demo/clone；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.复制编排数据源
// 路由：POST /dws/flowserve/datasources/{id}/clone
// 业务说明：复制数据源配置为新记录。示例：POST /dws/flowserve/datasources/ds_demo/clone；涉及 fs_datasource 等业务表；执行数据库写入并返回处理结果。
// 复制编排数据源：处理 POST /datasources/{id}/clone 的核心写入或状态变更逻辑。
import flowDataSource
var source = db.table('fs_datasource').where().eq('id',id).selectOne()
if (source == null) { exit 404, '数据源不存在' }
var newId = flowDataSource.newId('ds')
var newName = source.name + '_copy'
db.table('fs_datasource').insert({id:newId,name:newName,type:source.type,config:source.config,created_by:'flowserve'})
var saved = db.table('fs_datasource').where().eq('id',newId).selectOne()
saved.config = flowDataSource.maskedConfig(saved.config)
saved.createdAt = saved.createdAt+''
return saved
