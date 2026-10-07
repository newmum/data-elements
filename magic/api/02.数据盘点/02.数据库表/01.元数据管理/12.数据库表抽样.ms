{
  "properties": {},
  "id": "06ee6f0e37e44762be967dbd2d764780",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "12.数据库表抽样",
  "createTime": 1782955344245,
  "updateTime": 1787156076883,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/table/sample-data",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"可选，已登记表ID\",\n    \"dbId\": \"372b36d08059473eb4e248fd06276a41\",\n    \"tableName\": \"police_person_info\",\n    \"sampleSize\": 10\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据库表抽样（POST /dst/database/metadata/table/sample-data）。已登记表可传 tableId；第二步首次探查可传 dbId + tableName，直接读取物理表前 N 行。只读，不登记表资产；涉及 db_datasource_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.数据库表抽样
// 路由：POST /dst/database/metadata/table/sample-data
// 业务说明：数据库表抽样（POST /dst/database/metadata/table/sample-data）。已登记表可传 tableId；第二步首次探查可传 dbId + tableName，直接读取物理表前 N 行。只读，不登记表资产；涉及 db_datasource_t、db_table_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.model.dto.SampleRequest
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map

/*
 * 元数据管理 12：数据库表抽样。
 *
 * 只读接口。根据 tableId 找到表资产和所属数据库资产，组装 SampleRequest 后调用底层元数据抽样服务。
 * 与 13 的区别：12 返回抽样模型，适合“前 N 条/随机抽样”等场景；13 返回分页模型，适合弹框预览。
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
    var main = db.table('db_datasource_t').where().eq('tid', assetId).eq('is_del', 0).selectOne()
    var assetType = 'db'
    if (main == null) {
        // 从 MySQL 业务表读取当前有效数据。
        main = db.table('db_table_t').where().eq('tid', assetId).eq('is_del', 0).selectOne()
        assetType = 'table'
    }
    if (main == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: rowValue(main, ['tid']), id: rowValue(main, ['tid']), assetType: assetType,
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
// 物化目标表仅允许一个 _TARGET 后缀。历史上重复生成的登记记录可能带有
// _TARGET_TARGET，抽样必须回落到真实物理表名，避免对业务库发出无效 SQL。
var normalizePhysicalTableName = (value) => {
    var name = textOf(value)
    if (blank(name)) {
        return name
    }
    while (name.toUpperCase().endsWith('_TARGET_TARGET')) {
        name = name.substring(0, name.length() - '_TARGET'.length())
    }
    return name
}
var dataSourceOf = (asset) => {
    var dbType = databaseTypeOf(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType']))
    var typeCode = textOf(dbType.getCode()).toLowerCase()
    var virtualResource = !dbType.isRelational()
    var zookeeperQuorum = firstValue(asset, ['zookeeperQuorum', 'zkQuorum'])
    var hiveZooKeeperDiscovery = dbType == DatabaseType.HIVE && !blank(zookeeperQuorum)
    var host = firstValue(asset, ['host', 'dbIp', 'minioEndpoint', 'apiUrl', 'url', 'nodes', 'ftpHost', 'hostname', 'bootstrapServers', 'kafkaBootstrapServers', 'maxcomputeEndpoint'])
    var port = firstValue(asset, ['port', 'dbPort', 'ftpPort'])
    var databaseName = firstValue(asset, ['database', 'dbName', 'dbMetaDbName', 'schema', 'defaultSchema', 'currentSchema', 'maxcomputeProject', 'minioBucket', 'bucket', 'topic', 'kafkaTopic', 'indexName', 'apiName', 'assetName', 'name'])
    var username = firstValue(asset, ['username', 'dbUser', 'dbMetaUser', 'user', 'maxcomputeAccessKeyId', 'minioAccessKey', 'ftpUsername', 'apiUser'])
    var password = firstValue(asset, ['password', 'dbPassword', 'dbMetaPassword', 'maxcomputeAccessKeySecret', 'minioSecretKey', 'ftpPassword', 'apiToken'])
    if (!virtualResource && (blank(databaseName) || (!hiveZooKeeperDiscovery && (blank(host) || blank(username))))) {
        exit 400, '数据源连接参数不完整'
    }
    var dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(dbType)
    dsConfig.setHost(blank(host) ? (hiveZooKeeperDiscovery ? '' : 'virtual') : textOf(host))
    dsConfig.setPort(blank(port) ? dbType.getDefaultPort() : port::int)
    dsConfig.setDatabase(blank(databaseName) ? typeCode + '_resource' : textOf(databaseName))
    dsConfig.setUsername(blank(username) ? '' : textOf(username))
    dsConfig.setPassword(blank(password) ? '' : textOf(password))
    // Oracle SID and Service Name use different URL formats; reuse the tested JDBC URL.
    dsConfig.setJdbcUrl(blank(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])) ? null : textOf(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url'])))
    dsConfig.setSchema(blank(firstValue(asset, ['schema', 'defaultSchema', 'currentSchema'])) ? null : textOf(firstValue(asset, ['schema', 'defaultSchema', 'currentSchema'])))
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
    dsConfig.setExtraParams(blank(firstValue(asset, ['extraParams', 'connectParams', 'jdbcParams', 'maxcomputeEndpoint'])) ? null : textOf(firstValue(asset, ['extraParams', 'connectParams', 'jdbcParams', 'maxcomputeEndpoint'])))
    dsConfig.setConnectionTimeout(30)
    return dsConfig
}
// 解析请求体并为可选参数设置默认值。
var tableId = body == null ? null : body.tableId
var directDbId = body == null ? null : body.dbId
var directTableName = body == null ? null : body.tableName
var dbAsset = null
var resolvedTableName = null
if (!blank(tableId)) {
    var table = loadAsset(tableId)
    if (table == null) { exit 404, 'tableId 参数不正确' }
    dbAsset = loadAsset(firstValue(table, ['dbId']))
    resolvedTableName = firstValue(table, ['tableName', 'tableNameEn'])
} else {
    // 第二步首次进入时表尚未登记，直接使用 dbId + tableName 读取物理表样例。
    if (blank(directDbId) || blank(directTableName)) {
        exit 400, 'tableId 为空时必须提供 dbId 和 tableName'
    }
    dbAsset = loadAsset(directDbId)
    resolvedTableName = directTableName
}
if (dbAsset == null) { exit 404, '表所属数据源不存在' }

resolvedTableName = normalizePhysicalTableName(resolvedTableName)
var request = new SampleRequest()
request.setDataSource(dataSourceOf(dbAsset))
request.setTableName(resolvedTableName + '')
request.setSampleSize(body == null || body.sampleSize == null ? 20 : body.sampleSize::int)
request.setSampleMethod(SampleRequest.SampleMethod.FIRST)
if (body != null && body.columns != null) {
    request.setColumns(body.columns)
}
if (body != null && body.whereClause != null) {
    request.setWhereClause(body.whereClause)
}

// 返回当前业务分支的标准结果。
return metadataExplorerService.sampleData(request)
