{
  "properties": {},
  "id": "8315865f538845249d49f66c47a0ab27",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "11.根据表id刷新表元数据信息",
  "createTime": 1782955344272,
  "updateTime": 1787156076836,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refreshTableInfo",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"95fb07dab8094dbcafdfafd96359a3a4\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据已登记表标识读取所属数据源，重新探查物理表及字段结构，更新本地表和字段快照并刷新检索索引。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.根据表id刷新表元数据信息
// 路由：POST /dst/database/metadata/refreshTableInfo
// 业务说明：根据已登记表标识读取所属数据源，重新探查物理表及字段结构，更新本地表和字段快照并刷新检索索引。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import jsons
import java.util.Map
import log

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var nowText = () => date_format(new Date())
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
        tid: rowValue(main, ['tid']),
        id: rowValue(main, ['tid']),
        assetType: assetType,
        dbId: rowValue(main, ['datasourceId', 'datasource_id']),
        dbName: rowValue(main, ['dbName', 'db_name']),
        dbType: rowValue(main, ['dbType', 'db_type']),
        jdbcURL: rowValue(main, ['jdbcUrl', 'jdbc_url']),
        username: rowValue(main, ['username']),
        password: rowValue(main, ['password']),
        tableName: rowValue(main, ['tableName', 'table_name']),
        tableNameEn: rowValue(main, ['tableName', 'table_name']),
        tableNameCn: rowValue(main, ['tableNameCn', 'table_name_cn']),
        tableComment: rowValue(main, ['tableComment', 'table_comment'])
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
    if (assetType == 'table' && !blank(result.dbId)) {
        var datasourceRows = db.select("""
            select tid, db_name as dbName, app_id as appId, org_id as orgId
              from db_datasource_t
             where ifnull(is_del, 0) = 0
               and tid = #{result.dbId}
             limit 1
        """)
        var datasource = datasourceRows == null || datasourceRows.size() == 0 ? null : datasourceRows[0]
        if (datasource != null) {
            var datasourceName = datasource.dbname || datasource.dbName || datasource.db_name
            result.datasourceId = result.dbId
            result.datasourceName = datasourceName
            result.dbName = datasourceName
            result.sourceDbName = datasourceName
            result.appId = datasource.appid || datasource.appId || datasource.app_id
            result.orgId = datasource.orgid || datasource.orgId || datasource.org_id
        }
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
var columnKey = (value) => blank(value) ? '' : textOf(value).trim().toLowerCase()
var columnData = (tableId, col) => {
    // 返回当前业务分支的标准结果。
    return {
        table_id: tableId,
        column_name: col.columnName,
        column_comment: col.columnComment,
        data_type: col.dataType,
        column_type: col.columnType,
        length: col.length,
        precision_length: col.precision,
        scale: col.scale,
        nullable: col.nullable == true ? 1 : 0,
        default_value: col.defaultValue,
        primary_key: col.primaryKey == true ? 1 : 0,
        auto_increment: col.autoIncrement == true ? 1 : 0,
        is_unique: col.unique == true ? 1 : 0,
        indexed: col.indexed == true ? 1 : 0,
        ordinal_position: col.ordinalPosition,
        charset: col.charset,
        collation: col.collation,
        extra: col.extra,
        is_del: 0,
        updated_time: nowText()
    }
}
var refreshColumns = (tableId, columns) => {
    // 从 MySQL 业务表读取当前有效数据。
    var existing = db.table('db_table_column_t').where().eq('table_id', tableId).eq('is_del', 0).select()
    var existingMap = {}
    for (old in existing) {
        existingMap[columnKey(rowValue(old, ['columnName', 'column_name']))] = old
    }
    var inserted = 0
    var updated = 0
    var deleted = 0
    for (col in columns == null ? [] : columns) {
        var key = columnKey(col.columnName)
        if (blank(key)) {
            continue
        }
        var old = existingMap[key]
        var data = columnData(tableId, col)
        if (old == null) {
            data.tid = IdUtil.getSnowflakeNextIdStr()
            data.created_time = nowText()
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').primary('tid').insert(data)
            inserted = inserted + 1
        } else {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').where().eq('tid', rowValue(old, ['tid'])).update(data)
            existingMap[key] = null
            updated = updated + 1
        }
    }
    for (key in existingMap.keySet()) {
        var old = existingMap[key]
        if (old != null) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').where().eq('tid', rowValue(old, ['tid'])).update({ is_del: 1, updated_time: nowText() })
            deleted = deleted + 1
        }
    }
    // 返回当前业务分支的标准结果。
    return { inserted: inserted, updated: updated, deleted: deleted }
}

// 解析请求体并为可选参数设置默认值。
var tableId = body == null ? null : body.tableId
if (blank(tableId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableId不能为空'
}

var oldTable = loadAsset(tableId)
if (oldTable == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, 'tableId不正确'
}
var dbAsset = loadAsset(firstValue(oldTable, ['dbId']))
if (dbAsset == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '表所属数据源不存在'
}

var tableName = firstValue(oldTable, ['tableName', 'tableNameEn'])
var dsConfig = dataSourceOf(dbAsset)
var tableInfo = metadataExplorerService.getTableInfo(dsConfig, tableName)
if (tableInfo == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '表不存在或无法获取表信息'
}
var rowCount = metadataExplorerService.getTableRowCount(dsConfig, tableName)
if (rowCount != null) {
    tableInfo.setRowCount(rowCount)
}
var columns = metadataExplorerService.getColumns(dsConfig, tableName)
var columnSummary = refreshColumns(tableId, columns)

var tableComment = blank(tableInfo.tableComment) ? tableName : tableInfo.tableComment
var totalSizeFormatted = blank(tableInfo.totalSizeFormatted) ? '0 B' : tableInfo.totalSizeFormatted
var totalSizeBytes = tableInfo.totalSizeBytes == null ? 0 : tableInfo.totalSizeBytes
var fieldCount = columns == null ? 0 : columns.size()
var recordCount = tableInfo.rowCount == null ? 0 : tableInfo.rowCount


// 将校验后的业务数据写入 MySQL 主表。
db.table('db_table_t').primary('tid').update({
    tid: tableId,
    table_name_cn: tableComment,
    table_comment: tableComment,
    record_count: recordCount,
    field_count: fieldCount,
    storage_size: totalSizeFormatted,
    total_size_bytes: totalSizeBytes,
    ddl_create_time: tableInfo.createTime,
    ddl_update_time: tableInfo.updateTime,
    updated_time: nowText()
}, true)

var patch = {
    tid: tableId,
    id: tableId,
    dbId: oldTable.dbId,
    datasourceId: oldTable.datasourceId || oldTable.dbId,
    datasourceName: oldTable.datasourceName || oldTable.dbName,
    dbName: oldTable.datasourceName || oldTable.dbName,
    sourceDbName: oldTable.datasourceName || oldTable.dbName,
    tableNameCn: tableComment,
    tableName: tableName,
    tableNameEn: tableName,
    recordCount: recordCount,
    fieldCount: fieldCount,
    storageSize: totalSizeFormatted,
    totalSizeFormatted: totalSizeFormatted,
    totalSizeBytes_num: totalSizeBytes,
    ddlCreatTime: tableInfo.createTime,
    ddlUpdateTime: tableInfo.updateTime,
    updatedTime: nowText(),
    updatedTime_date: nowText(),
    columnRefresh: columnSummary
}

try {
    var merged = loadAsset(tableId)
    if (merged == null) {
        merged = patch
    } else {
        for (key in patch.keySet()) {
            merged[key] = patch[key]
        }
    }
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(merged), false)
    // 返回当前业务分支的标准结果。
    return merged
} catch (e) {
    log.warn('sync dataassets es failed, tid: {}, error: {}', tableId, e.message)
    // 返回当前业务分支的标准结果。
    return patch
}
