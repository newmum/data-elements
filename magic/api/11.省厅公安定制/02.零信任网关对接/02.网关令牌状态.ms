{
  "properties": {},
  "id": "ga_ztgw_token_02",
  "script": null,
  "groupId": "ga_ztgw_group_02",
  "name": "02.网关令牌状态",
  "createTime": 1789560000000,
  "updateTime": 1789560000000,
  "path": "/token-status",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "零信任 OAuth2 client_credentials Token 调试接口。支持 dryRun，不读取系统配置、Redis 或环境变量。POST /ga/gateway/token-status。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.网关令牌状态
// 路由：POST /gateway/token-status
// 业务说明：零信任 OAuth2 client_credentials Token 调试接口。支持 dryRun，不读取系统配置、Redis 或环境变量。POST /ga/gateway/token-status。
// 零信任 OAuth2 client_credentials Token 调试接口。
// 调试阶段不缓存 Token，避免 Redis、系统配置和环境变量干扰问题定位。
// 正式投产前，将 gatewayConfig 迁移到受控配置，并恢复 Redis 缓存。
import tenantRuntime
import cn.hutool.core.codec.Base64
import http

var gatewayConfig = {
  tokenUrl: '',
  clientId: '',
  clientSecret: ''
}

var tenantId = tenantRuntime.id()
var requestBody = body == null ? {} : body
var dryRun = requestBody.dryRun == true
var missing = []
if ((gatewayConfig.tokenUrl || '').trim() == '') { missing.add('gatewayConfig.tokenUrl') }
if ((gatewayConfig.clientId || '').trim() == '') { missing.add('gatewayConfig.clientId') }
if ((gatewayConfig.clientSecret || '').trim() == '') { missing.add('gatewayConfig.clientSecret') }

if (dryRun) {
  return {
    tenantId: tenantId,
    dryRun: true,
    configured: missing.size() == 0,
    missingVariables: missing,
    request: {
      method: 'POST',
      grantType: 'client_credentials',
      authorizationScheme: 'Basic clientId:clientSecret（已脱敏）'
    }
  }
}

if (missing.size() > 0) { exit 503, '零信任网关调试变量未填写：' + missing.join(', ') }

var separator = gatewayConfig.tokenUrl.indexOf('?') >= 0 ? '&' : '?'
var tokenResponse = null
try {
  tokenResponse = http.connect(`${gatewayConfig.tokenUrl}${separator}grant_type=client_credentials`)
    .contentType('application/json;charset=utf-8')
    .header('Authorization', 'Basic ' + Base64.encode(`${gatewayConfig.clientId}:${gatewayConfig.clientSecret}`))
    .post()
    .getBody()
} catch (error) {
  exit 502, '零信任认证服务请求失败：' + (error.message || error)
}

var accessToken = tokenResponse == null ? '' : (tokenResponse.access_token || '').trim()
if (accessToken == '') {
  var gatewayMessage = tokenResponse == null ? '认证服务未返回响应' : (tokenResponse.message || tokenResponse.msg || '认证服务未返回 access_token')
  exit 502, '零信任认证失败：' + gatewayMessage
}

return {
  tenantId: tenantId,
  dryRun: false,
  tokenAvailable: true,
  tokenExposed: false,
  tokenType: tokenResponse.token_type || 'Bearer',
  expiresIn: tokenResponse.expires_in || null,
  eCode: tokenResponse.eCode || null
}
