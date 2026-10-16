{
  "properties": {},
  "id": "bc30a3edb1f04bceaeee373ed5952d4a",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "13.数据表预览",
  "createTime": 1782955344263,
  "updateTime": 1787156076929,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/table/preview-data",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"f370414d9ff64565a9fa06cbaa13d861\",\n    \"pageNo\": 1,\n    \"pageSize\": 20,\n    \"conditions\": {\n        \"datasource\": \"1\",\n        \"id\": \"00e4a8e8cc254c708925a853200bf5b9\"\n    },\n    \"columns\": \"测试\",\n    \"sorts\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "元数据管理 13：数据表分页预览；使用请求参数 tableId、pageNo、pageSize、columns、conditions、sorts、groupByColumn、groupNameColumn；涉及 db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：13.数据表预览
// 路由：POST /dst/database/metadata/table/preview-data
// 业务说明：元数据管理 13：数据表分页预览；使用请求参数 tableId、pageNo、pageSize、columns、conditions、sorts、groupByColumn、groupNameColumn；涉及 db_datasource_t、db_table_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import tenantRuntime
import dataScope
import tableSort
import hwhive
import java.util.HexFormat
import java.util.List
import java.lang.Number
import java.lang.Boolean
var tenantId=tenantRuntime.id()
if(tenantId==null){exit 401,"当前租户无效";}
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.dto.PageQueryCondition
import com.linewell.dataelement.metautil.model.dto.PageQueryRequest
import com.linewell.dataelement.metautil.model.dto.PageQuerySort
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataDataQueryService as metadataDataQueryService
import java.util.Map

/*
 * 元数据管理 13：数据表分页预览。
 *
 * 只读接口。根据 tableId 解析表资产和数据源资产，支持 pageNo/pageSize、columns、conditions、sorts。
 * conditions 支持 Map 形式的模糊查询，也兼容数组形式的 {field, op, value} 条件。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var loadAsset = (assetId) => {
    if (blank(assetId)) {
        // 返回当前业务分支的标准结果。
        return null
    }
    // 从 MySQL 业务表读取当前有效数据。
    var main = db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('tid', assetId).eq('is_del', 0).selectOne()
    var assetType = 'db'
    if (main == null) {
        // 从 MySQL 业务表读取当前有效数据。
        main = db.table('db_table_t').where().eq('tenant_id',tenantId).eq('tid', assetId).eq('is_del', 0).selectOne()
        assetType = 'table'
    }
    if (main == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: rowValue(main, ['tid']), id: rowValue(main, ['tid']), assetType: assetType,
        tenantId:tenantId,orgId:rowValue(main,['orgId','org_id']),createdBy:rowValue(main,['createdBy','created_by']),
        dbName: rowValue(main, ['dbName', 'db_name']),
        dbId: rowValue(main, ['datasourceId', 'datasource_id']),
        tableName: rowValue(main, ['tableName', 'table_name']),
        tableNameEn: rowValue(main, ['tableName', 'table_name']),
        dbType: rowValue(main, ['dbType', 'db_type']),
        jdbcURL: rowValue(main, ['jdbcUrl', 'jdbc_url']),
        username: rowValue(main, ['username']),
        password: rowValue(main, ['password'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(main, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) { result.putAll(connectionConfig) }
    if (!blank(rowValue(main, ['dbName', 'db_name']))) { result.dbName = rowValue(main, ['dbName', 'db_name']) }
    if (!blank(rowValue(main, ['dbType', 'db_type']))) { result.dbType = rowValue(main, ['dbType', 'db_type']) }
    if (rowValue(main, ['jdbcUrl', 'jdbc_url']) != null) { result.jdbcURL = rowValue(main, ['jdbcUrl', 'jdbc_url']) }
    if (rowValue(main, ['username']) != null) { result.username = rowValue(main, ['username']) }
    if (rowValue(main, ['password']) != null) { result.password = rowValue(main, ['password']) }
    if (!blank(rowValue(main, ['datasourceId', 'datasource_id']))) { result.dbId = rowValue(main, ['datasourceId', 'datasource_id']) }
    if (!blank(rowValue(main, ['tableName', 'table_name']))) {
        result.tableName = rowValue(main, ['tableName', 'table_name'])
        result.tableNameEn = rowValue(main, ['tableNameEn', 'table_name_en', 'tableName', 'table_name'])
        result.tableNameCn = rowValue(main, ['tableNameCn', 'table_name_cn'])
        result.tableComment = rowValue(main, ['tableComment', 'table_comment'])
    }
    // 返回当前业务分支的标准结果。
    return result
}
var databaseTypeOf = (code) => {
    if (blank(code)) {
        // 返回当前业务分支的标准结果。
        return DatabaseType.MYSQL
    }
    try {
        // 返回当前业务分支的标准结果。
        return DatabaseType.fromCode(textOf(code).toLowerCase())
    } catch (e) {
        // 返回当前业务分支的标准结果。
        return DatabaseType.MYSQL
    }
}
var dataSourceOf = (asset) => {
    var databaseType = databaseTypeOf(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType']))
    var zookeeperQuorum = firstValue(asset, ['zookeeperQuorum', 'zkQuorum'])
    var hiveZooKeeperDiscovery = databaseType == DatabaseType.HIVE && !blank(zookeeperQuorum)
    var host = firstValue(asset, ['host', 'dbIp'])
    var port = firstValue(asset, ['port', 'dbPort'])
    var databaseName = firstValue(asset, ['database', 'dbName', 'dbMetaDbName'])
    var username = firstValue(asset, ['username', 'dbUser', 'dbMetaUser', 'user'])
    var password = firstValue(asset, ['password', 'dbPassword', 'dbMetaPassword'])
    if (blank(databaseName) || (!hiveZooKeeperDiscovery && (blank(host) || blank(username)))) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 400, '数据源连接参数不完整'
    }
    var dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(databaseType)
    dsConfig.setHost(blank(host) ? (hiveZooKeeperDiscovery ? '' : 'virtual') : textOf(host))
    dsConfig.setPort(blank(port) ? databaseType.getDefaultPort() : port::int)
    dsConfig.setDatabase(textOf(databaseName))
    dsConfig.setUsername(blank(username) ? '' : textOf(username))
    dsConfig.setPassword(password == null ? '' : textOf(password))
    // Oracle SID and Service Name use different URL formats; reuse the tested JDBC URL.
    dsConfig.setJdbcUrl(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    dsConfig.setSchema(firstValue(asset, ['schema', 'defaultSchema']))
    dsConfig.setAuthMode(firstValue(asset, ['authMode', 'auth']))
    dsConfig.setPrincipal(firstValue(asset, ['principal', 'hivePrincipal', 'servicePrincipal']))
    dsConfig.setUserPrincipal(firstValue(asset, ['userPrincipal', 'clientPrincipal', 'kerberosUserPrincipal']))
    dsConfig.setKeytabPath(firstValue(asset, ['keytabPath', 'keytab', 'userKeytab']))
    dsConfig.setKrb5ConfPath(firstValue(asset, ['krb5ConfPath', 'krb5Conf', 'krb5']))
    dsConfig.setJaasConfPath(firstValue(asset, ['jaasConfPath', 'jaasConfig']))
    dsConfig.setZookeeperQuorum(blank(zookeeperQuorum) ? null : textOf(zookeeperQuorum))
    dsConfig.setZookeeperNamespace(firstValue(asset, ['zookeeperNamespace', 'zooKeeperNamespace']))
    dsConfig.setServiceDiscoveryMode(firstValue(asset, ['serviceDiscoveryMode', 'serviceDiscovery']))
    dsConfig.setSaslQop(firstValue(asset, ['saslQop', 'sasl.qop']))
    dsConfig.setClientConfigDir(firstValue(asset, ['clientConfigDir', 'mrsClientConfigDir']))
    // 返回当前业务分支的标准结果。
    return dsConfig
}
var toConditionList = (conditions) => {
    var list = []
    if (conditions == null) {
        // 返回当前业务分支的标准结果。
        return list
    }
    if (conditions instanceof Map) {
        for (key in conditions.keySet()) {
            var value = conditions[key]
            if (!blank(value)) {
                var c = new PageQueryCondition()
                c.setField(key)
                c.setOp('like')
                c.setValue(value)
                list.add(c)
            }
        }
        // 返回当前业务分支的标准结果。
        return list
    }
    for (item in conditions) {
        var field = firstValue(item, ['field', 'column', 'name'])
        if (blank(field)) {
            continue
        }
        var c = new PageQueryCondition()
        c.setField(field)
        c.setOp(firstValue(item, ['op', 'operator']) == null ? 'like' : firstValue(item, ['op', 'operator']))
        c.setValue(rowValue(item, ['value']))
        list.add(c)
    }
    // 返回当前业务分支的标准结果。
    return list
}
var toSortList = (sorts) => {
    var list = []
    if (sorts == null) {
        // 返回当前业务分支的标准结果。
        return list
    }
    for (item in sorts) {
        var field = firstValue(item, ['field', 'column', 'name'])
        if (blank(field)) {
            continue
        }
        var s = new PageQuerySort()
        s.setField(field)
        s.setDirection(firstValue(item, ['direction', 'order']) == null ? 'ASC' : firstValue(item, ['direction', 'order']))
        list.add(s)
    }
    // 返回当前业务分支的标准结果。
    return list
}

// 解析请求体并为可选参数设置默认值。
var tableId = body == null ? null : body.tableId
if (blank(tableId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableId不能为空'
}

var table = loadAsset(tableId)
if (table == null || table.assetType != 'table') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableId不正确'
}
var dbAsset = loadAsset(firstValue(table, ['dbId']))
if (dbAsset == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '表所属数据源不存在'
}

if(dataScope.visibleDataSourcesFor('MENU:2081000000000000002',[dbAsset]).size()!=1){exit 403,'表所属数据源不在当前角色数据范围内';}
// 一次批量读取当前租户的字段快照，同时用于中文表头、排序白名单和主键补序。
var fieldRows=db.table('db_table_column_t').where().eq('tenant_id',tenantId).eq('table_id',tableId).eq('is_del',0).page(1001,0)
if(fieldRows.total>1000){exit 413,'字段超过1000项，不能使用此数据预览';}
var columnLabels={}
for(row in fieldRows.list){
    var name=firstValue(row,['columnName','column_name'])
    var label=firstValue(row,['columnNameCn','column_name_cn','columnComment','column_comment'])
    if(!blank(name)&&!blank(label)){columnLabels[textOf(name).toLowerCase()]=textOf(label)}
}
var request = new PageQueryRequest()
request.setTableName(firstValue(table, ['tableName', 'tableNameEn']))
request.setPageNo(body == null || body.pageNo == null ? 1 : body.pageNo::int)
request.setPageSize(body == null || body.pageSize == null ? 20 : body.pageSize::int)
if (body != null && body.columns != null) {
    if(!(body.columns instanceof List)||body.columns.size()>1000){exit 400,'预览字段必须是最多1000项的数组';}
    request.setColumns(body.columns)
}
request.setConditions(toConditionList(body == null ? null : body.conditions))
if(request.getConditions().size()>50){exit 400,'最多选择50个预览过滤条件';}
var requestedSorts=body?.sorts==null?[]:body.sorts
if(!blank(body?.sortField)){requestedSorts=[{field:body.sortField,direction:tableSort.direction(body)}]}
if(!(requestedSorts instanceof List)){exit 400,'排序字段必须是数组';}
if(requestedSorts.size()>10){exit 400,'最多选择10个排序字段';}
if(request.getPageNo()<1||request.getPageNo()>100000||request.getPageSize()<1||request.getPageSize()>100){exit 400,'数据预览分页参数无效';}

var sourceType=textOf(firstValue(dbAsset,['dbType','databaseType','dataSourceType'])).toLowerCase()
// hwhive 对应 platform.magic.module.HiveModule，只用于已登记为服务端托管的 MRS 连接。
// 直接 JDBC Hive 保持自己的连接，不能误查服务端连接池所指向的另一套集群。
var managedHive=sourceType=='hive' && (
    textOf(firstValue(dbAsset,['metadataAccessMode','metadata_access_mode']))=='server-managed-mrs'
    || !blank(firstValue(dbAsset,['hiveProfile','hive_profile']))
)
if(managedHive){
    var queryHive=(sql)=>{
        try{return hwhive.queryTable(sql)}catch(e){
            var message=e.getMessage()==null?'':e.getMessage()
            if(message.contains('hiveclient.properties')&&message.contains('not found')){
                exit 503,'Hive 服务端缺少 hiveclient.properties 客户端配置，请配置 MRS Hive 连接后重试'
            }
            exit 502,'Hive 数据预览查询失败，请检查服务端连接状态、查询权限和错误日志'
        }
    }
    var hiveDatabase=firstValue(dbAsset,['database','schema','dbMetaDbName','dbName'])
    var hiveTable=textOf(request.getTableName())
    var parts=hiveTable.split('\\.')
    if(parts.size()==2){
        if(blank(hiveDatabase)||!parts[0].equalsIgnoreCase(textOf(hiveDatabase))){exit 400,'Hive 表不属于登记的数据源库';}
        hiveTable=parts[1]
    }else if(parts.size()!=1){exit 400,'Hive 表名格式不正确';}
    if(blank(hiveDatabase)||!textOf(hiveDatabase).matches('[A-Za-z_][A-Za-z0-9_]*')||!hiveTable.matches('[A-Za-z_][A-Za-z0-9_]*')){exit 400,'Hive 库名或表名格式不正确';}
    var qualified='`'+hiveDatabase+'`.`'+hiveTable+'`'
    var hiveValue=(row,name)=>{
        for(key in row.keySet()){
            var shortName=textOf(key).substring(textOf(key).lastIndexOf('.')+1)
            if(shortName.equalsIgnoreCase(name)){return row[key]}
        }
        return null
    }
    var physicalColumns=[]
    var physicalTypes={}
    var allowed={}
    // DESCRIBE 一次得到实际字段；分区列的重复段去重，不按每列发起查询。
    var described=queryHive('DESCRIBE '+qualified)
    for(row in described){
        var name=textOf(hiveValue(row,'col_name'))
        if(blank(name)||name.trim().startsWith('#')){continue}
        name=name.trim()
        if(allowed.containsKey(name.toLowerCase())){continue}
        if(name.length()>256||name.contains('\u0000')||name.contains('\n')||name.contains('\r')){exit 400,'Hive 字段名格式不正确';}
        physicalColumns.add(name)
        allowed[name.toLowerCase()]=name
        physicalTypes[name.toLowerCase()]=hiveValue(row,'data_type')
        var comment=hiveValue(row,'comment')
        if(blank(columnLabels[name.toLowerCase()])&&!blank(comment)){columnLabels[name.toLowerCase()]=textOf(comment)}
    }
    if(physicalColumns.size()==0||physicalColumns.size()>1000){exit 400,'Hive 表字段为空或超过1000项';}
    var checkedField=(value)=>{
        var name=blank(value)?null:allowed[textOf(value).toLowerCase()]
        if(name==null){exit 400,'预览字段不存在或不允许访问';}
        return name
    }
    var quote=(value)=>'`'+checkedField(value).replace('`','``')+'`'
    // queryTable 接受 SQL 文本：把字符串转为 UTF-8 十六进制，避免引号/反斜杠改变 SQL 结构。
    var stringLiteral=(value)=>{
        var text=textOf(value)
        if(text.length()>4000){exit 400,'过滤值超过4000字符';}
        return "decode(unhex('"+HexFormat.of().formatHex(text.getBytes('UTF-8'))+"'),'UTF-8')"
    }
    var clauses=[]
    for(condition in request.getConditions()){
        var field=quote(condition.getField())
        var value=condition.getValue()
        var op=textOf(condition.getOp()).toLowerCase()
        if(op=='eq'){
            if(value==null){clauses.add(field+' IS NULL')}
            else if(value instanceof Number){
                if(!textOf(value).matches('[-+]?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?')){exit 400,'数字过滤值无效';}
                clauses.add(field+' = '+value)
            }else if(value instanceof Boolean){clauses.add(field+' = '+(value?'TRUE':'FALSE'))}
            else{clauses.add(field+' = '+stringLiteral(value))}
        }else if(op=='like'){
            if(value!=null){clauses.add('CAST('+field+' AS STRING) LIKE '+stringLiteral('%'+value+'%'))}
        }else{exit 400,'仅支持 eq 和 like 过滤条件';}
    }
    var where=clauses.size()==0?'':' WHERE '+clauses.join(' AND ')
    if(!blank(body?.groupByColumn)){
        var group=quote(body.groupByColumn)
        var groupName=blank(body.groupNameColumn)?group:quote(body.groupNameColumn)
        var groups=queryHive('SELECT '+group+' AS category_value, MIN('+groupName+') AS category_name, COUNT(*) AS record_count FROM '+qualified+where+' GROUP BY '+group+' ORDER BY '+group+' LIMIT 10001')
        if(groups.size()>10000){exit 413,'分组超过10000项，请缩小登记范围';}
        var values=[]
        for(row in groups){
            var value=hiveValue(row,'category_value')
            if(!blank(value)){values.add({value:value,name:blank(body.groupNameColumn)?null:hiveValue(row,'category_name'),count:hiveValue(row,'record_count')})}
        }
        return {groupedValues:values}
    }
    var selected=[]
    for(name in body?.columns==null||body.columns.size()==0?physicalColumns:body.columns){
        var checked=checkedField(name)
        if(!selected.contains(checked)){selected.add(checked)}
    }
    var orders=[]
    var orderFields=[]
    for(item in requestedSorts){
        var name=checkedField(firstValue(item,['field','column','name']))
        var direction=firstValue(item,['direction','order'])
        orders.add(quote(name)+' '+tableSort.direction({sortDir:direction==null?'asc':direction})+' NULLS LAST')
        orderFields.add(name)
    }
    for(row in fieldRows.list){
        var primary=rowValue(row,['primaryKey','primary_key'])
        var name=firstValue(row,['columnName','column_name'])
        var actual=blank(name)?null:allowed[textOf(name).toLowerCase()]
        if((''+primary=='1'||primary==true)&&actual!=null&&!orderFields.contains(actual)){
            orders.add(quote(actual)+' ASC NULLS LAST');orderFields.add(actual)
        }
    }
    // 无主键时使用可排序的标量字段，确保翻页和同值排序稳定；复杂类型不参与 ORDER BY。
    for(name in physicalColumns){
        var type=textOf(physicalTypes[name.toLowerCase()])
        if(type!=null&&!type.toLowerCase().matches('(array|map|struct|uniontype).*')&&!orderFields.contains(name)){
            orders.add(quote(name)+' ASC NULLS LAST');orderFields.add(name)
        }
    }
    var projection=[]
    var types=[]
    for(name in selected){projection.add(quote(name));types.add(physicalTypes[name.toLowerCase()])}
    var countRows=queryHive('SELECT COUNT(*) AS __preview_total FROM '+qualified+where)
    if(countRows==null||countRows.size()!=1||hiveValue(countRows[0],'__preview_total')==null){exit 500,'Hive 预览总数查询结果无效';}
    var offset=(request.getPageNo()-1)*request.getPageSize()
    var orderSql=orders.size()==0?'':' ORDER BY '+orders.join(', ')
    var dataRows=queryHive('SELECT '+projection.join(', ')+' FROM '+qualified+where+orderSql+' LIMIT '+offset+', '+request.getPageSize())
    var rows=[]
    for(row in dataRows){
        var normalized={}
        for(name in selected){normalized[name]=hiveValue(row,name)}
        rows.add(normalized)
    }
    return {rows:rows,columns:selected,columnTypes:types,columnLabels:columnLabels,total:hiveValue(countRows[0],'__preview_total'),pageNo:request.getPageNo(),pageSize:request.getPageSize()}
}

request.setDataSource(dataSourceOf(dbAsset))
if(requestedSorts.size()>0){
    var allowedFields={}
    for(row in fieldRows.list){var fieldName=rowValue(row,['columnName','column_name']);if(!blank(fieldName)){allowedFields[fieldName]=fieldName}}
    var sorts=[]
    var selectedFields=[]
    for(item in requestedSorts){
        var fieldName=firstValue(item,['field','column','name'])
        var chosen=tableSort.field({sortField:fieldName},allowedFields,fieldName)
        var direction=firstValue(item,['direction','order'])
        sorts.add({field:chosen,direction:tableSort.direction({sortDir:direction==null?'asc':direction})})
        selectedFields.add(chosen)
    }
    for(row in fieldRows.list){
        var primary=rowValue(row,['primaryKey','primary_key'])
        var fieldName=rowValue(row,['columnName','column_name'])
        if((''+primary=='1'||primary==true)&&!selectedFields.contains(fieldName)){sorts.add({field:fieldName,direction:'asc'})}
    }
    requestedSorts=sorts
}
request.setSorts(toSortList(requestedSorts))

if (body != null && !blank(body.groupByColumn)) {
    // 多类别码表按真实数据源全量分组，不能从分页预览样例推导类别。
    return { groupedValues: metadataDataQueryService.queryGroupedValues(request, textOf(body.groupByColumn), textOf(body.groupNameColumn)) }
}

// 返回当前业务分支的标准结果。
var result=metadataDataQueryService.queryPage(request)
return {rows:result.getRows(),columns:result.getColumns(),columnTypes:result.getColumnTypes(),columnLabels:columnLabels,total:result.getTotal(),pageNo:result.getPageNo(),pageSize:result.getPageSize()}
