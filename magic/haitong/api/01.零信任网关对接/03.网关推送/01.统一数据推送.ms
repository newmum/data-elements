{
  "id": "haitong-ztgw-push-03",
  "groupId": "c99660a56bd6546cba45daed93c05941",
  "name": "01.统一数据推送",
  "path": "/push",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "将表批次以零信任网关要求的 OAuth2 身份和审计字段转发。可用 dryRun=true 仅检查请求和部署配置。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import zeroTrustGateway
import request

// POST /gateway/data/push。验证调用密钥和幂等键后转发数据批次。dryRun=true 时只校验，不请求外部网关。

var apiKeys = request.getHeaders('X-Api-Key')
var idempotencyKeys = request.getHeaders('Idempotency-Key')
return zeroTrustGateway.dataPush(body == null ? {} : body, apiKeys == null ? '' : apiKeys[0], idempotencyKeys == null ? '' : idempotencyKeys[0])
