{
  "properties": {},
  "id": "8a56e7d664934c1499102cfa508cd58a",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "14.获取ddl建表语句",
  "createTime": 1782955344281,
  "updateTime": 1790118995803,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getCreateTableDDL",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"propList\": {\n        \"dbType\": \"MYSQL\",\n        \"tableNameCn\": \"字典表aaa\",\n        \"tableName\": \"sys_file_aaa\",\n        \"dbId\": \"8940a21f79c34bcf9a165a6b0ee8dc08\"\n    },\n    \"tableItems\": [\n        {\n            \"columnName\": \"id\",\n            \"columnComment\": \"主键\",\n            \"dataType\": \"varchar\",\n            \"columnType\": \"varchar(36)\",\n            \"length\": 36,\n            \"precisionLength\": 0,\n            \"scale\": 0,\n            \"nullable\": 0,\n            \"primaryKey\": 1,\n            \"autoIncrement\": 0,\n            \"isUnique\": 1,\n            \"indexed\": 1,\n            \"ordinalPosition\": 1,\n            \"charset\": \"utf8mb4\",\n            \"collation\": \"utf8mb4_general_ci\"\n        },\n        {\n            \"columnName\": \"id1\",\n            \"columnComment\": \"主键\",\n            \"dataType\": \"int\",\n            \"columnType\": \"int(0)\",\n            \"length\": 0,\n            \"precisionLength\": 0,\n            \"scale\": 0,\n            \"nullable\": 0,\n            \"primaryKey\": 1,\n            \"autoIncrement\": 0,\n            \"isUnique\": 0,\n            \"indexed\": 0,\n            \"ordinalPosition\": 3,\n            \"charset\": \"utf8mb4\",\n            \"collation\": \"utf8mb4_general_ci\"\n        },\n        {\n            \"columnName\": \"desc_ribe\",\n            \"columnComment\": \"描述\",\n            \"dataType\": \"varchar\",\n            \"columnType\": \"varchar(100)\",\n            \"length\": 100,\n            \"precisionLength\": 0,\n            \"scale\": 0,\n            \"nullable\": 1,\n            \"defaultValue\": \"356\",\n            \"primaryKey\": 0,\n            \"autoIncrement\": 0,\n            \"isUnique\": 0,\n            \"indexed\": 0,\n            \"ordinalPosition\": 2,\n            \"charset\": \"utf8mb4\",\n            \"collation\": \"utf8mb4_general_ci\"\n        }\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "元数据管理 14：生成建表 DDL；使用请求参数 propList、tableItems、canvas、sourceDatabaseType；涉及 db_datasource_t 等业务表；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：14.获取ddl建表语句
// 路由：POST /dst/database/metadata/getCreateTableDDL
// 业务说明：元数据管理 14：生成建表 DDL；使用请求参数 propList、tableItems、canvas、sourceDatabaseType；涉及 db_datasource_t 等业务表；读取或计算后返回业务结果。

import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import tenantRuntime
import canvasTableDdl
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.ColumnInfo
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map

/*
 * 元数据管理 14：生成建表 DDL。
 *
 * 只读接口。根据 propList.tableName/tableNameCn 和 tableItems 字段定义生成目标库方言的 CREATE TABLE。
 * 传 dbId 时按目标数据库类型生成；不传 dbId 时使用默认 MySQL 配置，仅用于模板预览。
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
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录状态已失效，请重新登录后再生成建表脚本'
}

var loadAsset = (assetId) => {
    if (blank(assetId)) {
        // 返回当前业务分支的标准结果。
        return null
    }
    // 从 MySQL 业务表读取当前有效数据。
    var main = db.table('db_datasource_t').where().eq('tid', assetId).eq('is_del', 0).eq('tenant_id', tenantId).selectOne()
    if (main == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: rowValue(main, ['tid']), id: rowValue(main, ['tid']), assetType: 'db',
        dbName: rowValue(main, ['dbName', 'db_name']),
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
    var host = firstValue(asset, ['host', 'dbIp'])
    var port = firstValue(asset, ['port', 'dbPort'])
    var databaseName = firstValue(asset, ['database', 'dbName', 'dbMetaDbName'])
    var username = firstValue(asset, ['username', 'dbUser', 'dbMetaUser', 'user'])
    var password = firstValue(asset, ['password', 'dbPassword', 'dbMetaPassword'])
    var databaseType = databaseTypeOf(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType']))
    var zookeeperQuorum = firstValue(asset, ['zookeeperQuorum', 'zkQuorum'])
    var hiveZooKeeperDiscovery = databaseType == DatabaseType.HIVE && !blank(zookeeperQuorum)
    var schema = firstValue(asset, ['schema', 'defaultSchema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
    if (databaseType == DatabaseType.DAMENG) {
        // 旧达梦数据源可能未单独存储 Schema。达梦业务账号的默认 Schema 即用户名，
        // 因此在未显式配置时安全回退到该账号；系统账号仍会在下方被明确拦截。
        if (blank(schema)) {
            schema = username
        }
        if (blank(schema)) {
            exit 400, '达梦目标数据源未配置业务模式（Schema）；请在数据源连接信息中填写可建表的业务 Schema 后重试'
        }
        var normalizedSchema = textOf(schema).trim().toUpperCase()
        var damengSystemSchemas = ['SYS', 'SYSTEM', 'SYSDBA', 'SYSAUDITOR', 'SYSSSO', 'SYSJOB', 'SYSDATE', 'SYSCONSOLE', 'SYSDBO', 'CTISYS', 'DBA', 'INFORMATION_SCHEMA']
        if (damengSystemSchemas.indexOf(normalizedSchema) >= 0) {
            exit 400, '达梦目标数据源的默认模式 ' + normalizedSchema + ' 是系统内部模式；请改为业务 Schema 后重试'
        }
    }
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
    dsConfig.setSchema(schema)
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

// 解析请求体并为可选参数设置默认值。
var propList = body == null ? null : body.propList
var tableItems = body == null ? null : body.tableItems
if (tableItems == null || tableItems.size() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '字段列表不能为空'
}
if (propList == null || blank(propList.tableName)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableName不能为空'
}

var dsConfig
if (body.canvas == true) {
    if (blank(propList.dbId)) { exit 400, '请选择已登记的目标库' }
    var registered = connectionProperties.resolve(tenantRuntime.id(), textOf(propList.dbId))
    if (registered == null || registered.isEmpty()) { exit 404, '当前租户中不存在所选目标数据源' }
    // DDL generation only needs target dialect/scope; managed Hive credentials stay on the server.
    dsConfig = new DataSourceConfig()
    var targetType = DatabaseType.fromCode(textOf(registered.dbType).toLowerCase())
    if (targetType == null) { exit 400, '目标数据源未配置数据库类型' }
    dsConfig.setDatabaseType(targetType)
    dsConfig.setDatabase(firstValue(registered, ['database', 'dbName', 'dbMetaDbName']))
    dsConfig.setUsername(firstValue(registered, ['username', 'user']))
    dsConfig.setSchema(firstValue(registered, ['schema', 'defaultSchema', 'currentSchema', 'schemaName']))
} else if (propList != null && !blank(propList.dbId)) {
    var dbAsset = loadAsset(propList.dbId)
    if (dbAsset == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, 'dbId参数不正确'
    }
    // DDL 预览只依赖目标库方言和可选的库/模式名，并不会建立 JDBC 连接。
    // 因此不能复用 dataSourceOf 的“主机、账号、连接参数完整”校验：
    // 该校验属于实际建表/连通性测试，若用于预览会把本可生成的 DDL 错误地拦截。
    dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(databaseTypeOf(firstValue(dbAsset, ['dbType', 'databaseType', 'dataSourceType'])))
    dsConfig.setDatabase(firstValue(dbAsset, ['database', 'dbName', 'dbMetaDbName']))
    dsConfig.setUsername(firstValue(dbAsset, ['username', 'dbUser', 'user']))
    dsConfig.setSchema(firstValue(dbAsset, ['schema', 'defaultSchema', 'currentSchema', 'schemaName', 'owner', 'dbSchema']))
} else {
    dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(databaseTypeOf(propList == null ? null : propList.dbType))
    dsConfig.setHost('localhost')
    dsConfig.setPort(3306)
    dsConfig.setDatabase('test')
    dsConfig.setUsername('root')
    dsConfig.setPassword('123456')
}

// Final DDL guard for the protected ODS ingestion timestamps.  Callers may send
// stale/editable table rows, so derive their physical type from the resolved
// target datasource instead of trusting the request's generic `datetime` value.
var materializationTimeType = (databaseType) => {
    var code = databaseType == null ? '' : textOf(databaseType.getCode()).trim().toLowerCase()
    if (code.indexOf('oceanbase') >= 0 || code == 'oracle' || code == 'dameng') {
        return 'DATE'
    }
    if (code == 'postgresql' || code == 'kingbase' || code == 'gaussdb'
        || code == 'vertica' || code == 'hetu' || code == 'hive') {
        return 'TIMESTAMP'
    }
    if (code == 'sqlserver') {
        return 'DATETIME2'
    }
    return 'DATETIME'
}
var columns = []
for (item in tableItems) {
    var columnInfo = new ColumnInfo()
    columnInfo.setSourceDatabaseType(body.sourceDatabaseType == null ? null : textOf(body.sourceDatabaseType))
    var columnName = firstValue(item, ['columnName', 'name'])
    var normalizedColumnName = blank(columnName) ? '' : textOf(columnName).trim().toUpperCase()
    var dataType = firstValue(item, ['dataType', 'type'])
    var columnType = firstValue(item, ['columnType'])
    if (normalizedColumnName == 'ODS_RKSJ' || normalizedColumnName == 'ODS_GXSJ') {
        dataType = materializationTimeType(dsConfig.getDatabaseType())
        columnType = dataType
    }
    columnInfo.setColumnName(columnName)
    columnInfo.setColumnComment(firstValue(item, ['columnComment', 'comment']))
    columnInfo.setDataType(dataType)
    columnInfo.setColumnType(columnType)
    if (rowValue(item, ['length']) != null) {
        columnInfo.setLength(rowValue(item, ['length'])::long)
    }
    if (rowValue(item, ['precisionLength']) != null) {
        columnInfo.setPrecision(rowValue(item, ['precisionLength'])::int)
    }
    if (rowValue(item, ['scale']) != null) {
        columnInfo.setScale(rowValue(item, ['scale'])::int)
    }
    columnInfo.setNullable(rowValue(item, ['nullable']) == 1 || rowValue(item, ['nullable']) == true)
    columnInfo.setDefaultValue(rowValue(item, ['defaultValue']))
    columnInfo.setPrimaryKey(rowValue(item, ['primaryKey']) == 1 || rowValue(item, ['primaryKey']) == true)
    columnInfo.setAutoIncrement(rowValue(item, ['autoIncrement']) == 1 || rowValue(item, ['autoIncrement']) == true)
    columnInfo.setUnique(rowValue(item, ['isUnique', 'unique']) == 1 || rowValue(item, ['isUnique', 'unique']) == true)
    columnInfo.setIndexed(rowValue(item, ['indexed']) == 1 || rowValue(item, ['indexed']) == true)
    if (rowValue(item, ['ordinalPosition']) != null) {
        columnInfo.setOrdinalPosition(rowValue(item, ['ordinalPosition'])::int)
    }
    columnInfo.setCharset(rowValue(item, ['charset']))
    columnInfo.setCollation(rowValue(item, ['collation']))
    columnInfo.setExtra(rowValue(item, ['extra']))
    columns.add(columnInfo)
}

// 返回当前业务分支的标准结果。
try {
    var ddl = metadataExplorerService.generateCreateTableDdl(dsConfig, propList.tableName, columns, propList.tableNameCn)
    if (body.canvas == true) {
        // Use the same single-table and target-scope validation as physical creation.
        var parsed = canvasTableDdl.parse(ddl, dsConfig)
        return parsed.ddl
    }
    return ddl
} catch (e) {
    exit 400, '生成建表语句失败：' + e.getMessage()
}
