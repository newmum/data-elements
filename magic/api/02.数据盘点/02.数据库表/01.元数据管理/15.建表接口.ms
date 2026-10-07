{
  "properties": {},
  "id": "a0b5bba38fbf428c8e3646864d83d56b",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "15.建表接口",
  "createTime": 1782955344211,
  "updateTime": 1790843736559,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/createTable",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"ddl\": \"CREATE TABLE sys_gen_info_traget (\\n  id int(0) NOT NULL AUTO_INCREMENT COMMENT '序号',\\n  name varchar(50) NOT NULL COMMENT '姓名',\\n  nationality varchar(50) NOT NULL COMMENT '国籍',\\n  id_number varchar(50) NOT NULL COMMENT '证件号码',\\n  entry_date date(0) NOT NULL COMMENT '入境日期',\\n  entry_port varchar(100) NOT NULL COMMENT '入境口岸',\\n  current_location varchar(100) NOT NULL COMMENT '当前位置',\\n  in_border_status varchar(20) NOT NULL COMMENT '在境状态',\\n  risk_level varchar(20) NOT NULL COMMENT '风险等级',\\n  last_activity_time datetime(0) NOT NULL COMMENT '最后活动时间',\\n  is_del tinyint(0) NOT NULL DEFAULT 0 COMMENT '删除标记（0-未删除，1-已删除）',\\n  create_time datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\\n  update_time datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',\\n  is_deleted int(1) NOT NULL AUTO_INCREMENT COMMENT '删除标志',\\n  PRIMARY KEY (id)\\n) COMMENT='生成表信息';\\n\",\n    \"dbId\": \"62d25cc8bf1344d6b9f41fed23af4eaf\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "建表接口（POST /dst/database/metadata/createTable）。写接口；根据 dbId 连接已登记的目标数据源并执行 DDL。默认严格执行；物化业务可传 tableName 与 skipIfExists=true，在同名物理表已存在时返回复用结果而不重复执行 DDL。 画布可传 canvas=true，按当前租户解析目标库，校验单表 DDL，返回 created/exists 状态；托管 Hive 使用服务端 hwhive；涉及 db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：15.建表接口
// 路由：POST /dst/database/metadata/createTable
// 业务说明：建表接口（POST /dst/database/metadata/createTable）。写接口；根据 dbId 连接已登记的目标数据源并执行 DDL。默认严格执行；物化业务可传 tableName 与 skipIfExists=true，在同名物理表已存在时返回复用结果而不重复执行 DDL。 画布可传 canvas=true，按当前租户解析目标库，校验单表 DDL，返回 created/exists 状态；托管 Hive 使用服务端 hwhive；涉及 db_datasource_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map
import tenantRuntime
import canvasTableDdl
import hwhive
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver

/*
 * 元数据管理 15：执行建表 DDL。
 *
 * 写接口。根据 dbId 找到目标数据库连接，然后执行 body.ddl。
 * 本接口只负责物理建表，不自动登记资产；建表后若要资产快照，请调用 08 或页面登记流程。
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
    dsConfig.setJdbcProperties(JdbcDriverPropertyResolver.resolve(asset))
    // 返回当前业务分支的标准结果。
    return dsConfig
}

// 解析请求体并为可选参数设置默认值。
var dbId = body == null ? null : body.dbId
var ddl = body == null ? null : body.ddl
var tableName = body == null ? null : body.tableName
var skipIfExists = body != null && body.skipIfExists == true
if (blank(dbId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'dbId参数不正确'
}
if (blank(ddl)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'ddl不能为空'
}

// Canvas uses the same physical creation capability as materialization. Resolve
// its selected target exclusively inside the active tenant, then validate DDL.
if (body.canvas == true) {
    var tenantId = tenantRuntime.id()
    var registered = connectionProperties.resolve(tenantId, textOf(dbId))
    if (registered == null || registered.isEmpty()) { exit 404, '当前租户中不存在所选目标数据源' }
    var managedHive = textOf(registered.dbType).toLowerCase() == 'hive'
        && (registered.metadataAccessMode == 'server-managed-mrs' || registered.hiveConnectionMode == 'huawei-mrs'
            || !blank(registered.hiveProfile))
    var config = null
    if (managedHive) {
        config = new DataSourceConfig()
        config.setDatabaseType(DatabaseType.HIVE)
        config.setDatabase(firstValue(registered, ['database', 'dbName', 'dbMetaDbName']))
    } else {
        config = dataSourceOf(registered)
    }
    var parsed = null
    try { parsed = canvasTableDdl.parse(textOf(ddl), config) }
    catch (e) { exit 400, '建表语句校验失败：' + e.getMessage() }
    if (!blank(tableName) && !canvasTableDdl.matches(parsed, textOf(tableName).trim(), parsed.schemaName)) {
        exit 400, '建表语句中的目标表名与当前填写的目标表名不一致'
    }
    if (body.validateOnly == true) {
        return { valid: true, tableName: parsed.tableName, targetScope: parsed.schemaName, ddl: parsed.ddl, sideEffectsApplied: false }
    }
    config.setSchema(parsed.schemaName)
    if (managedHive) {
        for (candidate in hwhive.showTables(config.getDatabase())) {
            if (canvasTableDdl.matches(parsed, candidate, config.getDatabase())) {
                return { status: 'exists', created: false, tableName: parsed.tableName, message: '目标表已存在，请直接选择已有表' }
            }
        }
    } else {
        var page = metadataExplorerService.getTablesPage(config, 1, 500, parsed.lookupName)
        for (candidate in (page == null || page.rows == null ? [] : page.rows)) {
            if (canvasTableDdl.matches(parsed, candidate.tableName, candidate.schemaName)) {
                return { status: 'exists', created: false, tableName: parsed.tableName, message: '目标表已存在，请直接选择已有表' }
            }
        }
    }
    try {
        if (managedHive) { hwhive.execDDLSqlInDatabase(config.getDatabase(), parsed.ddl) }
        else { metadataExplorerService.createTable(config, parsed.ddl) }
    } catch (e) {
        if (canvasTableDdl.alreadyExists(e)) {
            return { status: 'exists', created: false, tableName: parsed.tableName, message: '目标表已存在，请直接选择已有表' }
        }
        throw e
    }
    return { status: 'created', created: true, tableName: parsed.tableName, message: '物化建表成功' }
}

var dbAsset = loadAsset(dbId)
if (dbAsset == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'dbId参数不正确'
}

var targetConfig = dataSourceOf(dbAsset)
if (skipIfExists && !blank(tableName)) {
    try {
        var page = metadataExplorerService.getTablesPage(targetConfig, 1, 200, textOf(tableName).trim())
        var candidates = page == null || page.rows == null ? [] : page.rows
        for (candidate in candidates) {
            if (!blank(candidate.tableName)
                && textOf(candidate.tableName).trim().toLowerCase() == textOf(tableName).trim().toLowerCase()) {
                return { status: 'exists', created: false, tableName: candidate.tableName }
            }
        }
    } catch (ignored) {
        // Some target drivers fail metadata paging; DDL execution below still handles existing-table reuse.
    }
}

try {
    metadataExplorerService.createTable(targetConfig, ddl)
} catch (e) {
    var errorText = textOf(e == null ? '' : e.getMessage())
    if (skipIfExists && (errorText.indexOf('已存在') >= 0
        || errorText.toLowerCase().indexOf('already exists') >= 0
        || errorText.toLowerCase().indexOf('exists') >= 0)) {
        return { status: 'exists', created: false, tableName: tableName }
    }
    throw e
}
if (skipIfExists) {
    return { status: 'created', created: true, tableName: tableName }
}
// 返回当前业务分支的标准结果。
return 'success'
