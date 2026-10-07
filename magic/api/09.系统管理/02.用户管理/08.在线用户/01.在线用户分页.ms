{
  "properties": {
    "gaLocalSessionAdapter": "v1"
  },
  "id": "6743b6c46a384a7c8881d0a582a3947e",
  "script": null,
  "groupId": "8b441ce693f44c30af90b26d85743384",
  "name": "01.在线用户分页",
  "createTime": 1785640211460,
  "updateTime": 1787182300236,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "在线用户分页（POST /sym/user/online/page）。读取当前租户 Sa-Token/Redis 会话，不返回可复用登录令牌；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 pageNum、pageSize、keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.在线用户分页
// 路由：POST /sym/user/online/page
// 业务说明：在线用户分页（POST /sym/user/online/page）。读取当前租户 Sa-Token/Redis 会话，不返回可复用登录令牌；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 pageNum、pageSize、keyword。
import tenantAccounts

import '@/tenant-account/users' as localUsers
import cn.dev33.satoken.stp.StpUtil
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import tenantRuntime


var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
  for (key in keys) {
    if (row != null && row[key] != null) {
      return row[key]
    }
  }
  return null
}
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var currentTenantId = text(tenantRuntime.id())
if (currentTenantId == '') {
  exit 401, '未获取到当前租户'
}
var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 20 : body.pageSize::int
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : (pageSize > 200 ? 200 : pageSize)
var keyword = text(body == null ? null : body.keyword).toLowerCase()
var logic = StpUtil.getStpLogic()
var tokenValues = tenantAccounts.tokens()
var sessionRows = []
var loginIds = []
var fingerprint = token => HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest((token + '').getBytes(StandardCharsets.UTF_8)))
for (token in tokenValues) {
  try {
    if (!logic.isValidToken(token)) {
      continue
    }
    var accountId = logic.getLoginIdByTokenNotThinkFreeze(token)
    if (accountId == null) {
      continue
    }
    var tokenSession = logic.getTokenSessionByToken(token, false)
    if (tokenSession == null || text(tokenSession.get('tenantId')) != currentTenantId || (tenantAccounts.isLocal(currentTenantId) && tokenSession.get('authRealm')!='tenant-local-v1')) {
      continue
    }
    var terminal = logic.getTerminalInfoByToken(token)
    var loginTime = terminal == null ? tokenSession.getCreateTime() : terminal.getCreateTime()
    var activeTime = 0
    try {
      activeTime = logic.getTokenLastActiveTime(token)
    } catch (ignored) { }
    var row = { sessionKey: fingerprint(token), tokenValue: token, loginId: text(accountId), deviceType: terminal == null ? null : terminal.getDeviceType(), deviceId: terminal == null ? null : terminal.getDeviceId(), loginTime: loginTime, lastActiveTime: activeTime, timeoutSeconds: logic.getTokenTimeout(token), current: token == StpUtil.getTokenValue() }
    sessionRows.add(row)
    if (!loginIds.contains(row.loginId)) {
      loginIds.add(row.loginId)
    }
  } catch (ignored) { }
}
var userMap = {}
if (loginIds.size() > 0) {
  for (user in tenantAccounts.page(currentTenantId,1,200,loginIds,null,null).list) {
    userMap[text(rowValue(user, ['id']))] = user
  }
}
var result = []
for (session in sessionRows) {
  var user = userMap[session.loginId]
  session.userName = user == null ? session.loginId : rowValue(user, ['userName','user_name'])
  session.realName = user == null ? session.loginId : rowValue(user, ['realName','real_name'])
  var search = (text(session.loginId) + ' ' + text(session.userName) + ' ' + text(session.realName) + ' ' + text(session.deviceType) + ' ' + text(session.deviceId)).toLowerCase()
  if (keyword == '' || search.contains(keyword)) {
    session.remove('tokenValue')
    result.add(session)
  }
}
var from = (pageNum - 1) * pageSize
if (from > result.size()) {
  from = result.size()
}
var to = from + pageSize
if (to > result.size()) {
  to = result.size()
}
return { list: result.subList(from, to), total: result.size(), pageNum: pageNum, pageSize: pageSize }
