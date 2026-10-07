{
  "properties": {},
  "id": "c98ee7cf1481482586080eb71cf423d5",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "02.单点登录",
  "createTime": 1782660713568,
  "updateTime": 1784459658799,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/ssoLogin",
  "method": "GET",
  "parameters": [
    {
      "name": "ticket",
      "value": "ZT-2063843563657363457",
      "description": "真实数据示例参数：ticket",
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
      "value": "测试",
      "description": "真实数据示例参数：code",
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
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"org.ssssssss.script.exception.MagicScriptException: 400 Bad Request: \\\"{\\\"error\\\":\\\"invalid_request\\\",\\\"error_description\\\":\\\"ticket已失效\\\"}\\\" at Row:31~31,Col:6~11\\n\\n    .post()\\r\\n     ^^^^^^ \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "核验当前租户的登录请求，区分公安本地账号与其他租户原有认证分支；登录成功后建立会话并记录登录结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.单点登录
// 路由：GET /portal/ssoLogin
// 业务说明：核验当前租户的登录请求，区分公安本地账号与其他租户原有认证分支；登录成功后建立会话并记录登录结果。
import tenantAccounts
import cn.hutool.core.util.StrUtil
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil;
import jsons
import '@/common/configData' as configData
import systemConfig
import http
import log
import request
import cn.hutool.http.useragent.UserAgentUtil
import cn.hutool.http.useragent.UserAgent
import tenantRuntime


// 入参判断
if (!ticket && !code){
  // 参数或业务状态不满足要求时立即终止请求。
  exit 500, "无效权限"
}

// 1.根据 ticket 获取 access_token
var url = ``
if (ticket){
  url = `${configData().ssoServerUrl}/oauth/token?client_id=${configData().client_id}&client_secret=${systemConfig.legacySsoClientSecret()}&grant_type=ticket&ticket=${ticket}&scope=all`
}

if (code){
  url = `${configData().ssoServerUrl}/oauth/token?client_id=${configData().client_id}&client_secret=${systemConfig.legacySsoClientSecret()}&grant_type=authorization_code&code=${code}&scope=all`
}

// try{
  // return url
  var {access_token} =  http.connect(url)
    .post()
    .getBody()
// }catch(e) {
//   log.info("获取token令牌报错")
// }


if(access_token == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500,"获取token令牌报错"
}

// 2. 根据 access_token 获取用户信息 
var url = `${configData().ssoServerUrl}/user/current?access_token=${access_token}`
var user =  http.connect(url)
    .get()
    .getBody()


// 3. SSO 账号必须拥有控制库中的有效租户成员关系。
var appId = configData().appId;
var requestedTenantId = tenantRuntime.requireLoginMembership(user.id)
if(tenantAccounts.isLocal(requestedTenantId)) {exit 403,'当前租户使用独立账号，请通过本地登录页面登录';}

UserAgent ua = UserAgentUtil.parse(request.getHeaders("User-Agent")[0])
StpUtil.login(user.id)
var loginTenant = tenantRuntime.activateLoginTenant(requestedTenantId)
var tenantId = loginTenant.tenantId
var saToken;
saToken = StpUtil.getTokenValueByLoginId(user.id)

db.table('log_user_login_t').insert({
    tid: IdUtil.getSnowflakeNextIdStr(),
    account: user.userName,
    is_success: 1,
    token: saToken,
    ip: request.getClientIP(),
    browser: ua.getBrowser().toString(),
    os: ua.getOs().toString(),
    created_by: user.id,
    updated_by: user.id,
    created_time: new Date(),
    updated_time: new Date(),
    is_del: 0,
    tenant_id: tenantId
})
user.appId = appId;
tenantAccounts.session().set("user", user);

//用户统一token获取
tenantAccounts.session().set("name", user.userName);
tenantAccounts.session().set("appId", appId);

// 返回当前业务分支的标准结果。
return {
    token: saToken
}
