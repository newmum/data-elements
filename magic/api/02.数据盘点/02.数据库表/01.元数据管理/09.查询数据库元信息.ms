{
  "properties": {},
  "id": "metadata_db_info_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "09.查询数据库元信息",
  "createTime": 1783639496542,
  "updateTime": 1787156076783,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dbInfo",
  "method": "GET",
  "parameters": [
    {
      "name": "tid",
      "value": "8b0a98de552b4bc68a203293fadfff33",
      "description": "数据库资产ID",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tableId",
      "description": "可选：当前数据源下的表资产 ID，仅查询该表概览",
      "required": false,
      "dataType": "String"
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询当前租户及角色范围内的数据源概览；传 tableId 时仅查询当前表的实时概览，不写入资产。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.查询数据库元信息
// 路由：GET /dst/database/metadata/dbInfo
// 业务说明：元数据管理 09：查询数据库概览信息；涉及 db_datasource_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import tenantRuntime
import dataScope
import hwhive
import cn.hutool.core.date.DateUtil
import jsons
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map

/*
 * 元数据管理 09：查询数据库概览信息。
 *
 * 只读接口。根据数据库资产 tid 组装 DataSourceConfig，调用底层 MetadataExplorerService
 * 读取物理数据库概览，返回容量、表数量、视图数量、存储过程数量和用户数量。
 * 这里只返回实时概览，不写资产属性；需要刷新并持久化概览请走 10。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var currentTenantId = tenantRuntime.id()
if (blank(currentTenantId)) { exit 401, '当前登录租户无效，请重新登录' }
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
    var main = db.table('db_datasource_t').where().eq('tenant_id', currentTenantId).eq('tid', assetId).eq('is_del', 0).selectOne()
    if (main == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    if (dataScope.visibleDataSourcesFor('MENU:2081000000000000002', [main]).size() != 1) {
        exit 403, '数据源不在当前角色数据范围内'
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
var countValue = (value) => value == null ? 0 : value
var sizeValue = (value) => blank(value) ? '0 B' : value

if (blank(tid)) { exit 400, 'tid 不能为空' }
var requestedTableId = tableId
var tableAsset = null
if (!blank(requestedTableId)) {
    tableAsset = db.table('db_table_t').where().eq('tenant_id', currentTenantId)
        .eq('tid', requestedTableId).eq('is_del', 0).selectOne()
    if (tableAsset == null) { exit 404, '数据表不存在或无访问权限' }
    if (textOf(firstValue(tableAsset, ['datasourceId', 'datasource_id'])) != textOf(tid)) {
        exit 403, '数据表不属于指定数据源'
    }
}
var asset = loadAsset(tid)
if (asset == null) { exit 404, '数据源资产不存在或无访问权限' }
var sourceType = textOf(firstValue(asset, ['dbType', 'databaseType', 'dataSourceType'])).toLowerCase()
var managedHive = sourceType == 'hive' && (
    textOf(firstValue(asset, ['metadataAccessMode', 'metadata_access_mode'])) == 'server-managed-mrs'
    || !blank(firstValue(asset, ['hiveProfile', 'hive_profile']))
)
var dateValue = (value) => value == null ? null : DateUtil.format(value, 'yyyy-MM-dd HH:mm:ss')
if (tableAsset != null) {
    // A single detail action probes only this table. Never load a whole database or its indexes.
    var physicalName = firstValue(tableAsset, ['tableName', 'table_name'])
    if (blank(physicalName)) { exit 400, '数据表缺少物理表名' }
    var sourceConfig = managedHive ? null : dataSourceOf(asset)
    var info = null
    if (managedHive) {
        var hiveDatabase = firstValue(asset, ['database', 'dbName', 'db_name'])
        if (!textOf(hiveDatabase).matches('[A-Za-z_][A-Za-z0-9_]*')
            || !textOf(physicalName).matches('[A-Za-z_][A-Za-z0-9_]*')) {
            exit 400, 'Hive 库名或表名格式不正确'
        }
        var qualified = '`' + hiveDatabase + '`.`' + physicalName + '`'
        var details = hwhive.queryTable('DESCRIBE FORMATTED ' + qualified)
        var fieldCount = 0
        var fieldSection = true
        var totalBytes = null
        var createTime = null
        for (row in details == null ? [] : details) {
            var key = firstValue(row, ['col_name', 'COL_NAME'])
            var value = firstValue(row, ['data_type', 'DATA_TYPE'])
            if (blank(key)) { fieldSection = false; continue }
            key = textOf(key).trim()
            if (key.startsWith('#')) { continue }
            if (fieldSection && !blank(value)) { fieldCount = fieldCount + 1 }
            if (key == 'totalSize' && textOf(value).trim().matches('[0-9]+')) { totalBytes = textOf(value).trim()::long }
            if (key == 'CreateTime:') { createTime = textOf(value).trim() }
        }
        var counted = hwhive.queryTable('SELECT COUNT(*) AS row_count FROM ' + qualified)
        info = { columnCount: fieldCount, rowCount: rowValue(counted[0], ['row_count', 'ROW_COUNT']),
            totalSizeBytes: totalBytes, totalSizeFormatted: totalBytes == null ? null : totalBytes + ' B',
            createTime: createTime, updateTime: null }
    } else {
        var probeName = physicalName + ''
        if (sourceConfig.getDatabaseType().isRelational()) {
            // Normalize an owner/schema-qualified identifier before passing it to dialect explorers.
            var parts = probeName.split('\\.')
            if (parts.size() > 2) { exit 400, '仅支持表名或模式.表名' }
            for (index, part in parts) {
                part = part.trim()
                if (part.startsWith('"') && part.endsWith('"')) { part = part.substring(1, part.length() - 1) }
                else if (part.startsWith('`') && part.endsWith('`')) { part = part.substring(1, part.length() - 1) }
                else if (part.startsWith('[') && part.endsWith(']')) { part = part.substring(1, part.length() - 1) }
                if (!part.matches('[\\p{L}_][\\p{L}\\p{N}_$#]*')) { exit 400, '物理表名格式不正确' }
                parts[index] = part
            }
            probeName = parts[parts.size() - 1]
            if (parts.size() == 2) {
                if (sourceType == 'mysql' || sourceType == 'oceanbasemysql' || sourceType == 'hive') {
                    if (!parts[0].equalsIgnoreCase(sourceConfig.getDatabase())) { exit 403, '表名中的库名不匹配当前数据源' }
                } else { sourceConfig.setSchema(parts[0]) }
            }
            var page = metadataExplorerService.getTablesPage(sourceConfig, 1, 100, probeName)
            for (candidate in page.rows) {
                var candidateName = textOf(candidate.tableName)
                if (candidateName.equalsIgnoreCase(probeName)
                    || candidateName.equalsIgnoreCase(parts.join('.'))) { info = candidate; break }
            }
            if (info == null) { exit 422, '未读取到当前表概览，请检查来源表和模式配置' }
            info.rowCount = metadataExplorerService.getTableRowCount(sourceConfig, probeName)
            if (info.columnCount == null) {
                info.columnCount = metadataExplorerService.getColumns(sourceConfig, probeName).size()
            }
        } else {
            info = metadataExplorerService.getTableInfo(sourceConfig, probeName)
        }
    }
    return {
        tid: requestedTableId, datasourceId: tid,
        recordCount: info.rowCount, fieldCount: info.columnCount,
        storageSize: info.totalSizeFormatted, totalSizeFormatted: info.totalSizeFormatted,
        totalSizeBytes: info.totalSizeBytes,
        ddlCreateTime: managedHive ? info.createTime : dateValue(info.createTime),
        ddlUpdateTime: managedHive ? info.updateTime : dateValue(info.updateTime)
    }
}
var info = null
if (managedHive) {
    var hiveDatabase = firstValue(asset, ['database', 'dbName', 'db_name'])
    if (blank(hiveDatabase) || !textOf(hiveDatabase).matches('[A-Za-z_][A-Za-z0-9_]*')) { exit 400, 'Hive 库名格式不正确' }
    var connection = null
    try {
        connection = hwhive.getConnection()
        info = { version: connection.getMetaData().getDatabaseProductVersion(),
            tableCount: hwhive.queryTable('SHOW TABLES IN `' + hiveDatabase + '`').size(),
            viewCount: null, procedureCount: null, userCount: null,
            totalSizeFormatted: null, dataSizeFormatted: null }
    } finally { if (connection != null) { connection.close() } }
} else { info = metadataExplorerService.getDatabaseInfo(dataSourceOf(asset)) }
var totalSize = managedHive ? info.totalSizeFormatted : sizeValue(info == null ? null : info.totalSizeFormatted)
var dataSize = managedHive ? info.dataSizeFormatted : sizeValue(info == null ? null : info.dataSizeFormatted)
if (dataSize == '0 B' && totalSize != '0 B') { dataSize = totalSize }
return {
    dbVersion: info == null ? '' : (blank(info.version) ? '' : info.version),
    db_version: info == null ? '' : (blank(info.version) ? '' : info.version),
    dataSize: dataSize, totalSize: totalSize,
    tableCount: info == null ? null : info.tableCount,
    viewCount: info == null ? null : info.viewCount,
    procedureCount: info == null ? null : info.procedureCount,
    userCount: info == null ? null : info.userCount
}
