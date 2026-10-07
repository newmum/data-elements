{
  "properties": {
    "gaLocalSessionAdapter": "v1"
  },
  "id": "72bece30af4d4ca998f0378d6ba69923",
  "script": null,
  "groupId": "8b441ce693f44c30af90b26d85743384",
  "name": "02.强制下线",
  "createTime": 1785640211460,
  "updateTime": 1787182300196,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/kickout",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"sessionKey\": \"SHA-256 session key from page API\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "强制下线（POST /sym/user/online/kickout）。仅允许管理当前租户会话，不允许下线当前管理会话；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 sessionKey。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.强制下线
// 路由：POST /sym/user/online/kickout
// 业务说明：强制下线（POST /sym/user/online/kickout）。仅允许管理当前租户会话，不允许下线当前管理会话；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制；使用请求参数 sessionKey。
import tenantAccounts

import cn.dev33.satoken.stp.StpUtil
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import tenantRuntime


var text = value => value == null ? '' : (value + '').trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var currentTenantId = text(tenantRuntime.id())
if (currentTenantId == '') {
  exit 401, '未获取到当前租户'
}
var sessionKey = text(body == null ? null : body.sessionKey)
if (sessionKey == '') {
  exit 400, '会话标识不能为空'
}
var logic = StpUtil.getStpLogic()
var fingerprint = token => HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest((token + '').getBytes(StandardCharsets.UTF_8)))
for (token in tenantAccounts.tokens()) {
  try {
    if (!logic.isValidToken(token) || fingerprint(token) != sessionKey) {
      continue
    }
    var tokenSession = logic.getTokenSessionByToken(token, false)
    if (tokenSession == null || text(tokenSession.get('tenantId')) != currentTenantId || (tenantAccounts.isLocal(currentTenantId) && tokenSession.get('authRealm')!='tenant-local-v1')) {
      continue
    }
    if (token == StpUtil.getTokenValue()) {
      exit 400, '不能强制下线当前会话'
    }
    logic.kickoutByTokenValue(token)
    return { kicked: true }
  } catch (e) {
    if (e.message != null && e.message.contains('不能强制下线')) {
      throw e
    }
  }
}
exit 404, '在线会话不存在、已失效或不属于当前租户'
