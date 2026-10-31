{"id":"haitong-function-registry","groupId":"haitong-business-functions","name":"04.登记配置同步","path":"/registry","parameters":[{"name":"input","dataType":"Object"}],"description":"服务间密钥专用的完整登记快照同步。验证全批数据源/表/字段后事务维护镜像及授权。registryAction=STATUS 按至多100源/1000表一次读取镜像表和字段并返回实际指纹、启用状态、最近同步时间，不写镜像或幂等账本。"}
================================
// 业务：完整快照覆盖所选数据源的镜像；没有出现在快照中的表/字段/授权逻辑停用。
// 不以请求中的租户、连接、Topic或路径决定隔离；调用前必须通过服务间认证。
import '@/haitong/common' as common
import java.util.HashSet
import org.springframework.dao.DuplicateKeyException
var c=common();var _fail=c.fail;var _text=c.text;var _integer=c.integer;var _now=c.now;var _list=c.list;var _object=c.object;var _key=c.key;var _hash=c.hash;
if(!c.p.getStandaloneRegistry().isSyncEnabled()){_fail(503,'STANDALONE_REGISTRY_SYNC_NOT_READY','独立登记配置同步服务尚未启用')}
if(!input.caller.admin() && input.caller.id()!='platform-standalone-registry'){_fail(403,'FORBIDDEN_REGISTRY_SYNC','仅允许数据源平台同步独立登记配置')}
var root=input.body
// Read-only mirror inspection shares the service credential and the existing route.
if(root.registryAction=='STATUS'){
    var refs=_list(root.tableRefs)
    if(refs.size()>1000){_fail(413,'PAYLOAD_TOO_LARGE','同步状态每次最多1000张表')}
    var args=[];var tuples=[];var seenRefs=new HashSet();var sourceIds=new HashSet()
    for(ref in refs){
        _object(ref)
        var sourceId=_text(ref.datasourceId).trim();var tableName=_text(ref.tableName).trim()
        if(!sourceId.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,63}') || !tableName.matches('[A-Za-z0-9][A-Za-z0-9_-]{0,199}')){_fail(422,'INVALID_BODY','同步状态的源和表标识不合法')}
        if(!seenRefs.add(sourceId+':'+tableName)){_fail(422,'INVALID_BODY','同步状态表标识不得重复')}
        sourceIds.add(sourceId);args.add(sourceId);args.add(tableName);tuples.add('(?,?)')
    }
    if(sourceIds.size()>100){_fail(413,'PAYLOAD_TOO_LARGE','同步状态每次最多100个数据源')}
    if(refs.size()==0){return {code:'REGISTRY_STATUS',readOnly:true,tables:[]}}
    var marks=tuples.join(',')
    var tables=c.jdbc.queryForList('SELECT t.*,s.is_enable AS source_enabled,s.is_del AS source_deleted FROM ht_standalone_table t JOIN ht_standalone_datasource s ON s.datasource_id=t.datasource_id WHERE (t.datasource_id,t.table_name) IN ('+marks+') ORDER BY t.datasource_id,t.table_name',args.toArray())
    var fields=c.jdbc.queryForList('SELECT datasource_id,table_name,column_name,column_comment,data_type,ordinal_no,is_required,is_enable FROM ht_standalone_table_column WHERE is_del=0 AND (datasource_id,table_name) IN ('+marks+') ORDER BY datasource_id,table_name,ordinal_no,column_name LIMIT 10001',args.toArray())
    if(fields.size()>10000){_fail(413,'PAYLOAD_TOO_LARGE','同步状态字段超过10000项')}
    var byTable={}
    for(field in fields){
        var name=field.datasource_id+':'+field.table_name
        if(byTable[name]==null){byTable[name]=[]}
        byTable[name].add({columnName:_text(field.column_name),columnComment:_text(field.column_comment),dataType:_text(field.data_type),ordinal:field.ordinal_no::int,required:field.is_required==1,enabled:field.is_enable==1})
    }
    var result=[]
    for(table in tables){
        var definition={tableId:_text(table.table_id),tableName:_text(table.table_name),tableComment:_text(table.table_comment),schemaVersion:_text(table.schema_version),enabled:table.is_enable==1,columns:byTable[table.datasource_id+':'+table.table_name] || []}
        result.add({datasourceId:table.datasource_id,tableName:table.table_name,definitionHash:_hash(c.json.bytes([definition.tableId,definition.tableName,definition.tableComment,definition.schemaVersion,definition.enabled,definition.columns.map(field=>[field.columnName,field.columnComment,field.dataType,field.ordinal,field.required,field.enabled])])),active:table.is_enable==1 && table.is_del==0 && table.source_enabled==1 && table.source_deleted==0,syncedAt:table.updated_at})
    }
    return {code:'REGISTRY_STATUS',readOnly:true,tables:result}
}

