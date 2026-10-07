{
  "properties": {},
  "id": "ece7387ad85acf642ec762600aad847d",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "02.保存流程",
  "createTime": 1784826967486,
  "updateTime": 1784826967486,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows/{id}",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "requestBody": "{\"flow\":{\"flowId\":\"fl_demo\",\"name\":\"订单查询\"},\"lockVersion\":1}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "使用乐观锁保存设计器 DSL。请求示例：{\"flow\":{\"flowId\":\"fl_demo\"},\"lockVersion\":1}；涉及 fs_flow 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.保存流程
// 路由：PUT /dws/flowserve/flows/{id}
// 业务说明：使用乐观锁保存设计器 DSL。请求示例：{"flow":{"flowId":"fl_demo"},"lockVersion":1}；涉及 fs_flow 等业务表。
import jsons
if (body == null || body.flow == null) { exit 400, 'flow 不能为空' }
var flow = body.flow
// lock_version 防止多人同时编辑时相互覆盖。
var updated = db.table('fs_flow').where().eq('id',id).eq('deleted',0)
  .eq('lock_version',body.lockVersion)
  .update({name:flow.name,flow_group:flow.group,dsl:jsons.toJSONStr(flow),
           lock_version:body.lockVersion+1,updated_at:new Date()})
if (updated == 0) { exit 409, '保存失败：版本冲突，请刷新后重试' }
return {lockVersion:body.lockVersion+1}
