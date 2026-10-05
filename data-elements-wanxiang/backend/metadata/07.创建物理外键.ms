{"properties":{},"id":"dwm_metadata_governance_fk_create_01","groupId":"dwm_metadata_governance_group_01","name":"07.创建物理外键","path":"/relations/foreign-key/create","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"确认摘要匹配且用户明确同意后创建物理外键，不删除业务数据、不关闭外键检查、不自动创建目标索引。"}
================================
import tenantRuntime
import metadataGovernance
var tenantId = tenantRuntime.id()
return metadataGovernance.createForeignKey(body)
