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
if (table == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableId不正确'
}
var dbAsset = loadAsset(firstValue(table, ['dbId']))
if (dbAsset == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '表所属数据源不存在'
}

if(dataScope.visibleDataSourcesFor('MENU:2081000000000000002',[dbAsset]).size()!=1){exit 403,'表所属数据源不在当前角色数据范围内';}
var request = new PageQueryRequest()
request.setDataSource(dataSourceOf(dbAsset))
request.setTableName(firstValue(table, ['tableName', 'tableNameEn']))
request.setPageNo(body == null || body.pageNo == null ? 1 : body.pageNo::int)
request.setPageSize(body == null || body.pageSize == null ? 20 : body.pageSize::int)
if (body != null && body.columns != null) {
    request.setColumns(body.columns)
}
request.setConditions(toConditionList(body == null ? null : body.conditions))
var requestedSorts=body?.sorts==null?[]:body.sorts
if(!blank(body?.sortField)){requestedSorts=[{field:body.sortField,direction:tableSort.direction(body)}]}
if(requestedSorts.size()>10){exit 400,'最多选择10个排序字段';}
if(requestedSorts.size()>0){
    var fieldRows=db.table('db_table_column_t').where().eq('tenant_id',tenantId).eq('table_id',tableId).eq('is_del',0).page(1001,0)
    if(fieldRows.total>1000){exit 413,'字段超过1000项，不能使用此预览排序';}
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
if(request.getPageNo()<1||request.getPageNo()>100000||request.getPageSize()<1||request.getPageSize()>100){exit 400,'数据预览分页参数无效';}
request.setSorts(toSortList(requestedSorts))

if (body != null && !blank(body.groupByColumn)) {
    // 多类别码表按真实数据源全量分组，不能从分页预览样例推导类别。
    return { groupedValues: metadataDataQueryService.queryGroupedValues(request, textOf(body.groupByColumn), textOf(body.groupNameColumn)) }
}

// 返回当前业务分支的标准结果。
return metadataDataQueryService.queryPage(request)
