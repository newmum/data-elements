{
  "properties": {},
  "id": "1c698cf2c43db55cad6c2b2a0d0ec740",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "05.回滚流程版本",
  "createTime": 1784826967742,
  "updateTime": 1784826967742,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}/rollback",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"version\":1}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "将指定历史版本恢复为当前草稿。请求示例：{\"version\":1}；涉及 fs_flow_version、fs_flow 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.回滚流程版本
// 路由：POST /dws/flowserve/flows/{id}/rollback
// 业务说明：将指定历史版本恢复为当前草稿。请求示例：{"version":1}；涉及 fs_flow_version、fs_flow 等业务表；执行数据库写入并返回处理结果。
// 回滚流程版本：处理 POST /flows/{id}/rollback 的核心写入或状态变更逻辑。
var version = db.table('fs_flow_version').where().eq('flow_id',id).eq('version',body.version).selectOne()
if (version == null) { exit 404, '版本不存在' }
var current = db.table('fs_flow').where().eq('id',id).selectOne()
db.table('fs_flow').where().eq('id',id).update({dsl:version.dsl,current_version:body.version,
  status:'draft',lock_version:current.lockVersion+1,updated_at:new Date()})
return {lockVersion:current.lockVersion+1,status:'draft'}
