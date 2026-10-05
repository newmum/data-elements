{"properties":{},"id":"dwm_metadata_governance_fk_preview_01","groupId":"dwm_metadata_governance_group_01","name":"06.预检查物理外键","path":"/relations/foreign-key/preview","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"只读检查真实目标主键或唯一索引，生成受控 DDL、撤销 SQL 与确认摘要，不执行源数据库变更。"}
================================
import tenantRuntime
import metadataGovernance
var tenantId = tenantRuntime.id()
return metadataGovernance.previewForeignKey(body)
