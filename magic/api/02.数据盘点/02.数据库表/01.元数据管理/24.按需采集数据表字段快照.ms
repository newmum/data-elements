{
  "properties": {},
  "id": "metadata_columns_collect_24",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "24.按需采集数据表字段快照",
  "createTime": 1790386199138,
  "updateTime": 1790388386216,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/collectColumns",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tableId\":\"数据表ID\",\"force\":false,\"dryRun\":false}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "登记第三、四步按表采集/同步字段（POST /dst/database/metadata/collectColumns）。默认已有 db_table_column_t 快照直接返回；force=true 时重新探查当前物理表，按字段名保留已有 ID 和治理设置，更新结构、新增字段，并将来源已移除字段软删除。数据行预览由独立接口实时查询。 支持 dryRun=true 仅探查并返回字段差异，不写入；涉及 db_datasource_t、db_table_t、db_table_column_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：24.按需采集数据表字段快照
// 路由：POST /dst/database/metadata/collectColumns
// 业务说明：登记第三、四步按表采集/同步字段（POST /dst/database/metadata/collectColumns）。默认已有 db_table_column_t 快照直接返回；force=true 时重新探查当前物理表，按字段名保留已有 ID 和治理设置，更新结构、新增字段，并将来源已移除字段软删除。数据行预览由独立接口实时查询。 支持 dryRun=true 仅探查并返回字段差异，不写入；涉及 db_datasource_t、db_table_t、db_table_column_t 等业务表。
import tenantRuntime
import hwhive
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredSourceProbeService
import metadataAsset
import cn.hutool.core.util.IdUtil
import java.util.Date
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import java.util.Map
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService

/*
 * 元数据管理 24：登记第三、四步按当前表采集或同步字段。
 * 读取 db_table_t/db_datasource_t；有快照时默认只读 db_table_column_t。
 * 缺失快照或 force=true 时探查当前物理表，写入/更新字段快照；
 * dryRun=true 只返回差异，不修改 MySQL 或物理数据源。数据行预览由独立接口负责。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) { return value }
    }
    return null
}
var firstValue = (row, keys) => {
    for (key in keys) {
        var value = rowValue(row, [key])
        if (!blank(value)) { return value }
    }
    return null
}
var databaseTypeOf = (code) => {
    if (blank(code)) { return DatabaseType.MYSQL }
    try {
        return DatabaseType.fromCode(textOf(code).toLowerCase())
    } catch (e) {
        return DatabaseType.MYSQL
    }
}
var loadDatasource = (datasourceId) => {
    if (blank(datasourceId)) { return null }
    var row = db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).selectOne()
    if (row == null) { return null }
    var result = {
        tid: rowValue(row, ['tid']),
        dbName: rowValue(row, ['dbName', 'db_name']),
        dbType: rowValue(row, ['dbType', 'db_type']),
        jdbcURL: rowValue(row, ['jdbcUrl', 'jdbc_url']),
        username: rowValue(row, ['username']),
        password: rowValue(row, ['password'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(row, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) { result.putAll(connectionConfig) }
    if (!blank(rowValue(row, ['dbType', 'db_type']))) { result.dbType = rowValue(row, ['dbType', 'db_type']) }
    if (rowValue(row, ['jdbcUrl', 'jdbc_url']) != null) { result.jdbcURL = rowValue(row, ['jdbcUrl', 'jdbc_url']) }
    if (rowValue(row, ['username']) != null) { result.username = rowValue(row, ['username']) }
    if (rowValue(row, ['password']) != null) { result.password = rowValue(row, ['password']) }
    return result
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

/* 登记第三、四步按表按需采集字段。已有 db_table_column_t 快照时不接触来源库；预览数据另走实时接口。 */
var requestedTableId = body == null ? null : body.tableId
if (blank(requestedTableId)) { exit 400, "tableId 不能为空" }
var tableAsset = db.table("db_table_t").where().eq("tid", requestedTableId).eq("is_del", 0).selectOne()
if (tableAsset == null) { exit 404, "数据表不存在或无访问权限" }
var snapshot = db.table("db_table_column_t").where().eq("table_id", requestedTableId).eq("is_del", 0).select()
var force = body != null && (body.force == true || textOf(body.force) == 'true')
var dryRun = body != null && (body.dryRun == true || textOf(body.dryRun) == 'true')
if (!force && !dryRun && snapshot != null && snapshot.size() > 0) { return snapshot }
var datasourceId = firstValue(tableAsset, ["datasourceId", "datasource_id"])
var physicalName = firstValue(tableAsset, ["tableName", "table_name"])
if (blank(datasourceId) || blank(physicalName)) { exit 400, "数据表缺少来源数据源或物理表名" }
var datasource = loadDatasource(datasourceId)
if (datasource == null) { exit 404, "数据源不存在或无访问权限" }
var sourceType = textOf(firstValue(datasource, ["dbType", "db_type"])).toLowerCase()
var structured = sourceType == "ftp" || sourceType == "api" || sourceType == "kafka"
var managedHive = sourceType == "hive" && (
    textOf(firstValue(datasource, ["metadataAccessMode", "metadata_access_mode"])) == "server-managed-mrs"
    || !blank(firstValue(datasource, ["hiveProfile", "hive_profile"]))
)
var physicalColumns = []
if (managedHive) {
    var hiveDatabase = firstValue(datasource, ["database", "dbName", "db_name"])
    if (blank(hiveDatabase) || !(hiveDatabase + "").matches("[A-Za-z_][A-Za-z0-9_]*")
        || !(physicalName + "").matches("[A-Za-z_][A-Za-z0-9_]*")) {
        exit 400, "Hive 库名或表名格式不正确"
    }
    var hiveRows = hwhive.queryTable("DESCRIBE `" + hiveDatabase + "`.`" + physicalName + "`")
    for (hiveRow in hiveRows == null ? [] : hiveRows) {
        var hiveName = firstValue(hiveRow, ["col_name", "COL_NAME", "colName", "column_name"])
        if (blank(hiveName) || (hiveName + "").startsWith("#")) { continue }
        var hiveType = firstValue(hiveRow, ["data_type", "DATA_TYPE", "dataType", "type"])
        physicalColumns.add({ columnName: hiveName, columnComment: firstValue(hiveRow, ["comment", "COMMENT"]),
            dataType: hiveType, columnType: hiveType, ordinalPosition: physicalColumns.size() + 1 })
    }
} else if (structured) {
    physicalColumns = structuredSourceProbeService.getColumns(datasource, physicalName + "")
} else {
    physicalColumns = metadataExplorerService.getColumns(dataSourceOf(datasource), physicalName + "")
}
if (physicalColumns == null || physicalColumns.isEmpty()) { exit 422, "来源表未返回字段，请检查表名和访问权限" }
var now = new Date()
var rows = []
for (index, column in physicalColumns) {
    if (blank(column.columnName)) { continue }
    rows.add({
        tid: IdUtil.getSnowflakeNextIdStr(), table_id: requestedTableId, tenant_id: tenantRuntime.id(),
        column_name: column.columnName, column_comment: column.columnComment,
        data_type: column.dataType, column_type: column.columnType,
        length: column.length, precision_length: column.precision, scale: column.scale,
        nullable: column.nullable, default_value: column.defaultValue,
        primary_key: column.primaryKey, auto_increment: column.autoIncrement,
        is_unique: column.unique, indexed: column.indexed,
        ordinal_position: column.ordinalPosition == null ? index + 1 : column.ordinalPosition,
        charset: column.charset, collation: column.collation, extra: column.extra,
        is_del: 0, created_time: now, updated_time: now
    })
}
if (rows.isEmpty()) { exit 422, "来源表字段名称为空" }
var previousByName = {}
for (previous in snapshot == null ? [] : snapshot) {
    if (!blank(previous.columnName)) { previousByName[textOf(previous.columnName).toLowerCase()] = previous }
}
if (dryRun) {
    var incoming = {}
    var addedCount = 0
    for (column in rows) {
        var key = textOf(column.column_name).toLowerCase()
        incoming[key] = true
        if (previousByName[key] == null) { addedCount = addedCount + 1 }
    }
    var removedCount = 0
    for (previous in snapshot == null ? [] : snapshot) {
        if (!blank(previous.columnName) && incoming[textOf(previous.columnName).toLowerCase()] != true) { removedCount = removedCount + 1 }
    }
    return { dryRun: true, tableId: requestedTableId, sourceFieldCount: rows.size(),
        snapshotFieldCount: snapshot == null ? 0 : snapshot.size(), added: addedCount, removed: removedCount }
}
var retained = {}
var additions = []
for (row in rows) {
    var key = textOf(row.column_name).toLowerCase()
    var previous = previousByName[key]
    if (previous == null) {
        additions.add(row)
        continue
    }
    retained[key] = true
    db.table("db_table_column_t").where()
        .eq("tid", previous.tid).eq("table_id", requestedTableId).eq("is_del", 0)
        .update({
            column_comment: row.column_comment, data_type: row.data_type, column_type: row.column_type,
            length: row.length, precision_length: row.precision_length, scale: row.scale,
            nullable: row.nullable, default_value: row.default_value,
            auto_increment: row.auto_increment, is_unique: row.is_unique, indexed: row.indexed,
            ordinal_position: row.ordinal_position, charset: row.charset,
            collation: row.collation, extra: row.extra, updated_time: now
        })
}
if (!additions.isEmpty()) { metadataAsset.batchInsertTableColumns(additions) }
// Remove stale fields only after every current field has been updated or inserted.
for (previous in snapshot == null ? [] : snapshot) {
    if (blank(previous.columnName) || retained[textOf(previous.columnName).toLowerCase()] == true) { continue }
    db.table("db_table_column_t").where()
        .eq("tid", previous.tid).eq("table_id", requestedTableId).eq("is_del", 0)
        .update({ is_del: 1, updated_time: now })
}
db.table("db_table_t").where().eq("tid", requestedTableId).eq("is_del", 0)
    .update({ field_count: rows.size(), updated_time: now })
return db.table("db_table_column_t").where().eq("table_id", requestedTableId).eq("is_del", 0).select()
