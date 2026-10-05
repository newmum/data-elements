{"properties":{},"id":"dwm_standard_landing_save_01","groupId":"dwm_standard_landing_group_01","name":"02.保存字段标准引用","path":"/save","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"requestBody":"{}","description":"只更新 db_table_column_t.data_standard_id；校验当前租户、已发布标准及并发修改，支持 dryRun。"}
================================
import tenantRuntime
var tenantId = tenantRuntime.id()
var tableId = body?.tableId
var columnId = body?.columnId
var standardId = body?.standardId
var expectedStandardId = body?.expectedStandardId == null ? '' : (body.expectedStandardId + '').trim()
if (tableId == null || tableId == '' || columnId == null || columnId == '') { exit 400, '请选择真实的数据表及字段' }
if (standardId != null) { standardId = (standardId + '').trim(); if (standardId == '') { standardId = null } }
var column = db.selectOne("""
 select c.tid,c.data_standard_id,c.column_comment
 from db_table_column_t c
 join db_table_t t on t.tid=c.table_id and t.tenant_id=#{tenantId} and t.is_del=0
 join db_datasource_t d on d.tid=t.datasource_id and d.tenant_id=#{tenantId} and d.is_del=0
 where c.tenant_id=#{tenantId} and c.tid=#{columnId} and c.table_id=#{tableId} and c.is_del=0
""")
if (column == null) { exit 404, '字段不存在或不属于当前租户' }
var currentId = column.dataStandardId == null ? '' : (column.dataStandardId + '').trim()
if (currentId != expectedStandardId) { exit 409, '字段标准引用已被其他操作修改，请刷新后重试' }
// Dameng treats the empty string as NULL. Use a real null expected value and
// explicit empty/null predicates so a first binding still has an atomic guard.
var expectedDbId = expectedStandardId == '' ? null : expectedStandardId
if (standardId != null) {
 var standard = db.table('da_metadata_t').where().eq('tid',standardId).eq('tenant_id',tenantId).eq('is_del',0).selectOne()
 if (standard == null) { exit 404, '标准不存在或不属于当前租户' }
 if (standard.publishStatus != 1) { exit 409, '只允许绑定已审批发布的数据元' }
}
if (body?.dryRun == true) { return {columnId:columnId,standardId:standardId,dryRun:true,valid:true} }
// Deliberately no table registration flags, field-governance bulk save, ES rebuild, or catalog projection.
var affected = db.update("""
 update db_table_column_t set data_standard_id=#{standardId},updated_time=current_timestamp(6)
 where tenant_id=#{tenantId} and tid=#{columnId} and table_id=#{tableId} and is_del=0
   and ((#{expectedDbId} is null and
         (data_standard_id is null or trim(data_standard_id) is null or trim(data_standard_id)=''))
        or trim(data_standard_id)=#{expectedDbId})
""")
if (affected == 0) { exit 409, '字段标准引用保存冲突，请刷新后重试' }
return {columnId:columnId,standardId:standardId,saved:true}
