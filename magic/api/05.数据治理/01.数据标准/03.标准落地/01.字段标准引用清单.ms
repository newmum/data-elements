{
  "properties": {},
  "id": "dwm_standard_landing_list_01",
  "groupId": "dwm_standard_landing_group_01",
  "name": "01.字段标准引用清单",
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{}",
  "description": "只读当前租户已采集字段及 da_metadata_t 标准引用，统计有效已发布标准覆盖率；涉及 db_table_t、db_datasource_t、db_table_column_t、da_metadata_t 等业务表。",
  "script": null
}
================================
import tenantRuntime;
import dataScope;
import tableSort;
import db;
var tenantId=tenantRuntime.id();
var scope=dataScope.dataSource();
if(scope==null || !['ALL','OWNER','ORG'].contains(scope.scope)) {exit 403,'无权读取数据源';}
var owner=scope.userId;var orgIds=scope.organizationIds||[];
if(scope.scope=='OWNER' && (owner==null || owner=='')) {exit 403,'数据范围身份缺失';}
var sourceScope=scope.scope=='ALL'?'1=1':scope.scope=='OWNER'?'d.created_by=#{owner}':orgIds.size()==0?'1=0':'d.org_id IN (#{orgIds})';
var p=body||{};var page=Math.max(1,(p.pageNum||1)::int);var size=Math.min(100,Math.max(1,(p.pageSize||20)::int));
var keyword=(p.keyword||'').trim();var state=p.scope||'ALL';var targetId=p.targetId;var entityId=p.entityId;var fieldKeyword=(p.fieldKeyword||'').trim();
if(keyword.length()>100 || fieldKeyword.length()>100 || !['ALL','BOUND','UNBOUND'].contains(state)) {exit 400,'标准关联筛选参数不正确';}
var from=" FROM db_table_column_t c JOIN db_table_t t ON t.tid=c.table_id AND t.tenant_id=c.tenant_id AND t.is_del=0 JOIN db_datasource_t d ON d.tid=t.datasource_id AND d.tenant_id=t.tenant_id AND d.is_del=0 LEFT JOIN da_metadata_t m ON m.tid=c.data_standard_id AND m.tenant_id=c.tenant_id AND m.is_del=0 WHERE c.tenant_id=#{tenantId} AND c.is_del=0 AND "+sourceScope;
var coverage=db.selectOne("SELECT count(*) total_fields,sum(CASE WHEN c.data_standard_id IS NOT NULL AND trim(c.data_standard_id)<>'' THEN 1 ELSE 0 END) bound_fields,sum(CASE WHEN m.tid IS NOT NULL AND m.publish_status=1 THEN 1 ELSE 0 END) mapped_fields,CASE WHEN count(*)=0 THEN 0 ELSE round(sum(CASE WHEN m.tid IS NOT NULL AND m.publish_status=1 THEN 1 ELSE 0 END)*100.0/count(*),1) END coverage"+from);
coverage.boundFields=coverage.boundFields||0;coverage.mappedFields=coverage.mappedFields||0;

var filter=" AND (#{entityId} IS NULL OR c.table_id=#{entityId}) AND (#{fieldKeyword}='' OR locate(lower(#{fieldKeyword}),lower(concat_ws(' ',c.column_name,c.column_comment)))>0) AND (#{targetId} IS NULL OR c.data_standard_id=#{targetId}) AND (#{keyword}='' OR locate(lower(#{keyword}),lower(concat_ws(' ',t.table_name,t.table_name_cn,t.table_comment,c.column_name,c.column_comment,m.meta_name,m.meta_code)))>0)";
if(state=='BOUND') {filter=filter+" AND c.data_standard_id IS NOT NULL AND trim(c.data_standard_id)<>''";}
if(state=='UNBOUND') {filter=filter+" AND (c.data_standard_id IS NULL OR trim(c.data_standard_id)='')";}
var sort=tableSort.field(p,{field_id:"coalesce(nullif(c.column_comment,''),c.column_name)",fieldName:'c.column_name',target_id:'m.meta_name',createdTime:'c.created_time',updatedTime:'c.updated_time'},'field_id');
var result=db.page("SELECT c.tid field_id,c.table_id entity_id,c.column_name field_name,c.column_comment display_name,c.data_type data_type,c.length,c.data_standard_id target_id,t.table_name table_name,t.table_name_cn table_name_cn,t.table_comment table_comment,d.db_name source_name,m.tid standard_id,m.meta_name meta_name,m.meta_code meta_code,m.field_type field_type,m.field_length field_length,m.publish_status publish_status"+from+filter+' ORDER BY CASE WHEN '+sort+' IS NULL THEN 1 ELSE 0 END,'+sort+' '+tableSort.direction(p)+',c.tid ASC',size,(page-1)*size);
var entities=[];var items=[];var elements=[];var byId={};var standardIds=[];
for(row in result.list) {
 var entity=byId.get(row.entityId);
 if(entity==null) {entity={id:row.entityId,name:row.tableName,displayName:row.tableNameCn||row.tableComment||row.tableName,sourceName:row.sourceName,fields:[]};entities.add(entity);byId.put(row.entityId,entity);}
 var target=row.targetId==null?null:(row.targetId+'').trim();if(target=='') {target=null;}
 var valid=row.standardId!=null && row.publishStatus==1;
 entity.fields.add({id:row.fieldId,name:row.fieldName,displayName:row.displayName||row.fieldName,type:row.dataType,length:row.length,standardId:target});
 items.add({id:row.fieldId+':STANDARD',entity_id:row.entityId,field_id:row.fieldId,mapping_mode:'STANDARD',target_id:target,status:target==null?'UNBOUND':valid?'CONFIRMED':'STALE'});
 if(row.standardId!=null && !standardIds.contains(row.standardId)) {standardIds.add(row.standardId);elements.add({id:row.standardId,name:row.metaName,code:row.metaCode,fieldType:row.fieldType,fieldLength:row.fieldLength,status:row.publishStatus==1?'PUBLISHED':'PENDING'});}
}
return {items:items,entities:entities,elements:elements,coverage:coverage,total:result.total,pageNum:page,pageSize:size};
