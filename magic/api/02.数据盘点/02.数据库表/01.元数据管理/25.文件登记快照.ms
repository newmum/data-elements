{
  "id": "metadata_file_registration_01",
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "25.文件登记快照",
  "path": "/fileRegistration",
  "method": "POST",
  "parameters": [],
  "headers": [],
  "options": [],
  "description": "非结构化推送文件定义的读取、保存与登记完成。复用当前租户 db_table_t(FILE) 和 db_table_column_t，不探查物理数据库；read/save/complete 均按数据源归属校验，save 支持 dryRun。"
}
================================
// 文件登记复用表/字段快照。FILE 是逻辑文件登记项，不是可执行 SQL 的物理表。
// 格式和大小上限保存为治理规则，不能冒充实际存储量；一次最多100项，关联批量校验。
import tenantRuntime
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.List
import java.util.HashSet
import java.util.Map
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import log

var text = value => value == null ? '' : (value+'').trim()
var value = (row, snake, camel) => row == null ? null : (row[snake] == null ? row[camel] : row[snake])
var object = input => {
    if (input == null || text(input)=='') { return {} }
    if (input instanceof Map) { return input }
    try { return JSONUtil.parseObj(input+'') } catch(e) { exit 422, '文件登记配置不是有效JSON对象' }
}
var tenantId = text(tenantRuntime.id())
if (tenantId=='') { exit 401, '请登录并选择当前行业后操作' }
if (body?.tenantId!=null || body?.tenant_id!=null) { exit 400, '行业归属由当前登录会话确定，不接受请求指定' }
var sourceId = text(body?.dbId)
var action = text(body?.action)
if (action=='') { action='read' }
if (!['read','save','complete'].contains(action) || sourceId=='') { exit 400, '请提供有效的数据源及文件登记操作' }
var source = db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('tid',sourceId).eq('is_del',0).selectOne()
if (source==null) { exit 404, '数据源不存在或不属于当前行业' }
var sourceConfig = object(value(source,'pool_cfg','poolCfg'))
if (!['receive','push'].contains(text(sourceConfig.accessMode)) || text(sourceConfig.pushDataType).toUpperCase()!='UNSTRUCTURED') {
    exit 409, '当前数据源不是非结构化数据推送方式，请先在第一步保存类型'
}
var oldTables = db.table('db_table_t').where().eq('tenant_id',tenantId).eq('datasource_id',sourceId).eq('table_type','FILE').eq('is_del',0).orderBy('created_time').orderBy('tid').select()
if (oldTables.size()>100) { exit 422, '文件登记项超过100项，请先整理现有清单' }
var oldIds = oldTables.map(row => text(row.tid))
var oldColumns = oldIds.size()==0 ? [] : db.table('db_table_column_t').where().eq('tenant_id',tenantId).in('table_id',oldIds).eq('is_del',0).select()
var byId = {}; var byName = {}; var columnByKey = {}; var fieldNamesById = {}
for (column in oldColumns) {
    var tableId=text(value(column,'table_id','tableId'))
    var name=text(value(column,'column_name','columnName'))
    columnByKey[tableId+':'+name]=column
    if (fieldNamesById[tableId]==null) { fieldNamesById[tableId]=[] }
    fieldNamesById[tableId].add(name)
}
var rows=[]
for (table in oldTables) {
    var id=text(table.tid)
    var policy=object(value(table,'field_governance_config','fieldGovernanceConfig')).fileRegistration || {}
    var row={
        tableId:id, collectionId:text(value(table,'table_name','tableName')),
        fileName:text(value(table,'table_name_cn','tableNameCn')),
        fileDescription:text(value(table,'table_comment','tableComment')),
        fileFormat:text(policy.fileFormat), maxFileBytes:policy.maxFileBytes,
        maxFileSizeMb:policy.maxFileSizeMb==null ? (policy.maxFileBytes==null ? null : Math.round(policy.maxFileBytes::double/1048576*1000000)/1000000.0) : policy.maxFileSizeMb,
        relatedTableName:text(policy.relatedTableName),relatedTableId:text(policy.relatedTableId),
        relatedFieldName:text(policy.relatedFieldName),relatedFieldId:text(policy.relatedFieldId),
        registered:(value(table,'asset_status','assetStatus')+'')=='2',
        fieldCount:(fieldNamesById[id] || []).size()
    }
    rows.add(row);byId[id]=row;byName[row.fileName.toLowerCase()]=row
}
if (action=='read') { return {datasourceId:sourceId,list:rows,total:rows.size(),resourceKind:'FILE'} }
var incoming=action=='complete' ? rows : body.files
if (!(incoming instanceof List) || incoming.size()==0 || incoming.size()>100) { exit 422, '请填写1至100项文件登记信息' }
var normalized=[];var names=new HashSet();var requestedIds=new HashSet();var relatedNames=new HashSet();var relatedFields=new HashSet()
for (item in incoming) {
    var name=text(item.fileName);var description=text(item.fileDescription)
    var format=text(item.fileFormat).toLowerCase().replace('.','')
    if (name=='' || name.length()>255 || description=='' || description.length()>2000) { exit 422, '文件名称、文件说明必填，名称最多255字，说明最多2000字' }
    if (!names.add(name.toLowerCase())) { exit 422, '同次导入的文件名称不能重复' }
    if (!['jpg','jpeg','png','pdf','docx'].contains(format)) { exit 422, '文件格式请选择JPG、JPEG、PNG、PDF或DOCX' }
    var sizeText=text(item.maxFileSizeMb)
    if (!sizeText.matches('[0-9]+(\\.[0-9]{1,6})?')) { exit 422, '文件大小上限必须是大于0的MB数值，最多6位小数' }
    var bytes=Math.round(sizeText::double*1048576)
    if (bytes<1 || bytes>1099511627776L) { exit 422, '文件大小上限须大于0且不超过1048576MB，实际还受海通节点限制' }
    var id=text(item.tableId)
    if (id!='' && byId[id]==null) { exit 404, '文件登记ID不存在或不属于当前数据源' }
    var old=id=='' ? byName[name.toLowerCase()] : byId[id]
    if (old!=null) { id=old.tableId }
    if (id=='') { id=IdUtil.getSnowflakeNextIdStr() }
    if (!requestedIds.add(id)) { exit 422, '同次导入不能重复引用同一文件登记项' }
    var relatedTable=text(action=='complete' && text(item.relatedTableId)!='' ? item.relatedTableId : item.relatedTableName)
    var relatedField=text(action=='complete' && text(item.relatedFieldId)!='' ? item.relatedFieldId : item.relatedFieldName)
    if (relatedTable.length()>255 || relatedField.length()>255 || (relatedTable=='' && relatedField!='')) { exit 422, '填写关联字段时须同时填写关联表，名称最多255字' }
    if (relatedTable!='') { relatedNames.add(relatedTable) }
    if (relatedField!='') { relatedFields.add(relatedField) }
    normalized.add({tableId:id,collectionId:old==null ? 'file_'+id : old.collectionId,fileName:name,fileDescription:description,fileFormat:format,maxFileBytes:bytes,maxFileSizeMb:sizeText::double,relatedTableName:relatedTable,relatedFieldName:relatedField,registered:action=='complete'})
}
var totalIds=new HashSet(oldIds);totalIds.addAll(requestedIds)
if (totalIds.size()>100) { exit 422, '同一数据源最多100项文件定义；重复导入按名称或登记ID更新，不删除未列出的已有项' }
// 所有关联记录一次批量读取，先验证全部归属再写入；不根据输入切换行业数据库。
var relatedByKey={};var relatedIds=[]
if (relatedNames.size()>0) {
    var matches=db.select('SELECT tid,table_name,table_type FROM db_table_t WHERE tenant_id=#{tenantId} AND is_del=0 AND (tid IN (#{relatedNames}) OR table_name IN (#{relatedNames}))',{tenantId:tenantId,relatedNames:relatedNames.toArray()})
    for (table in matches) {
        var id=text(table.tid);var name=text(value(table,'table_name','tableName'))
        relatedByKey[id]=[table]
        if (relatedByKey[name]==null) { relatedByKey[name]=[] }
        if (name!=id) { relatedByKey[name].add(table) }
        relatedIds.add(id)
    }
}
var relatedColumnByKey={}
if (relatedIds.size()>0 && relatedFields.size()>0) {
    var matches=db.select('SELECT tid,table_id,column_name FROM db_table_column_t WHERE tenant_id=#{tenantId} AND is_del=0 AND table_id IN (#{relatedIds}) AND (column_name IN (#{relatedFields}) OR tid IN (#{relatedFields}))',{tenantId:tenantId,relatedIds:relatedIds,relatedFields:relatedFields.toArray()})
    for (column in matches) {
        var tableId=text(value(column,'table_id','tableId'));var name=text(value(column,'column_name','columnName'))
        relatedColumnByKey[tableId+':'+name]=column;relatedColumnByKey[tableId+':'+text(column.tid)]=column
    }
}
for (row in normalized) {
    if (row.relatedTableName=='') { continue }
    var matches=relatedByKey[row.relatedTableName] || []
    if (matches.size()!=1 || text(value(matches[0],'table_type','tableType'))=='FILE') { exit 422, '关联表不存在、同名不唯一或不是结构化表；同名时可填写当前行业的登记表ID' }
    var table=matches[0];row.relatedTableId=text(table.tid);row.relatedTableName=text(value(table,'table_name','tableName'))
    if (row.relatedFieldName!='') {
        var column=relatedColumnByKey[row.relatedTableId+':'+row.relatedFieldName]
        if (column==null) { exit 422, '关联字段不存在或不属于所选关联表' }
        row.relatedFieldId=text(column.tid);row.relatedFieldName=text(value(column,'column_name','columnName'))
    }
}
// 重复保存相同定义保留完成状态；有业务变化时只将相关文件定义恢复为待结束登记。
var merged={};for (id,row in byId) { merged[id]=row }
for (row in normalized) {
    var old=byId[row.tableId]
    var unchanged=old!=null && old.fileName==row.fileName && old.fileDescription==row.fileDescription && old.fileFormat==row.fileFormat && old.maxFileBytes==row.maxFileBytes && old.relatedTableName==row.relatedTableName && old.relatedFieldName==row.relatedFieldName && text(old.relatedTableId)==text(row.relatedTableId) && text(old.relatedFieldId)==text(row.relatedFieldId)
    row.registered=action=='complete' || (unchanged && old.registered==true)
    merged[row.tableId]=row
}
var allRegistered=merged.size()>0
for (row in merged.values()) { if (row.registered!=true) { allRegistered=false } }
var sourceStatus=allRegistered ? 2 : 0
var attributes=[
    {name:'file_name',comment:'登记文件名称',type:'varchar',length:255,required:true},
    {name:'file_description',comment:'登记文件说明',type:'varchar',length:2000,required:true},
    {name:'file_format',comment:'允许的文件扩展名',type:'varchar',length:16,required:true},
    {name:'max_file_bytes',comment:'文件大小上限，单位字节',type:'bigint',length:0,required:true},
    {name:'related_table_name',comment:'可选关联数据表',type:'varchar',length:255,required:false},
    {name:'related_field_name',comment:'可选关联数据表字段',type:'varchar',length:255,required:false}
]
if (action=='complete') {
    for (row in normalized) {
        var savedNames=fieldNamesById[row.tableId] || []
        if (!savedNames.containsAll(attributes.map(attribute=>attribute.name))) { exit 422, '文件字段快照不完整，请返回第一步重新导入并保存' }
    }
}
if (body.dryRun==true) { return {valid:true,dryRun:true,datasourceId:sourceId,list:normalized,total:normalized.size(),resourceKind:'FILE'} }
var now=new Date()
db.transaction(() => {
    // 仅锁定当前源，不在文件循环中访问数据库。
    var locked=db.selectOne('SELECT tid FROM db_datasource_t WHERE tid=#{sourceId} AND tenant_id=#{tenantId} AND is_del=0 FOR UPDATE',{sourceId:sourceId,tenantId:tenantId})
    if (locked==null) { exit 404, '当前数据源已失效' }
    if (action=='complete') {
        db.table('db_table_t').where().eq('tenant_id',tenantId).eq('datasource_id',sourceId).eq('table_type','FILE').in('tid',normalized.map(row=>row.tableId)).update({asset_status:2,flow_status:2,updated_time:now})
        db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('tid',sourceId).update({asset_status:2,flow_status:2,updated_time:now})
        return
    }
    var insertTables=[];var updateTables=[];var insertColumns=[];var updateColumns=[]
    for (row in normalized) {
        var config=JSONUtil.toJsonStr({resourceKind:'FILE',fileRegistration:{fileFormat:row.fileFormat,maxFileBytes:row.maxFileBytes,maxFileSizeMb:row.maxFileSizeMb,relatedTableName:row.relatedTableName,relatedTableId:row.relatedTableId,relatedFieldName:row.relatedFieldName,relatedFieldId:row.relatedFieldId}})
        if (byId[row.tableId]==null) {
            insertTables.add({tid:row.tableId,datasource_id:sourceId,table_name:row.collectionId,table_name_cn:row.fileName,table_comment:row.fileDescription,table_type:'FILE',business_type:'文件',annotated:1,field_count:6,field_governance_config:config,tenant_id:tenantId,org_id:value(source,'org_id','orgId'),asset_status:0,flow_status:0,is_del:0,created_time:now,updated_time:now,reg_time:now})
        } else {
            updateTables.add([row.fileName,row.fileDescription,config,now,row.registered ? 2 : 0,row.registered ? 2 : 0,row.tableId,tenantId,sourceId].toArray())
        }
        var ordinal=0
        for (attribute in attributes) {
            ordinal++
            var old=columnByKey[row.tableId+':'+attribute.name]
            if (old==null) {
                insertColumns.add({tid:IdUtil.getSnowflakeNextIdStr(),table_id:row.tableId,column_name:attribute.name,column_comment:attribute.comment,data_type:attribute.type,column_type:attribute.type,length:attribute.length,nullable:attribute.required ? 0 : 1,primary_key:0,ordinal_position:ordinal,tenant_id:tenantId,is_del:0,created_time:now,updated_time:now})
            } else { updateColumns.add([attribute.comment,attribute.type,attribute.type,attribute.length,attribute.required ? 0 : 1,ordinal,now,text(old.tid),tenantId,row.tableId].toArray()) }
        }
    }
    if (insertTables.size()>0) { db.table('db_table_t').batchInsert(insertTables,100) }
    if (insertColumns.size()>0) { db.table('db_table_column_t').batchInsert(insertColumns,600) }
    if (updateTables.size()>0) { db.batchUpdate('UPDATE db_table_t SET table_name_cn=?,table_comment=?,field_governance_config=?,updated_time=?,asset_status=?,flow_status=? WHERE tid=? AND tenant_id=? AND datasource_id=? AND table_type=\'FILE\' AND is_del=0',updateTables) }
    if (updateColumns.size()>0) { db.batchUpdate('UPDATE db_table_column_t SET column_comment=?,data_type=?,column_type=?,length=?,nullable=?,ordinal_position=?,updated_time=? WHERE tid=? AND tenant_id=? AND table_id=? AND is_del=0',updateColumns) }
    db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('tid',sourceId).update({asset_status:sourceStatus,flow_status:sourceStatus,updated_time:now})
})
if (action=='complete') {
    body={tid:sourceId,assetType:'db'}
    try { refreshAssetIndex() } catch(e) { log.warn('文件登记状态已保存，数据源搜索投影刷新待重试') }
}
return {datasourceId:sourceId,list:normalized,total:normalized.size(),resourceKind:'FILE',saved:true,assetStatus:action=='complete' ? 2 : sourceStatus}
