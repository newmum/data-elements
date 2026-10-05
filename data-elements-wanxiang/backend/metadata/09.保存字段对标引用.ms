{"properties":{},"id":"dwm_metadata_governance_bindings_save_01","groupId":"dwm_metadata_governance_group_01","name":"09.保存字段对标引用","path":"/bindings/save","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"更新共享字段的 quality_rule_id/reference_column_id，校验租户、目标和并发版本；支持 dryRun。"}
================================
// Explicit field-reference write only. Does not create a rule or mutate business records.
import tenantRuntime
var tenantId = tenantRuntime.id()
var tableId = body?.tableId
var columnId = body?.columnId
var mode = body?.mode
var targetId = body?.targetId
if (targetId != null) { targetId=(targetId+'').trim(); if (targetId=='') { targetId=null } }
if (tableId==null || columnId==null || (mode!='QUALITY' && mode!='REFERENCE')) { exit 400,'请选择字段和有效对标类型' }
return db.transaction(() => {
// Serialize reference edits within the active tenant: two simultaneous A->B,
// B->A writes must not both pass a snapshot-only cycle check. This locks only
// owned source rows, not business records or other tenants' catalogs.
if (mode=='REFERENCE') {
 db.select('select tid from db_datasource_t where tenant_id=#{tenantId} and is_del=0 order by tid for update')
}
var c=db.selectOne("""
 select c.tid,c.column_name,c.quality_rule_id,c.reference_column_id,c.metadata_binding_version
 from db_table_column_t c join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 where c.tenant_id=#{tenantId} and c.tid=#{columnId} and c.table_id=#{tableId} and c.is_del=0
""")
if (c==null) { exit 404,'字段不存在或不属于当前租户' }
var expected=body?.expectedTargetId==null ? '' : (body.expectedTargetId+'').trim()
var current=mode=='QUALITY' ? c.qualityRuleId : c.referenceColumnId
current=current==null ? '' : (current+'').trim()
if (current!=expected) { exit 409,'此类型的对标引用已变更，请刷新重试' }
if (targetId!=null && mode=='QUALITY') {
 var r=db.selectOne("""
 select r.tid from dq_quality_task_rule_t r
 join dq_quality_task_t q on q.tid=r.task_id and q.tenant_id=#{tenantId} and q.is_del=0 and q.table_id=#{tableId}
 where r.tenant_id=#{tenantId} and r.tid=#{targetId} and r.is_del=0 and r.enabled=1 and r.column_name=#{c.columnName}
 """)
 if (r==null) { exit 400,'只能绑定当前表、当前字段已有且启用的质检任务规则，请先在质检任务中配置规则' }
}
if (targetId!=null && mode=='REFERENCE') {
 if (targetId==columnId) { exit 400,'不能参照自身字段' }
 var target=db.selectOne("""
 select rc.tid from db_table_column_t rc
 join db_table_t rt on rt.tid=rc.table_id and rt.tenant_id=#{tenantId} and rt.is_del=0
 join db_datasource_t rd on rd.tid=rt.datasource_id and rd.tenant_id=#{tenantId} and rd.is_del=0
 where rc.tenant_id=#{tenantId} and rc.tid=#{targetId} and rc.is_del=0
 """)
 if (target==null) { exit 404,'参照字段不存在或不属于当前租户' }
 // References must remain acyclic. A bounded walk fails closed on historical corrupt chains.
 var visited=[columnId]
 var next=targetId
 var depth=0
 while (next!=null && next!='') {
  if (visited.contains(next)) { exit 400,'此参照会产生字段引用循环' }
  if (depth>=128) { exit 400,'参照链过深，请先整理字段参照' }
  visited.add(next);depth=depth+1
  var ref=db.table('db_table_column_t').where().eq('tid',next).eq('tenant_id',tenantId).eq('is_del',0).selectOne()
  next=ref?.referenceColumnId
 }
}
var version=c.metadataBindingVersion
if (body?.dryRun==true) { return {valid:true,dryRun:true,version:version} }
var affected=0
if (mode=='QUALITY') { affected=db.update('update db_table_column_t set quality_rule_id=#{targetId},metadata_binding_version=metadata_binding_version+1,updated_time=current_timestamp(6) where tenant_id=#{tenantId} and tid=#{columnId} and is_del=0 and metadata_binding_version=#{version}') }
else { affected=db.update('update db_table_column_t set reference_column_id=#{targetId},metadata_binding_version=metadata_binding_version+1,updated_time=current_timestamp(6) where tenant_id=#{tenantId} and tid=#{columnId} and is_del=0 and metadata_binding_version=#{version}') }
if (affected==0) { exit 409,'字段对标保存冲突，请刷新重试' }
return {saved:true,columnId:columnId,mode:mode,targetId:targetId,version:version+1}
})
