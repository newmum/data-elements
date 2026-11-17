{
  "properties": {},
  "id": "10def1f21f6e42769e696534efbb91a9",
  "script": null,
  "groupId": "652292437ed443acba737511e3597522",
  "name": "02.系统登出",
  "createTime": 1782660713650,
  "updateTime": 1784798379882,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/logout",
  "method": "POST",
  "parameters": [],
  "options": [{"name": "access", "value": "SESSION_LOGOUT"}],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "清理当前登录会话及相关票据，记录退出结果并返回登出状态。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import auditEvents;

// POST /portal/sso/logout 系统登出。
// 应用本地会话退出：先冻结可信主体，再撤销会话，审计故障不能阻止退出。
auditEvents.logoutTenant();
return '/login';
