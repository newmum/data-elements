{"id":"haitong-function-table-contract","groupId":"haitong-business-functions","name":"02.表批次登记规范校验","path":"/table-contract","parameters":[{"name":"input","dataType":"Object"}],"description":"独立模式按数据源镜像、调用方及表字段校验每条记录；固定4次查询，不逐条读取数据库。已存在数据库记录时不回退YAML。"}
================================
// 功能：独立表契约校验。归属来自服务器授权关系；不接受请求选择租户/连接。
import '@/haitong/common' as common
var c=common();var _fail=c.fail;var _text=c.text;var _list=c.list;var _object=c.object;
var batch=input.batch
var source=_text(batch.datasourceId)
var table=_text(batch.tableName)
if(!source.matches('[A-Za-z0-9_-]{1,64}')){_fail(422,'INVALID_BODY','datasourceId 必须是已配置的数据源标识')}
var found=c.jdbc.queryForList("""
    SELECT datasource_id,is_enable,is_del FROM ht_standalone_datasource WHERE datasource_id=?
    """,[source].toArray())
var required=[];var allowed=[];var version=''
if(found.size()>0){
    var row=found[0]
    if(row.is_del::int != 0 || row.is_enable::int != 1){_fail(422,'STANDALONE_DATASOURCE_DISABLED','独立数据源已停用')}
    var grants=c.jdbc.queryForList('SELECT client_id FROM ht_standalone_datasource_client WHERE datasource_id=? AND is_enable=1 AND is_del=0',[source].toArray())
    if(grants.size()>0 && !grants.map(g=>g.client_id).contains(input.caller.id())){_fail(403,'STANDALONE_DATASOURCE_FORBIDDEN','当前调用方无此独立数据源的权限')}
    var tables=c.jdbc.queryForList('SELECT schema_version,is_enable,is_del FROM ht_standalone_table WHERE datasource_id=? AND table_name=?',[source,table].toArray())
    if(tables.size()==0){_fail(422,'STANDALONE_TABLE_NOT_CONFIGURED','独立运行模式未配置此数据表')}
    if(tables[0].is_del::int!=0 || tables[0].is_enable::int!=1){_fail(422,'STANDALONE_TABLE_DISABLED','独立数据表已停用')}
    version=_text(tables[0].schema_version)
    var columns=c.jdbc.queryForList('SELECT column_name,is_required FROM ht_standalone_table_column WHERE datasource_id=? AND table_name=? AND is_enable=1 AND is_del=0 ORDER BY ordinal_no,column_name',[source,table].toArray())
    if(columns.size()==0){_fail(422,'STANDALONE_TABLE_NOT_CONFIGURED','独立数据表未配置有效字段')}
    allowed=columns.map(f=>f.column_name)
    required=columns.filter(f=>f.is_required::int==1).map(f=>f.column_name)
}else{
    var configured=c.p.getStandalone().getDatasources().get(source)
    if(configured==null){_fail(422,'STANDALONE_DATASOURCE_NOT_CONFIGURED','独立运行模式未配置此 datasourceId')}
    if(configured.getClients().size()>0 && !configured.getClients().contains(input.caller.id())){_fail(403,'STANDALONE_DATASOURCE_FORBIDDEN','当前调用方无此独立数据源的权限')}
    var spec=configured.getTables().get(table)
    if(spec==null){_fail(422,'STANDALONE_TABLE_NOT_CONFIGURED','独立运行模式未配置此数据表')}
    required=spec.getRequiredFields();allowed=spec.getAllowedFields();version=_text(spec.getSchemaVersion())
}
for(row in _list(batch.records)){
    _object(row)
    for(field in required){if(row[field]==null || (row[field] instanceof String && row[field].trim()=='')){_fail(422,'STANDALONE_REQUIRED_FIELD_MISSING','独立表契约缺少必填字段：'+field)}}
    for(field in row.keySet()){if(allowed.size()>0 && !allowed.contains(field)){_fail(422,'STANDALONE_FIELD_NOT_ALLOWED','独立表契约不允许字段：'+field)}}
}
var result={mode:'standalone',datasourceId:source,tableName:table,recordCount:batch.records.size()}
if(version!=''){result.schemaVersion=version}
return result
