{
  "properties" : { },
  "id" : "2ebdc2a6af4b5e2989cd8add98c849fa",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "03.materialization-command",
  "createTime" : null,
  "updateTime" : 1791068445365,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/materialization-command",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 06.逻辑模型物化：固定冻结版本及绑定，复用共享元数据建表接口执行真实 CREATE TABLE。
// 创建/预检不连接业务库。execute/retry 必须显式确认，先乐观锁领取方案再逐项执行；绝不覆盖已存在表。
import tenantRuntime
import jsons
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.metautil.service.MetadataRelationJdbcService as fusionJdbc
import '@post:/dst/database/metadata/getCreateTableDDL' as generateDDL
import '@post:/dst/database/metadata/createTable' as createPhysicalTable
import '@/resource-center/planning-read' as readPlanning
import '@/resource-center/model-read' as readModels
import '@/resource-center/model-validate' as validateModel

var tenantId=tenantRuntime.id()
var actorId=StpUtil.getLoginIdAsString()
var text=v=>v==null?'':(v+'').trim()
var clone=v=>JSONUtil.parse(jsons.toJSONStr(v))
var payload=input.body==null?{}:input.body
var id=text(input.id)
var action=text(input.action)
var method=text(input.method)
var key=v=>{var name=text(v);if(!name.matches('[A-Za-z_][A-Za-z0-9_]{0,62}')){exit 400,'表和字段技术名称须为安全标识符，最多63字符'};return name}
var find=(items,objectId)=>{for(item in items){if(item.id==objectId){return item}};exit 404,'对象不存在或不属于当前租户'}
var unwrap=value=>{if(value instanceof java.util.Map && value.success==false){throw new java.lang.IllegalStateException(text(value.message)==''?text(value.msg):text(value.message))};return value instanceof java.util.Map&&value.data!=null?value.data:value}
var audit=(verb,plan,detail)=>db.table('res_resource_operation_log').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,actor_id:actorId,action_name:verb,object_id:plan.id,object_name:plan.name,detail_text:detail,created_time:new Date()})
var readPlan=planId=>{
 var row=db.table('res_materialization_plan').where().eq('tenant_id',tenantId).eq('tid',planId).eq('is_del',0).selectOne()
 if(row==null||text(row.planJson)==''){exit 404,'物化方案不存在或不属于当前租户'}
 var plan=JSONUtil.parse(row.planJson);plan.id=text(row.tid);plan.version=row.revisionNo;plan.state=row.planStatus;plan.updatedAt=row.updatedTime;plan.createdAt=row.createdTime;return plan
}
var save=(plan,expected)=>{
 var now=new Date();var encoded=jsons.toJSONStr(plan)
 var count=db.update('UPDATE res_materialization_plan SET plan_json=#{encoded},plan_status=#{state},revision_no=revision_no+1,updated_by=#{actorId},updated_time=#{now} WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0 AND revision_no=#{expected}',{encoded:encoded,state:plan.state,actorId:actorId,now:now,tenantId:tenantId,id:plan.id,expected:expected})
 if(count!=1){exit 409,'物化方案已更新，请刷新后重试'}
 plan.version=expected+1;plan.updatedAt=now;return plan
}
var bindingFor=plan=>{
 var planning=readPlanning({});var binding=find(planning.bindings,plan.bindingId);var domain=find(planning.databases,binding.databaseId)
 if(domain.state!='ACTIVE'||domain.mode!='MANAGED'||binding.category=='REFERENCE'){throw new java.lang.IllegalStateException('只有有效管理型分库可以物化')}
 if(binding.state!='VALID'||binding.checkedHash!=binding.currentHash){throw new java.lang.IllegalStateException('绑定配置已变化或尚未校验，请重新检查')}
 if(plan.bindingHash!=null&&plan.bindingHash!=binding.currentHash){throw new java.lang.IllegalStateException('方案绑定快照已失效，请重新创建物化方案')}
 var registered=connectionProperties.resolve(tenantId,binding.sourceId)
 if(registered==null||registered.isEmpty()){throw new java.lang.IllegalStateException('目标数据源不存在或不属于当前租户')}
 var pick=keys=>{for(k in keys){if(text(registered[k])!=''){return text(registered[k])}};return ''}
 var engine=pick(['dbType','databaseType']).toLowerCase()
 var catalog=pick(['database','dbMetaDbName','dbName'])
 var schema=pick(['schema','defaultSchema','currentSchema','schemaName'])
 if(schema==''&&['oracle','dameng'].contains(engine)){schema=pick(['username','user'])}
 if(schema==''&&['postgresql','kingbase','kingbase8','gaussdb'].contains(engine)){schema='public'}
 if(catalog==''&&schema==''){throw new java.lang.IllegalStateException('目标数据源未登记实际数据库或Schema')}
 // 物化API使用已登记连接的范围，不允许页面填写另一个schema冒充目标。
 if(text(binding.catalog)!=''&&text(binding.catalog).toLowerCase()!=catalog.toLowerCase()){throw new java.lang.IllegalStateException('绑定数据库与数据源登记配置不一致')}
 var expectedSchema=['mysql','mariadb'].contains(engine)?catalog:schema
 if(text(binding.schema)!=''&&text(binding.schema).toLowerCase()!=expectedSchema.toLowerCase()){throw new java.lang.IllegalStateException('绑定Schema与数据源登记配置不一致')}
 return {binding:binding,domain:domain,engine:engine}
}
var physical=(field,engine)=>{
 var logical=text(field.logicalType)
 if(logical=='String'){return (['oracle','dameng'].contains(engine)?'VARCHAR2':'VARCHAR')+'('+field.length+')'}
 if(logical=='Text'){return ['oracle','dameng'].contains(engine)?'CLOB':engine=='sqlserver'?'NVARCHAR(MAX)':'TEXT'}
 if(logical=='Int32'){return 'INTEGER'}
 if(logical=='Int64'){return ['oracle','dameng'].contains(engine)?'NUMBER(19)':'BIGINT'}
 if(logical=='Decimal'){return 'DECIMAL('+field.precision+','+field.scale+')'}
 if(logical=='Boolean'){return ['oracle','dameng'].contains(engine)?'NUMBER(1)':engine=='sqlserver'?'BIT':'BOOLEAN'}
 if(logical=='Date'){return 'DATE'}
 if(logical=='Timestamp'){return engine=='sqlserver'?'DATETIME2':['mysql','mariadb'].contains(engine)?'DATETIME':'TIMESTAMP'}
 if(logical=='UUID'){return ['postgresql','kingbase','kingbase8'].contains(engine)?'UUID':'CHAR(36)'}
 if(logical=='Binary'){return ['postgresql','kingbase','kingbase8'].contains(engine)?'BYTEA':engine=='sqlserver'?'VARBINARY(MAX)':'BLOB'}
 if(logical=='JSON'&&['mysql','mariadb','postgresql'].contains(engine)){return engine=='postgresql'?'JSONB':'JSON'}
 throw new java.lang.IllegalStateException('字段'+text(field.name)+'的'+logical+'类型没有目标方言映射')
}
var itemsFor=(entity,engine)=>{
 var columns=[];var primary=[];var unique=[]
 for(k in entity.keys==null?[]:entity.keys){if(k.kind=='primary'){primary.addAll(k.fieldIds)};if(k.kind=='unique'&&k.fieldIds.size()==1){unique.add(k.fieldIds[0])}}
 var ordinal=0
 for(f in entity.fields){ordinal=ordinal+1;var native=physical(f,engine);var base=native.contains('(')?native.substring(0,native.indexOf('(')):native
  columns.add({columnName:key(f.name),columnComment:text(f.businessName)==''?text(f.comment):text(f.businessName),dataType:base,columnType:native,length:f.length,precisionLength:f.precision,scale:f.scale,nullable:f.nullable=='false'?0:1,primaryKey:primary.contains(f.id)?1:0,isUnique:unique.contains(f.id)?1:0,autoIncrement:0,indexed:primary.contains(f.id)?1:0,ordinalPosition:ordinal,designFieldId:f.id,standardId:f.standard==null?null:f.standard.id})
 }
 return columns
}
var precheck=plan=>{
 var errors=[];var ddlParts=[]
 var validation=validateModel(clone(plan.revision));errors.addAll(validation.errors)
 try{
  var target=bindingFor(plan);var names=[]
  for(item in plan.items){
   if(item.state=='SUCCEEDED'){ddlParts.add(item.ddl);continue}
   var name=key(item.targetName)
   if(names.contains(name.toLowerCase())){throw new java.lang.IllegalStateException('方案中存在重复目标表名')};names.add(name.toLowerCase())
   var entity=find(plan.revision.entities,item.designEntityId)
   if(entity.reference!=null){throw new java.lang.IllegalStateException('引用模型的只读实体不能物化')}
   var sourceId=target.binding.sourceId
   var duplicate=db.selectOne('SELECT tid FROM db_table_t WHERE tenant_id=#{tenantId} AND datasource_id=#{sourceId} AND is_del=0 AND LOWER(table_name)=LOWER(#{name}) LIMIT 1')
   if(duplicate!=null){throw new java.lang.IllegalStateException('目标表已登记，不覆盖：'+name)}
   var columns=itemsFor(entity,target.engine)
   var savedBody=body
   try{body={canvas:true,propList:{dbId:sourceId,tableName:name,tableNameCn:text(entity.displayName)},tableItems:columns};item.ddl=text(unwrap(generateDDL()))}
   finally{body=savedBody}
   if(item.ddl==''){throw new java.lang.IllegalStateException('共享DDL接口未返回建表脚本')}
   item.columns=columns;ddlParts.add(item.ddl)
  }
 }catch(e){errors.add(text(e.getMessage()))}
 plan.errors=errors;plan.warnings=['预检只检查模型、命名、登记冲突和SQL方言；实际连接与实库同名检查在执行时完成。','仅创建表结构；不装载数据，不生成跨来源物理外键，不覆盖已有表。']
 plan.ddl=ddlParts.join('\n\n');plan.state=errors.size()>0?'BLOCKED':'READY';return plan
}
if(method=='POST'&&id==''){
 var models=readModels({entities:[]});var model=find(models.models,text(payload.modelId));var frozen=find(model.frozen,text(payload.revisionId))
 var planning=readPlanning({});var binding=find(planning.bindings,text(payload.bindingId))
 var chosen=payload.entityIds==null?[]:payload.entityIds
 if(chosen.size()==0||chosen.size()>300){exit 400,'请选择1至300个物化实体'}
 if(text(frozen.outputEntityId)!=''){
  if(chosen.size()!=1||text(chosen[0])!=text(frozen.outputEntityId)){exit 400,'融合模型只能物化唯一输出表，来源表不可物化'}
  if(text(binding.databaseId)!=text(model.databaseId)){exit 409,'物化目标必须是模型指定的目标分库'}
 }
 var selected=[];var seen=[]
 for(eid in chosen){if(seen.contains(eid)){exit 400,'实体选择不能重复'};seen.add(eid);var entity=find(frozen.entities,eid);if(entity.reference!=null){exit 400,'参照实体不能物化'}
  var name=payload.targetNames==null||payload.targetNames[eid]==null?text(binding.prefix)+entity.name:payload.targetNames[eid]
  selected.add({id:IdUtil.fastSimpleUUID(),designEntityId:eid,targetName:key(name),state:'PENDING'})
 }
 var now=new Date();var plan={id:IdUtil.fastSimpleUUID(),version:1,name:text(payload.name)==''?model.name+'物化':text(payload.name),modelId:model.id,revisionId:frozen.id,bindingId:binding.id,bindingHash:binding.currentHash,revision:clone(frozen),items:selected,attempt:0,createdAt:now,updatedAt:now,requestedBy:actorId}
 precheck(plan)
 db.table('res_materialization_plan').primary('tid').insert({tid:plan.id,tenant_id:tenantId,plan_code:'PS_'+plan.id,model_id:model.id,target_datasource_id:binding.sourceId,target_table_name:selected[0].targetName,plan_status:plan.state,risk_level:'STRUCTURE_CREATE',binding_id:binding.id,frozen_revision_id:frozen.id,plan_json:jsons.toJSONStr(plan),revision_no:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
 var targetNames=[];for(item in selected){targetNames.add(item.targetName)}
 audit('创建物化方案',plan,'模型：'+model.name+'；冻结版本：v'+frozen.number+'；目标表：'+targetNames.join('、')+'；预检结果：'+(plan.state=='READY'?'可执行':'存在阻断'));return plan
}
var plan=readPlan(id)
if(method=='GET'){return plan}
if(text(input.version)!=text(plan.version)){exit 409,'物化方案已更新，请刷新后重试'}
if(action=='cancel'){
 if(!['QUEUED','RUNNING'].contains(plan.state)){exit 409,'任务已结束，不可取消'}
 plan.state='CANCELLING';save(plan,plan.version);audit('请求取消物化',plan,'当前单表操作不可中断，后续对象将停止');return plan
}
if(action=='precheck'){
 if(['RUNNING','QUEUED','CANCELLING','SUCCEEDED'].contains(plan.state)){exit 409,'执行中或完成的方案不能重新预检'}
 precheck(plan);save(plan,plan.version);audit('重新预检物化',plan,'预检结果：'+(plan.state=='READY'?'可执行':'存在阻断')+'；问题 '+plan.errors.size()+' 项');return plan
}
if(action=='load'){
 if(payload.confirmExecution!=true){exit 400,'请确认将冻结的字段映射装载到目标表'}
 if(plan.state!='SUCCEEDED'||text(plan.revision.outputEntityId)==''){exit 409,'请先完成融合模型唯一输出表 D 的物化建表'}
 if(plan.items.size()!=1||plan.items[0].state!='SUCCEEDED'||text(plan.items[0].resourceId)==''){exit 409,'目标表尚未完整创建与登记'}
 if(plan.loadState=='SUCCEEDED'){exit 409,'该冻结版本已装载，禁止重复写入'}
 if(plan.loadState=='RUNNING'){exit 409,'融合装载正在执行，请稍后刷新'}
 var target=bindingFor(plan);var output=find(plan.revision.entities,plan.revision.outputEntityId)
 var fusion=plan.revision.fusion
 if(fusion==null||fusion.mappings==null||fusion.mappings.size()==0){exit 422,'冻结版本没有可执行的字段映射'}
 var validation=validateModel(clone(plan.revision));if(!validation.valid){exit 422,validation.errors.join('；')}
 var sourceById={};var sources=[]
 for(e in plan.revision.entities){if(e.role!='SOURCE'){continue}
  if(e.origin==null||text(e.origin.entityId)==''){exit 422,'来源表缺少共享技术快照标识'}
  var original=db.selectOne('SELECT tid,datasource_id,table_name FROM db_table_t WHERE tenant_id=#{tenantId} AND tid=#{tableId} AND is_del=0',{tenantId:tenantId,tableId:e.origin.entityId})
  if(original==null||text(original.tableName)!=text(e.origin.tableName==null?e.name:e.origin.tableName)||e.origin.sourceId!=null&&text(original.datasourceId)!=text(e.origin.sourceId)){exit 409,'来源技术表已变化，请重新逆向解析：'+text(e.name)}
  sourceById[e.id]=e;sources.add({entityId:e.id,sourceId:text(original.datasourceId),tableName:text(original.tableName)})
 }
 var currentName=(e,fid)=>{var f=find(e.fields,fid);var originalId=e.origin==null||e.origin.fieldMap==null?null:e.origin.fieldMap[fid]
  if(text(originalId)==''){exit 409,'来源字段没有原始快照映射：'+text(f.name)}
  var column=db.selectOne('SELECT tid,column_name FROM db_table_column_t WHERE tenant_id=#{tenantId} AND tid=#{columnId} AND table_id=#{tableId} AND is_del=0',{tenantId:tenantId,columnId:originalId,tableId:e.origin.entityId})
  if(column==null||text(column.columnName)!=text(f.name)){exit 409,'来源字段快照已变化，请重新解析：'+text(f.name)}
  return text(column.columnName)
 }
 var joins=[]
 for(j in fusion.joins){var left=sourceById[j.leftEntityId];var right=sourceById[j.rightEntityId];if(left==null||right==null){exit 422,'融合关联引用了非来源表'}
  var pairs=[];for(pair in j.pairs){pairs.add({leftName:currentName(left,pair.leftFieldId),rightName:currentName(right,pair.rightFieldId)})}
  joins.add({leftEntityId:j.leftEntityId,rightEntityId:j.rightEntityId,joinType:j.joinType,pairs:pairs,rightCardinality:j.rightCardinality,dedupe:j.dedupe,orderName:j.dedupe=='LATEST'?currentName(right,j.orderFieldId):null})
 }
 var mappings=[]
 for(mapping in fusion.mappings){var from=sourceById[mapping.sourceEntityId];if(from==null){exit 422,'字段映射引用了非来源表'}
  var targetField=find(output.fields,mapping.targetFieldId)
  mappings.add({sourceEntityId:from.id,sourceName:currentName(from,mapping.sourceFieldId),targetName:key(targetField.name),transform:mapping.transform})
 }
 var item=plan.items[0]
 plan.loadState='RUNNING';plan.loadStartedAt=new Date();plan.loadError=null;save(plan,plan.version)
 try{
  var result=fusionJdbc.loadModelFusion({targetSourceId:target.binding.sourceId,targetTable:item.targetName,baseEntityId:fusion.baseEntityId,sources:sources,joins:joins,mappings:mappings})
  plan.loadedRows=result.insertedRows;plan.loadState='SUCCEEDED';plan.loadedAt=new Date();plan.loadError=null
  db.update('UPDATE db_table_t SET record_count=#{count},updated_time=#{now},updated_by=#{actorId} WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0',{count:result.insertedRows,now:new Date(),actorId:actorId,tenantId:tenantId,id:item.resourceId})
  save(plan,plan.version);audit('装载融合模型数据',plan,'目标表 '+item.targetName+'，写入 '+result.insertedRows+' 行');return plan
 }catch(e){plan.loadState='FAILED';plan.loadError=text(e.getMessage());save(plan,plan.version);audit('融合模型装载失败',plan,'目标表 '+item.targetName+'；原因：'+plan.loadError);return plan}
}
if(!['execute','retry'].contains(action)){exit 400,'不支持的物化操作'}
if(payload.confirmExecution!=true){exit 400,'请核对SQL后明确确认执行物化建表'}
if(!['READY','FAILED','PARTIAL','CANCELLED'].contains(plan.state)){exit 409,'方案不在可执行状态'}
var previousState=plan.state
precheck(plan)
if(plan.errors.size()>0){save(plan,plan.version);exit 409,'预检未通过，请查看方案错误'}
plan.state='RUNNING';plan.attempt=plan.attempt+1;plan.startedAt=new Date();plan.finishedAt=null
for(item in plan.items){if(item.state!='SUCCEEDED'){item.state='PENDING';item.error=null}}
// 原子乐观锁领取；真实DDL不被长数据库事务包裹，避免取消请求无法读取状态。
save(plan,plan.version)
audit('开始物化建表',plan,'已明确确认执行，尝试 '+plan.attempt)
var target=bindingFor(plan)
for(item in plan.items){
 if(item.state=='SUCCEEDED'){continue}
 var latest=readPlan(plan.id)
 if(latest.state=='CANCELLING'){plan.version=latest.version;break}
 var executionId=IdUtil.fastSimpleUUID()
 db.table('res_materialization_execution').primary('tid').insert({tid:executionId,tenant_id:tenantId,plan_id:plan.id,item_id:item.id,execution_status:'RUNNING',execution_mode:'PHYSICAL_CREATE',ddl_text:item.ddl,physical_created:0,started_time:new Date(),created_by:actorId,created_time:new Date(),updated_by:actorId,updated_time:new Date(),is_del:0})
 try{
  bindingFor(plan)
  // 成功物理建表但登记失败的记录不得盲目重试CREATE；明确保留恢复依据。
  var previous=db.selectOne('SELECT tid,target_table_id FROM res_materialization_execution WHERE tenant_id=#{tenantId} AND plan_id=#{planId} AND item_id=#{itemId} AND physical_created=1 AND is_del=0 ORDER BY created_time DESC LIMIT 1',{tenantId:tenantId,planId:plan.id,itemId:item.id})
  if(previous!=null){throw new java.lang.IllegalStateException('该对象此前已物理创建，请先核对并登记已有表，未重复执行DDL')}
  var savedBody=body;var created=null
  try{body={canvas:true,dbId:target.binding.sourceId,tableName:item.targetName,ddl:item.ddl,skipIfExists:false};created=unwrap(createPhysicalTable())}
  finally{body=savedBody}
  if(!(created instanceof java.util.Map)||created.created!=true||created.status!='created'){throw new java.lang.IllegalStateException('目标实库表已存在或建表未成功，未覆盖已有对象')}
  db.table('res_materialization_execution').where().eq('tenant_id',tenantId).eq('tid',executionId).update({physical_created:1,updated_time:new Date()})
  var entity=find(plan.revision.entities,item.designEntityId);var tableId=IdUtil.fastSimpleUUID();var fieldMap={}
  db.transaction(()=>{
   var now=new Date()
   db.table('db_table_t').primary('tid').insert({tid:tableId,tenant_id:tenantId,datasource_id:target.binding.sourceId,table_name:item.targetName,table_name_en:item.targetName,table_name_cn:text(entity.displayName),table_comment:text(entity.comment),table_type:'TABLE',business_type:target.domain.domain,field_count:entity.fields.size(),record_count:0,asset_desc:text(entity.comment),asset_status:2,flow_status:2,resource_domain_id:target.domain.id,resource_owner:target.domain.owner,resource_state:'ACTIVE',resource_model_id:plan.modelId,resource_plan_id:plan.id,resource_revision_no:1,revision:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
   for(column in item.columns){var columnId=IdUtil.fastSimpleUUID();fieldMap[column.designFieldId]=columnId
    db.table('db_table_column_t').primary('tid').insert({tid:columnId,tenant_id:tenantId,table_id:tableId,column_name:column.columnName,column_comment:column.columnComment,data_type:column.dataType,column_type:column.columnType,length:column.length,precision_length:column.precisionLength,scale:column.scale,nullable:column.nullable,primary_key:column.primaryKey,is_unique:column.isUnique,indexed:column.indexed,ordinal_position:column.ordinalPosition,data_standard_id:column.standardId,revision:1,created_by:actorId,created_time:now,updated_by:actorId,updated_time:now,is_del:0})
   }
   db.table('db_table_t').where().eq('tenant_id',tenantId).eq('tid',tableId).update({resource_field_map:jsons.toJSONStr(fieldMap)})
   db.table('res_materialization_execution').where().eq('tenant_id',tenantId).eq('tid',executionId).update({target_table_id:tableId,execution_status:'SUCCEEDED',execution_summary:'物理表已创建，共享技术元数据和仓库归属已登记',finished_time:now,updated_time:now})
  })
  item.entityId=tableId;item.resourceId=tableId;item.state='SUCCEEDED';item.error=null
 }catch(e){
  item.state='FAILED';item.error=text(e.getMessage())
  db.table('res_materialization_execution').where().eq('tenant_id',tenantId).eq('tid',executionId).update({execution_status:'FAILED',failure_summary:item.error,finished_time:new Date(),updated_time:new Date()})
 }
 // 每个对象保存进度；取消请求保留其CANCELLING状态，不被执行线程覆盖。
 var latest=readPlan(plan.id);plan.version=latest.version;plan.state=latest.state=='CANCELLING'?'CANCELLING':'RUNNING';save(plan,plan.version)
}
var succeeded=0;var failed=0;var cancelled=0
for(item in plan.items){if(item.state=='SUCCEEDED'){succeeded=succeeded+1};if(item.state=='FAILED'){failed=failed+1};if(item.state=='PENDING'){item.state='CANCELLED';cancelled=cancelled+1}}
var latest=readPlan(plan.id);plan.version=latest.version
plan.state=succeeded==plan.items.size()?'SUCCEEDED':succeeded>0?'PARTIAL':cancelled>0?'CANCELLED':'FAILED';plan.finishedAt=new Date()
save(plan,plan.version);audit('物化执行完成',plan,'成功 '+succeeded+' 项，失败 '+failed+' 项，取消 '+cancelled+' 项');return plan
