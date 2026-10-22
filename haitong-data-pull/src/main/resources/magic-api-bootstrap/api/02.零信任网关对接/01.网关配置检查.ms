{"properties":{},"id":"haitong-ztgw-config-01","groupId":"haitong-ztgw-02","name":"01.网关配置检查","path":"/config-check","method":"GET","parameters":[],"options":[],"headers":[],"paths":[],"description":"检查零信任网关地址、OAuth 客户端配置和审计默认字段；不返回任何密钥或访问令牌。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
import zeroTrustGateway
return zeroTrustGateway.configCheck()
