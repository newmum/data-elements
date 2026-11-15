{
  "id": "haitong-ztgw-token-02",
  "groupId": "530d365665f8533282256c61df32bea4",
  "name": "01.网关令牌状态",
  "path": "/status",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "通过 OAuth2 client_credentials 检查零信任网关令牌可用性。dryRun=true 时只校验本地配置。不会返回 access_token。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import zeroTrustGateway

// POST /gateway/token/status。检查 OAuth 令牌是否可用。dryRun=true 时只校验本地配置，响应不包含令牌。

return zeroTrustGateway.tokenStatus(body == null ? {} : body)
