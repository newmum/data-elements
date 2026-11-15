{
  "properties": {},
  "id": "dc3a0d7cd3b04a959a71eac11398379f",
  "groupId": "56755e012bc14bec93d431716f86b595",
  "name": "01.概览",
  "path": "/summary",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/summary；只读；汇总当前租户的策略、执行状态和未处理差异；页面级业务接口。",
  "createTime": 1785340800000,
  "updateTime": 1785340800000,
  "script": null
}
================================
// 接口：01.概览
// 路由：POST /summary（完整路由由目录分组组合）
// 业务说明：POST /ods/reconciliation/summary；只读；汇总当前租户的策略、执行状态和未处理差异；页面级业务接口。

import tenantRuntime
import reconcile

// 只接受当前登录会话的业务租户；统计窗口固定为最近 30 天。
var tenantId = tenantRuntime.id()
return reconcile.summary(30)
