{"properties":{},"id":"dwm_metadata_governance_validate_01","groupId":"dwm_metadata_governance_group_01","name":"05.验证关系数据","path":"/relations/validate","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"用户明确操作后读取关系键，返回真实限定范围的验证汇总，不返回业务行或连接信息。"}
================================
import tenantRuntime
import metadataGovernance
var tenantId = tenantRuntime.id()
return metadataGovernance.validateRelation(body)
