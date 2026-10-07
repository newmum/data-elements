{
  "properties": {},
  "id": "a9dc328184b7410781b389f67fad1233",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "07.获取字段列表",
  "createTime": 1782955344294,
  "updateTime": 1790385855266,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/columns",
  "method": "GET",
  "parameters": [
    {
      "name": "tid",
      "value": "",
      "description": "已登记数据表ID，可选",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "dbId",
      "value": "372b36d08059473eb4e248fd06276a41",
      "description": "数据源ID，首次探查时与 tableName 一起传入",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tableName",
      "value": "police_person_info",
      "description": "物理表名，首次探查时与 dbId 一起传入",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "snapshotOnly",
      "value": "false",
      "description": "true 时仅从 db_table_column_t 读取，空快照不连接来源库",
      "required": false,
      "dataType": "Boolean",
      "type": null,
      "defaultValue": "false",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "获取字段列表（GET /dst/database/metadata/columns）。登记第三、四步传 tid + snapshotOnly=true 仅读 db_table_column_t；其他探查场景保留实时来源库兼容。只读接口。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.获取字段列表
// 路由：GET /dst/database/metadata/columns
// 业务说明：获取字段列表（GET /dst/database/metadata/columns）。登记第三、四步传 tid + snapshotOnly=true 仅读 db_table_column_t；其他探查场景保留实时来源库兼容。只读接口。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import jsons
import java.util.Map
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService

/*
 * 元数据管理 07：读取数据表字段结构。
 *
 * 已登记表优先读取 db_table_column_t 快照；第二步首次探查尚未生成表 ID 时，
 * 支持使用 dbId + tableName 直连物理数据库实时读取字段。全程只读，不登记表资产。
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
var excludes = ['tenantId', 'revision', 'createdBy', 'createdTime', 'updatedBy', 'updatedTime']
if (!blank(tid)) {
    if ((tid + '').contains(',')) {
        var tids = (tid + '').split(',').filter(item => !blank(item))
        var rows = db.table('db_table_column_t').excludes(excludes).where()
            .in('table_id', tids).eq('is_del', 0).select()
        return rows.group(item => item.tableId)
    }
    var snapshot = db.table('db_table_column_t').excludes(excludes).where()
        .eq('table_id', tid).eq('is_del', 0).select()
    if (snapshot != null && snapshot.size() > 0) { return snapshot }
    // 登记第三、四步只读 MySQL 字段快照；空快照也绝不回退物理探查。
    if (snapshotOnly == true || textOf(snapshotOnly) == '1' || textOf(snapshotOnly) == 'true') { return [] }
    // registered table asset fallback: old dirty snapshots may have no columns.
    var tableAsset = db.table('db_table_t').where().eq('tid', tid).eq('is_del', 0).selectOne()
    if (tableAsset != null) {
        dbId = firstValue(tableAsset, ['datasourceId', 'datasource_id'])
        tableName = firstValue(tableAsset, ['tableName', 'table_name'])
    }
}

// 首次进入第二步的物理表没有 tid，使用数据源和表名实时读取字段结构。
if (blank(dbId) || blank(tableName)) {
    exit 400, 'tid 为空时必须提供 dbId 和 tableName'
}
var datasource = loadDatasource(dbId)
if (datasource == null) { exit 404, '数据源不存在' }
return metadataExplorerService.getColumns(dataSourceOf(datasource), tableName + '')
