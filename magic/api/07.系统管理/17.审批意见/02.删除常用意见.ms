{
  "properties": {},
  "id": "88f84dd78cbb42899740f49437a41148",
  "script": null,
  "groupId": "08e1a2c4867d44009e15a322d2aea3ee",
  "name": "02.删除常用意见",
  "createTime": 1782955344444,
  "updateTime": 1784446270672,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "软删除当前租户内本人保存的常用审批意见。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime

// 软删除当前租户内本人保存的常用审批意见。
var id = (body?.tid || '') + ''
if (id.trim() == '') { exit -1, '常用意见ID不能为空' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var user = StpUtil.getLoginId() + ''
return db.table('flow_suggestion').where().eq('tenant_id', tenant).eq('tid', id)
    .eq('create_by', user).eq('is_del', 0).update({is_del: 1, update_by: user, update_time: new Date()})
