{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "tenant_assign_user_magic_01",
  "script": null,
  "groupId": "d28618bcdcca47279ce2c6f513002948",
  "name": "04.保存用户租户关联",
  "createTime": 1787162475946,
  "updateTime": 1787184457941,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/assign-user",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"targetTenantId\": \"目标租户ID\",\n  \"userId\": \"用户ID\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 targetTenantId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.保存用户租户关联
// 路由：POST /sym/tenant/assign-user
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 targetTenantId。
import tenantRuntime;
var current=tenantRuntime.id();var target=(body?.targetTenantId||current)+'';
if(target!=current) {exit 403,'禁止跨应用维护账号';}
exit 409,'账号直接归属本应用，请通过用户管理维护，无需配置共享成员关系';
