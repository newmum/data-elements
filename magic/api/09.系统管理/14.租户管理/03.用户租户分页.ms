{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "tenant_user_page_magic_01",
  "script": null,
  "groupId": "d28618bcdcca47279ce2c6f513002948",
  "name": "03.用户租户分页",
  "createTime": 1787162475917,
  "updateTime": 1787184457874,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/user-page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"targetTenantId\": \"目标租户ID\",\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 targetTenantId、keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.用户租户分页
// 路由：POST /sym/tenant/user-page
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 targetTenantId、keyword。
import tenantAccounts;
import tenantRuntime;
var current=tenantRuntime.id();var target=(body?.targetTenantId||current)+'';
if(target!=current) {exit 403,'本地账号只能查询所属应用';}
var rows=tenantAccounts.list(current,body?.keyword);
return {list:rows.map(u=>({userId:u.id,userName:u.userName,realName:u.realName,phone:u.phone,assigned:true,defaultTenant:true})),total:rows.size(),accountSource:'LOCAL'};
