{"properties":{},"id":"haitong-ztgw-token-02","groupId":"haitong-ztgw-02","name":"02.网关令牌状态","path":"/token-status","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"通过 OAuth2 client_credentials 检查零信任网关令牌可用性。dryRun=true 时只校验本地配置。不会返回 access_token。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
import zeroTrustGateway
return zeroTrustGateway.tokenStatus(body == null ? {} : body)
