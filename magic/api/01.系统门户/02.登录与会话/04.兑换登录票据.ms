{
  "properties": {},
  "id": "c98ee7cf1481482586080eb71cf423d5",
  "script": null,
  "groupId": "652292437ed443acba737511e3597522",
  "name": "04.兑换登录票据",
  "createTime": 1782660713568,
  "updateTime": 1784459658799,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/redeem",
  "method": "GET",
  "parameters": [
    {
      "name": "ticket",
      "value": null,
      "description": "上游 SSO 一次性登录票据",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "code",
      "value": null,
      "description": "上游 SSO 授权码",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [
    {
      "name": "access",
      "value": "LEGACY_SSO_REDEEM"
    }
  ],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "使用上游 SSO 签发的票据或授权码建立本地会话，并记录登录结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import auditEvents;
import tenantAccounts;
import tenantRuntime;
import legacySso;
import cn.dev33.satoken.stp.StpUtil;

// 从框架取得已通过上游验证的身份，核对租户成员关系后建立并审计本地会话。
var verified = legacySso.verifiedRedemption();
var user = verified.user;
var requestedTenantId = tenantRuntime.requireLoginMembership(user.id);
if (tenantAccounts.isLocal(requestedTenantId)) {
    exit 403, '当前租户使用独立账号，请通过本地登录页面登录';
}

StpUtil.login(user.id);
var tenantId = tenantRuntime.activateLoginTenant(requestedTenantId).tenantId;
var token = StpUtil.getTokenValueByLoginId(user.id);
user.appId = verified.appId;
var session = tenantAccounts.session();
session.set('user', user);
session.set('name', user.userName);
session.set('appId', verified.appId);
try {
    auditEvents.authentication(tenantId, user.id, 'AUTH_LOGIN_SUCCEEDED', 'SUCCESS', null);
} catch (error) {
    auditEvents.compensateLogin();
    throw error;
}
return {token: token};
