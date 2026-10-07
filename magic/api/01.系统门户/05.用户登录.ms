{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "portal_login_01",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "05.用户登录",
  "createTime": null,
  "updateTime": 1790779806604,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/login",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"account\": \"本地账号\", \"password\": \"<SM3密码摘要>\", \"tenantId\": \"应用运行租户ID\", \"appId\": \"本地权限应用ID\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 tenantId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.用户登录
// 路由：POST /portal/login
// 业务说明：应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 tenantId。

import '@/tenant-account/login' as localLogin
var loginTarget=(body?.tenantId||'')+'';
if(loginTarget=='') {
 var options=db.table('baseline.sym_tenant_t').columns('tid').where().eq('status',1).eq('is_del',0).orderBy('sort_no','asc').orderBy('created_time','asc').select();
 if(options==null || options.size()==0) {exit -1,'当前没有可用租户';}
 loginTarget=options[0].tid+'';
}
return localLogin({...body,tenantId:loginTarget});
