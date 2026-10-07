{
  "properties": {},
  "id": "2bf4c74dcb665a279d05d04b96bf21ba",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "02.功能与对象范围校验",
  "createTime": 1790612165860,
  "updateTime": 1790946368174,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/guard",
  "description": "按功能编码、应用和身份域校验操作人的权限及对象范围；越权时拒绝请求，成功时返回可用于后续查询的上下文。",
  "parameters": [
    {
      "name": "permission",
      "value": null,
      "description": "待校验的功能权限编码",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "appId",
      "value": null,
      "description": "目标业务应用标识",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "identityDomain",
      "value": null,
      "description": "人员或公众身份域",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：02.功能与对象范围校验
// 业务说明：按功能编码、应用和身份域校验操作人的权限及对象范围；越权时拒绝请求，成功时返回可用于后续查询的上下文。
// 函数输入：permission：待校验的功能权限编码；appId：目标业务应用标识；identityDomain：人员或公众身份域。
// 返回与副作用：按功能编码、应用和身份域校验操作人的权限及对象范围；越权时拒绝请求，成功时返回可用于后续查询的上下文。
import '@/idaas/context' as context;
import '@/idaas/audit' as audit;

// 功能与范围必须来自同一角色分配，防止只读全域范围扩大局部写权限。
var ctx=context();
var domain=identityDomain||'workforce';
if(!['workforce','public'].contains(domain)) {
    exit 400,'身份域不正确';
}
if(ctx.account.must_change_password==1) {
    exit 403,'请先在个人中心修改初始密码';
}
for(pair in ctx.pairs) {
    if(!pair.codes.contains(permission)) {
        continue;
    }
    for(scope in pair.scopes) {
        if(scope.identity_domain!=domain) {
            continue;
        }
        if(scope.scope_kind=='ALL' || (appId!=null && scope.scope_kind=='APPLICATION' && scope.app_id==appId)) {
            ctx.requiredPermission=permission;
            ctx.requiredAppId=appId;
            ctx.requiredDomain=domain;
            return ctx;
        }
    }
}
audit(ctx,'DENIED',permission,appId,'SCOPE_OR_PERMISSION',null);
exit 403,'当前平台账号无此功能或对象范围权限';
