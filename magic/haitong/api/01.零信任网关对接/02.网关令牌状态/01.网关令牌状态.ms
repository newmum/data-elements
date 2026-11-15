{
  "properties": {
    "leafRoute": true
  },
  "id": "haitong-ztgw-token-02",
  "groupId": "530d365665f8533282256c61df32bea4",
  "name": "01.网关令牌状态",
  "path": "/",
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
return zeroTrustGateway.tokenStatus(body == null ? {} : body)
