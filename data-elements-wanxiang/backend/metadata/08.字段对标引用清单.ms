{"properties":{},"id":"dwm_metadata_governance_bindings_list_01","groupId":"dwm_metadata_governance_group_01","name":"08.字段对标引用清单","path":"/bindings/list","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"读取共享技术字段上的质量规则/字段参照；不复制质量规则，不执行质检。"}
================================
// Read only: owned field references and existing configured task rules.
import tenantRuntime
var tenantId = tenantRuntime.id()
var rules = db.select("""
 select r.tid as id,r.rule_name,r.column_name,r.rule_type,r.enabled,q.tid as task_id,q.task_name,q.table_id
 from dq_quality_task_rule_t r
 join dq_quality_task_t q on q.tid=r.task_id and q.tenant_id=#{tenantId} and q.is_del=0
 join db_table_t t on t.tid=q.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 where r.tenant_id=#{tenantId} and r.is_del=0
""")
var columns = db.select("""
 select c.tid,c.table_id,c.quality_rule_id,c.reference_column_id,c.metadata_binding_version,
   case when q.tid is not null and q.table_id=c.table_id and r.column_name=c.column_name and r.enabled=1 then r.tid else null end as valid_rule,
   case when rd.tid is not null then rc.tid else null end as valid_reference
 from db_table_column_t c
 join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 left join dq_quality_task_rule_t r on r.tid=c.quality_rule_id and r.tenant_id=#{tenantId} and r.is_del=0
 left join dq_quality_task_t q on q.tid=r.task_id and q.tenant_id=#{tenantId} and q.is_del=0
 left join db_table_column_t rc on rc.tid=c.reference_column_id and rc.tenant_id=#{tenantId} and rc.is_del=0
 left join db_table_t rt on rt.tid=rc.table_id and rt.tenant_id=#{tenantId} and rt.is_del=0
 left join db_datasource_t rd on rd.tid=rt.datasource_id and rd.tenant_id=#{tenantId} and rd.is_del=0
 where c.tenant_id=#{tenantId} and c.is_del=0 and (c.quality_rule_id is not null or c.reference_column_id is not null)
""")
var items = []
for (c in columns) {
 if (c.qualityRuleId != null && c.qualityRuleId != '') { items.add({id:c.tid+':QUALITY',entity_id:c.tableId,field_id:c.tid,mapping_mode:'QUALITY',target_id:c.qualityRuleId,version:c.metadataBindingVersion,status:c.validRule != null ? 'CONFIRMED' : 'STALE'}) }
 if (c.referenceColumnId != null && c.referenceColumnId != '') { items.add({id:c.tid+':REFERENCE',entity_id:c.tableId,field_id:c.tid,mapping_mode:'REFERENCE',target_id:c.referenceColumnId,version:c.metadataBindingVersion,status:c.validReference != null ? 'CONFIRMED' : 'STALE'}) }
}
return {items:items,rules:rules}
