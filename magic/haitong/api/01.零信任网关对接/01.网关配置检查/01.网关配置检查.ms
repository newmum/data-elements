{
  "id": "haitong-ztgw-config-01",
  "groupId": "971cf899e6ef5239a6c5186225822aa8",
  "name": "01.网关配置检查",
  "path": "/check",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "检查零信任网关地址、OAuth 客户端配置和审计默认字段；不返回任何密钥或访问令牌。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import zeroTrustGateway

// GET /gateway/config/check。检查网关配置、OAuth 客户端和审计字段，只返回配置状态。

return zeroTrustGateway.configCheck()
