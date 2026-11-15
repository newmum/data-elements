{
  "properties": {},
  "id": "dwm_metadata_governance_catalog_01",
  "script": null,
  "groupId": "dwm_metadata_governance_group_01",
  "name": "01.已采集元数据目录",
  "createTime": null,
  "updateTime": 1790644820849,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/catalog",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "只读当前租户已采集的表、字段与逻辑关系；不探查源库，不返回连接凭据；涉及 db_datasource_t、db_table_t、db_table_column_t、dwm_metadata_relation_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.已采集元数据目录
// 路由：POST /dwm/metadata-governance/catalog
// 业务说明：只读当前租户已采集的表、字段与逻辑关系；不探查源库，不返回连接凭据；涉及 db_datasource_t、db_table_t、db_table_column_t、dwm_metadata_relation_t 等业务表。
import tenantRuntime
var tenantId = tenantRuntime.id()
// Read-only shared projection. Authoritative tables: db_datasource_t,
// db_table_t, db_table_column_t and dwm_metadata_relation_t. No source DB probes.
var sources = db.select("""
 select d.tid,d.db_name,coalesce(nullif(d.db_type,''),d.database_type) as db_type,
        d.is_enable,d.app_id,d.org_id,d.storage_domain,d.connection_status,d.updated_time
 from db_datasource_t d where d.tenant_id=#{tenantId} and d.is_del=0 order by d.db_name,d.tid
""")
var tables = db.select("""
 select t.tid,t.datasource_id,t.table_name,t.table_name_cn,t.table_comment,t.table_type,
        t.field_count,t.record_count,t.annotated,t.updated_time
 from db_table_t t join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 where t.tenant_id=#{tenantId} and t.is_del=0 order by t.table_name,t.tid
""")
var columns = db.select("""
 select c.tid,c.table_id,c.column_name,c.column_comment,c.data_type,c.column_type,c.length,
        c.nullable,c.primary_key,c.is_unique,c.indexed,c.ordinal_position,c.updated_time
 from db_table_column_t c
 join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 where c.tenant_id=#{tenantId} and c.is_del=0 order by c.table_id,c.ordinal_position,c.tid
""")
var relations = db.select("""
 select r.*
 from dwm_metadata_relation_t r
 join db_table_t st on st.tid=r.source_table_id and st.tenant_id=#{tenantId} and st.is_del=0
 join db_table_column_t sc on sc.tid=r.source_column_id and sc.table_id=st.tid and sc.tenant_id=#{tenantId} and sc.is_del=0
 join db_table_t tt on tt.tid=r.target_table_id and tt.tenant_id=#{tenantId} and tt.is_del=0
 join db_table_column_t tc on tc.tid=r.target_column_id and tc.table_id=tt.tid and tc.tenant_id=#{tenantId} and tc.is_del=0
 where r.tenant_id=#{tenantId} and r.is_del=0 order by r.updated_time desc,r.tid
""")
return {sources:sources,tables:tables,columns:columns,relations:relations}