var key=_key(input.key);var hash=_hash(input.raw)
if(root.containsKey('datasource') && root.containsKey('datasources')){_fail(422,'INVALID_BODY','datasource 与 datasources 不能同时提交')}
if(input.raw.length>1048576){_fail(413,'PAYLOAD_TOO_LARGE','登记配置最多1MiB')}
var batch=root.containsKey('datasources')
var sources=batch?_list(root.datasources):[_object(root.datasource)]
if(sources.size()==0 || sources.size()>100){_fail(422,'INVALID_BODY','每批1至100个数据源')}
var id=(value,label,max)=>{var s=_text(value).trim();if(s.length()<1 || s.length()>max || !s.matches('[A-Za-z0-9][A-Za-z0-9_-]*')){_fail(422,'INVALID_BODY',label+'仅允许字母、数字、下划线和连字符')}return s}
var limited=(value,max)=>{var s=_text(value).trim();if(s.length()>max){_fail(422,'INVALID_BODY','字段超过长度限制')}return s}
var flag=(value,fallback)=>value==null?fallback:(value==true || value==1 || value=='true' || value=='1')
var ids=[];var seen=new HashSet();var tableCount=0;var columnCount=0
var sourceRows=[];var clientRows=[];var tableRows=[];var fieldRows=[];var credentialRows=[];var now=_now()
for(source in sources){
    _object(source);var sourceId=id(source.datasourceId,'datasourceId',64)
    if(!seen.add(sourceId)){_fail(422,'INVALID_BODY','datasourceId不得重复')};ids.add(sourceId)
    var name=limited(source.datasourceName,255);if(name==''){_fail(422,'INVALID_BODY','datasourceName必填')}
    sourceRows.add([sourceId,name,limited(source.sourceSystemId,128),limited(source.sourceSystemName,255),limited(source.contractVersion,100),flag(source.enabled,true)?1:0,0,now,now])
    var clients=_list(source.clientIds);var clientNames=new HashSet()
    if(clients.size()>100){_fail(422,'INVALID_BODY','每源最多100个调用方')}
    for(client in clients){var value=id(client,'clientId',128);if(!clientNames.add(value)){_fail(422,'INVALID_BODY','clientId不得重复')}clientRows.add([sourceId,value,1,0,now,now])}
    // Only the authenticated registration service may provision a source-scoped digest.
    if(source.credential!=null){
        var credential=_object(source.credential)
        var credentialClient=_text(credential.clientId);var digest=_text(credential.apiKeySha256);var previous=_text(credential.previousSha256)
        if(credentialClient!='push-'+sourceId || !clientNames.contains(credentialClient) || !digest.matches('[a-f0-9]{64}') || (previous!='' && !previous.matches('[a-f0-9]{64}'))){_fail(422,'INVALID_CREDENTIAL_BINDING','密钥摘要必须绑定当前数据源的专用调用方')}
        credentialRows.add({clientId:credentialClient,hash:digest,previous:previous})
    }
    var tables=_list(source.tables);var names=new HashSet()
    if(tables.size()>100){_fail(422,'INVALID_BODY','每数据源最多100张表')}
    for(table in tables){
        _object(table);var tableName=id(table.tableName,'tableName',200)
        if(!names.add(tableName)){_fail(422,'INVALID_BODY','tableName不得重复')}
        tableRows.add([sourceId,tableName,limited(table.tableId,128),limited(table.tableComment,255),limited(table.schemaVersion,100),flag(table.enabled,true)?1:0,0,now,now]);tableCount++
        var fields=_list(table.columns);var fieldNames=new HashSet();var ordinal=0
        if(fields.size()==0 || fields.size()>10000){_fail(422,'INVALID_BODY','每表必须有1至10000个字段')}
        for(field in fields){
            _object(field);var fieldName=id(field.columnName,'columnName',200)
            if(!fieldNames.add(fieldName)){_fail(422,'INVALID_BODY','同表字段名不得重复')}
            ordinal++;var position=_integer(field.ordinal,ordinal)
            if(position<1 || position>10000){_fail(422,'INVALID_BODY','ordinal必须在1至10000之间')}
            fieldRows.add([sourceId,tableName,fieldName,limited(field.columnComment,255),limited(field.dataType,100),position,flag(field.required,false)?1:0,flag(field.enabled,true)?1:0,0,now,now]);columnCount++
        }
    }
}
if(tableCount>1000 || columnCount>10000){_fail(422,'INVALID_BODY','批量登记配置最多1000表和10000字段')}
var result={code:'REGISTRY_SYNCED',duplicate:false,tableCount:tableCount,columnCount:columnCount,credentialCount:credentialRows.size()}
if(batch){result.datasourceIds=ids;result.datasourceCount=ids.size()}else{result.datasourceId=ids[0]}
// SQL表名和列名完全由脚本固定白名单提供，业务输入只作为绑定参数。
var upsert=(table,names,keys,rows)=>{
    var columns=names.split(',');var values='('+columns.map(n=>'?').join(',')+')'
    var assignments=columns.filter(n=>!keys.contains(n) && n!='created_at').map(n=>n+'=VALUES('+n+')').join(',')
    var offset=0
    while(offset<rows.size()){
        var chunk=rows.subList(offset,Math.min(offset+500,rows.size()));var args=[]
        for(row in chunk){args.addAll(row)}
        c.jdbc.update('INSERT INTO '+table+' ('+names+') VALUES '+chunk.map(r=>values).join(',')+' ON DUPLICATE KEY UPDATE '+assignments,args.toArray());offset+=500
    }
}
try{
    return c.tx.execute(status=>{
        var prior=c.jdbc.queryForList('SELECT payload_sha256,datasource_id FROM ht_standalone_registry_sync WHERE idempotency_key=?',[key].toArray())
        if(prior.size()>0){
            if(prior[0].payload_sha256!=hash){_fail(409,'IDEMPOTENCY_CONFLICT','幂等键已用于不同内容')}
            result.code='ALREADY_SYNCED';result.duplicate=true
            if(!batch){result.tableCount=0;result.columnCount=0}
            return result
        }
        var snapshotId=batch?'batch-'+_hash(c.json.bytes(ids)).substring(0,58):ids[0]
        if(credentialRows.size()>0){
            var clientIds=credentialRows.map(row=>row.clientId);var clientMarks=clientIds.map(value=>'?').join(',')
            var existing=c.jdbc.queryForList('SELECT client_id,api_key_sha256,is_enable,is_del FROM ht_push_api_client WHERE client_id IN ('+clientMarks+') FOR UPDATE',clientIds.toArray())
            var byClient={};for(row in existing){byClient[row.client_id]=row}
            for(credential in credentialRows){
                var old=byClient[credential.clientId]
                if(old!=null && (old.is_enable::int!=1 || old.is_del::int!=0)){_fail(409,'CREDENTIAL_DISABLED','数据源业务密钥已停用')}
                if(old!=null && old.api_key_sha256!=credential.hash && old.api_key_sha256!=credential.previous){_fail(409,'CREDENTIAL_CONFLICT','海通已有不同版本的密钥，请核对凭据状态后重试同步')}
            }
            for(credential in credentialRows){
                var old=byClient[credential.clientId]
                if(old==null){c.jdbc.update('INSERT INTO ht_push_api_client(client_id,api_key_sha256,is_enable,is_del,created_at,updated_at) VALUES (?,?,1,0,?,?)',[credential.clientId,credential.hash,now,now].toArray())}
                else if(old.api_key_sha256!=credential.hash){c.jdbc.update('UPDATE ht_push_api_client SET api_key_sha256=?,updated_at=? WHERE client_id=?',[credential.hash,now,credential.clientId].toArray())}
            }
        }
        c.jdbc.update('INSERT INTO ht_standalone_registry_sync(idempotency_key,payload_sha256,datasource_id,applied_at) VALUES (?,?,?,?)',[key,hash,snapshotId,now].toArray())
        var args=[now];args.addAll(ids);var marks=ids.map(value=>'?').join(',')
        for(table in ['ht_standalone_datasource_client','ht_standalone_table_column','ht_standalone_table']){c.jdbc.update('UPDATE '+table+' SET is_enable=0,is_del=1,updated_at=? WHERE datasource_id IN ('+marks+')',args.toArray())}
        upsert('ht_standalone_datasource','datasource_id,datasource_name,source_system_id,source_system_name,contract_version,is_enable,is_del,created_at,updated_at',['datasource_id'],sourceRows)
        upsert('ht_standalone_datasource_client','datasource_id,client_id,is_enable,is_del,created_at,updated_at',['datasource_id','client_id'],clientRows)
        upsert('ht_standalone_table','datasource_id,table_name,table_id,table_comment,schema_version,is_enable,is_del,created_at,updated_at',['datasource_id','table_name'],tableRows)
        upsert('ht_standalone_table_column','datasource_id,table_name,column_name,column_comment,data_type,ordinal_no,is_required,is_enable,is_del,created_at,updated_at',['datasource_id','table_name','column_name'],fieldRows)
        return result
    })
}catch(e){
    var error=e
    while(error!=null){if(error instanceof DuplicateKeyException){_fail(409,'IDEMPOTENCY_CONFLICT','同步幂等键正在使用，请用原请求稍后重试')}error=error.getCause()}
    throw e
}
