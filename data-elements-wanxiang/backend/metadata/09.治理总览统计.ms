{"properties":{},"id":"dwm_metadata_governance_summary_01","groupId":"dwm_metadata_governance_group_01","name":"09.治理总览统计","path":"/summary","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"只读聚合当前租户数据源、实体、数据元和有效标准覆盖率；首屏不传输完整表字段目录。"}
================================
import tenantRuntime
var tenantId = tenantRuntime.id()
// Read-only shared-table aggregation. No datasource probe or business-row read.
var sources = db.selectInt("select count(*) from db_datasource_t where tenant_id=#{tenantId} and is_del=0")
var entities = db.selectInt("""
 select count(*) from db_table_t t
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 where t.tenant_id=#{tenantId} and t.is_del=0
""")
var standards = db.selectInt("select count(*) from da_metadata_t where tenant_id=#{tenantId} and is_del=0")
var fields = db.selectOne("""
 select count(*) as total_fields,
 coalesce(sum(case when m.is_del=0 and m.publish_status=1 then 1 else 0 end),0) as mapped_fields,
 coalesce(round(sum(case when m.is_del=0 and m.publish_status=1 then 1 else 0 end) * 100.0 / nullif(count(*),0),2),0) as coverage
 from db_table_column_t c
 join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 left join da_metadata_t m on m.tid=c.data_standard_id and m.tenant_id=#{tenantId}
 where c.tenant_id=#{tenantId} and c.is_del=0
""")
return {sources:sources,entities:entities,standards:standards,coverage:{totalFields:fields.totalFields,mappedFields:fields.mappedFields,coverage:fields.coverage}}
