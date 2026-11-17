{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "portal_login_01",
  "script": null,
  "groupId": "652292437ed443acba737511e3597522",
  "name": "01.用户登录",
  "createTime": null,
  "updateTime": 1790779806604,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/login",
  "method": "POST",
  "parameters": [],
  "options": [{"name": "access", "value": "SESSION_LOGIN"}],
  "requestBody": "{\"account\": \"本地账号\", \"password\": \"<SM3密码摘要>\", \"tenantId\": \"应用运行租户ID\", \"appId\": \"本地权限应用ID\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按显式租户和应用核验本地账号、密码摘要、账号状态及统一管理来源，返回本地会话。未传租户时选择首个有效租户，登录审计失败会撤销本次会话。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import db
import auditEvents
import identityRuntime
import tenantAccounts
import com.linewell.dataelement.utils.PwdRuleUtil
import '@/idaas/receiver-account-check' as sourceCheck

// POST /portal/sso/login 用户登录。

// 未指定租户时选择控制库中首个有效租户。账号认证与来源启用检查在该租户的业务库内执行。

var loginTarget = (body?.tenantId || '') + ''
if (loginTarget == '') {
    var options = db.table('baseline.sym_tenant_t').columns('tid').where()
        .eq('status', 1).eq('is_del', 0).orderBy('sort_no', 'asc').orderBy('created_time', 'asc').select()
    if (options == null || options.size() == 0) {
        exit -1, '当前没有可用租户'
    }
    loginTarget = options[0].tid + ''
}

var input = {...body, tenantId: loginTarget}
if (!(input instanceof Map)
    || input.account != null && !(input.account instanceof String) && !(input.account instanceof Number)
    || input.password != null && !(input.password instanceof String)) {
    exit 400, '请输入有效的账号和密码'
}
// 保留旧页面提交数字账号的兼容。密码参数是前端生成的摘要，按十六进制校验。
var account = input.account == null ? '' : (input.account + '').trim()
var password = input.password == null ? '' : input.password.trim().toUpperCase()
if (loginTarget == '' || account == '' || account.length() > 50 || !password.matches('[0-9A-F]{64}')) {
    exit 400, '请输入有效的账号和密码'
}
if (!tenantAccounts.isLocal(loginTarget)) {
    exit 403, '当前租户未启用本地账号'
}

// 显式切换到已启用本地账号的租户库，并在查询中再次限定租户 ID。
var user = identityRuntime.inTenant(loginTarget, () => db.table('rm_user_t')
    .columns('id', 'password', 'status').where()
    .eq('tenant_id', loginTarget).eq('user_name', account).eq('deleted', 0).selectOne())
if (user == null || user.status != 0 || user.password == null || !PwdRuleUtil.checkPwdBySalt(password, user.password)) {
    auditEvents.authentication(loginTarget, null, 'AUTH_LOGIN_FAILED', 'FAILED', 'INVALID_CREDENTIALS')
    exit -1, '用户名或密码错误'
}
if (!identityRuntime.inTenant(loginTarget, () => sourceCheck(user.id)).allowed) {
    auditEvents.authentication(loginTarget, null, 'AUTH_LOGIN_FAILED', 'DENIED', 'SOURCE_DISABLED')
    exit -1, '此账号的统一管理来源已停用，请联系管理员或办理脱管'
}

var token = tenantAccounts.loginLocal(loginTarget, user.id, (input.appId || '') + '', account)
try {
    auditEvents.authentication(loginTarget, user.id, 'AUTH_LOGIN_SUCCEEDED', 'SUCCESS', null)
} catch (error) {
    // 登录审计失败时撤销刚签发的会话。
    auditEvents.compensateLogin()
    throw error
}
return {token: token, url: '/home', authRealm: 'tenant-local-v1', tenantId: loginTarget}
