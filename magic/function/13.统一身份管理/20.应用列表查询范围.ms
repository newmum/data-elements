{
  "properties": {},
  "id": "10eb9e1105a75214a5470e57852f430b",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "20.应用列表查询范围",
  "createTime": 1790617158595,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/app-list-context",
  "description": "根据平台会话权限与应用授权构造列表可见范围，供多个应用资源查询接口复用。",
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
// 共享函数：20.应用列表查询范围
// 业务说明：根据平台会话权限与应用授权构造列表可见范围，供多个应用资源查询接口复用。
// 函数输入：permission：待校验的功能权限编码；appId：目标业务应用标识；identityDomain：人员或公众身份域。
// 返回与副作用：根据平台会话权限与应用授权构造列表可见范围，供多个应用资源查询接口复用。
import '@/idaas/context' as context;
import '@/idaas/guard' as guard;

// 先读取平台会话的功能与对象范围，不能把其他角色的范围拼接到本功能上。
var ctx=context();
var domain=identityDomain||'workforce';
// 指定应用时直接按该应用核验；未指定时才从可见应用中寻找合法范围。
if(appId!=null) {
    return guard(permission,appId,domain);
}
for(pair in ctx.pairs) {
    if(!pair.codes.contains(permission)) {
        continue;
    }
    for(scope in pair.scopes) {
        if(scope.identity_domain==domain && ['ALL','APPLICATION'].contains(scope.scope_kind)) {
            return guard(permission,scope.scope_kind=='ALL'?null:scope.app_id,domain);
        }
    }
}
exit 403,'当前账号没有应用功能范围权限';
