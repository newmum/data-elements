{
  "properties": {},
  "id": "b81216bf81ee41d8ad5d09526bf07703",
  "script": null,
  "groupId": "c1237871230f4f478b31fc9b2d00b13c",
  "name": "03.取消审批委托",
  "createTime": 1786654822605,
  "updateTime": 1786951315423,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "软删除当前租户内本人创建的审批委托。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime

// 软删除当前租户内本人创建的审批委托。
var id = (body?.tid || '') + ''
if (id.trim() == '') { exit -1, '委托规则ID不能为空' }
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var user = StpUtil.getLoginId() + ''
var updated = db.table('wf_approval_delegation_t').where().eq('tenant_id', tenant).eq('tid', id)
    .eq('delegator_user_id', user).eq('is_del', 0)
    .update({is_del: 1, status: 0, updated_by: user, updated_time: new Date()})
if (updated == 0) { exit -1, '委托规则不存在或无权取消' }
return true
