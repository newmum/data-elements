{"properties":{},"id":"haitong-ztgw-push-03","groupId":"haitong-ztgw-02","name":"03.统一数据推送","path":"/data-push","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"将表批次以零信任网关要求的 OAuth2 身份和审计字段转发。可用 dryRun=true 仅检查请求和部署配置。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
import zeroTrustGateway
import request
var apiKeys = request.getHeaders('X-Api-Key')
var idempotencyKeys = request.getHeaders('Idempotency-Key')
return zeroTrustGateway.dataPush(body == null ? {} : body, apiKeys == null ? '' : apiKeys[0], idempotencyKeys == null ? '' : idempotencyKeys[0])
