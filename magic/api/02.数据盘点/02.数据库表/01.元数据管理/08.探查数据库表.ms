{
  "properties" : { },
  "id" : "4347c17766bf43ac867654bea91e0d9a",
  "script" : null,
  "groupId" : "6e30fb6d565c40b18d480b219e13b20e",
  "name" : "08.探查数据库表",
  "createTime" : 1782955344232,
  "updateTime" : 1791112525954,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/exploreDbTables",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\n    \"dbId\": \"数据库资产ID\",\n    \"tenantId\": \"可选租户ID\",\n    \"dryRun\": false,\n    \"refreshExisting\": false,\n    \"tableOverrides\": \"测试\"\n}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "探查当前租户的数据源物理表并登记/刷新表与字段快照；dryRun=true 只读。一次预取该数据源已登记字段，刷新时按表在内存比对并保留旧字段 ID/治理配置，不按物理表逐次补查平台数据库。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：08.探查数据库表
// 路由：POST /dst/database/metadata/exploreDbTables
// 业务说明：元数据管理 08：探查物理数据库并登记数据表快照；使用请求参数 dbId、dryRun、refreshExisting、tableOverrides；涉及 db_datasource_t、db_table_t、db_table_column_t 等业务表；执行数据库写入并返回处理结果。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredSourceProbeService
import jsons
import java.util.Map
import log
import tenantRuntime
/*
 * 元数据管理 08：探查物理数据库并登记数据表快照。
 *
 * 读取 db_datasource_t 的连接信息，调用底层 MetadataExplorerService 探查物理表。
 * 新表写 db_table_t、db_table_column_t，并同步 dataassets ES；不再写
 * da_asset_t。dryRun=true 时完全只读。refreshExisting=true 时刷新已有物理元数据，
 * 同名字段沿用原 tid 和治理配置，避免覆盖登记步骤 3/4 的人工配置。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => blank(value) ? '' : textOf(value).trim().toLowerCase()
var nowTime = () => new Date()
var newId = () => IdUtil.getSnowflakeNextIdStr()
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再探查数据源'
}
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value == null) {
            var upperKey = textOf(key).toUpperCase()
            value = row instanceof Map ? row.get(upperKey) : row[upperKey]
        }
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
var loadDatasource = (datasourceId) => {
    // 从 MySQL 业务表读取当前有效数据。
    var source = db.table('db_datasource_t').where()
        .eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (source == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var result = {
        tid: datasourceId,
        tenantId: rowValue(source, ['tenantId', 'tenant_id']),
        dbName: rowValue(source, ['dbName', 'db_name']),
        dbType: rowValue(source, ['dbType', 'db_type']),
        jdbcURL: rowValue(source, ['jdbcUrl', 'jdbc_url']),
        username: rowValue(source, ['username']),
        password: rowValue(source, ['password']),
        appId: rowValue(source, ['appId', 'app_id']),
        orgId: rowValue(source, ['orgId', 'org_id'])
    }
    var connectionConfig = datasourceConnectionConfig('read', rowValue(source, ['tid']), null, false)
    if (connectionConfig != null && !connectionConfig.isEmpty()) { result.putAll(connectionConfig) }
    result.tid = datasourceId
    result.dbName = rowValue(source, ['dbName', 'db_name'])
    result.dbType = rowValue(source, ['dbType', 'db_type'])
    result.jdbcURL = rowValue(source, ['jdbcUrl', 'jdbc_url'])
    result.username = rowValue(source, ['username'])
    result.password = rowValue(source, ['password'])
    result.appId = rowValue(source, ['appId', 'app_id'])
    result.orgId = rowValue(source, ['orgId', 'org_id'])
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
        return DatabaseType.fromCode(lower(code))
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
var registeredMapOf = (datasourceId) => {
    var result = {}
    // 从 MySQL 业务表读取当前有效数据。
    var rows = db.table('db_table_t').where()
        .eq('datasource_id', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).select()
    for (row in rows) {
        var tableName = firstValue(row, ['tableName', 'table_name'])
        if (!blank(tableName) && result[lower(tableName)] == null) {
            result[lower(tableName)] = row
        }
    }
    // 返回当前业务分支的标准结果。
    return result
}
var typeName = (rawType) => {
    // 返回当前业务分支的标准结果。
    return !blank(rawType) && lower(rawType).indexOf('view') >= 0 ? '视图' : '数据表'
}
var containsAny = (value, words) => {
    var source = lower(value)
    for (word in words) {
        if (source.indexOf(word) >= 0) {
            // 返回当前业务分支的标准结果。
            return true
        }
    }
    // 返回当前业务分支的标准结果。
    return false
}
var inferBusinessType = (tableName, comment) => {
    var value = lower(tableName) + ' ' + lower(comment)
    if (containsAny(value, ['log', 'audit', 'trace', '日志', '审计', '轨迹'])) {
        // 返回当前业务分支的标准结果。
        return { type: '日志表', reason: '命中日志或审计关键字' }
    }
    if (containsAny(value, ['dict', 'dictionary', 'code', 'enum', '字典', '码表', '枚举'])) {
        // 返回当前业务分支的标准结果。
        return { type: '字典表', reason: '命中字典或码表关键字' }
    }
    if (containsAny(value, ['tmp', 'temp', '临时'])) {
        // 返回当前业务分支的标准结果。
        return { type: '临时表', reason: '命中临时表关键字' }
    }
    if (containsAny(value, ['bak', 'backup', 'history', 'archive', '备份', '历史', '归档'])) {
        // 返回当前业务分支的标准结果。
        return { type: '备份表', reason: '命中备份或历史关键字' }
    }
    // 返回当前业务分支的标准结果。
    return { type: '业务表', reason: '默认识别为业务表' }
}
var columnValues = (tableId, columnId, col, tenantId) => {
    // 返回当前业务分支的标准结果。
    return {
        tid: columnId,
        table_id: tableId,
        tenant_id: tenantId,
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
        updated_time: nowTime()
    }
}
var saveColumns = (tableId, columns, tenantId, oldRows) => {
    var oldMap = {}
    for (old in oldRows == null ? [] : oldRows) {
        oldMap[lower(firstValue(old, ['columnName', 'column_name']))] = old
    }
    var count = 0
    for (col in columns == null ? [] : columns) {
        var key = lower(col.columnName)
        var old = oldMap[key]
        var columnId = old == null ? newId() : firstValue(old, ['tid'])
        var values = columnValues(tableId, columnId, col, tenantId)
        if (old == null) {
            values.created_time = nowTime()
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').primary('tid').insert(values)
        } else {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').primary('tid').update(values, true)
            oldMap[key] = null
        }
        count = count + 1
    }
    for (key in oldMap.keySet()) {
        var old = oldMap[key]
        if (old != null) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_column_t').primary('tid').update({
                tid: firstValue(old, ['tid']),
                is_del: 1,
                updated_time: nowTime()
            }, true)
        }
    }
    // 返回当前业务分支的标准结果。
    return count
}
var saveEs = (tableId, doc) => {
    try {
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(doc), false)
    } catch (e) {
        log.warn('同步数据表 ES 失败, tid: {}, error: {}', tableId, e.message)
    }
}

// 解析请求体并为可选参数设置默认值。
var datasourceId = body == null ? null : body.dbId
if (blank(datasourceId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'dbId 不能为空'
}
var dryRun = body != null && body.dryRun == true
var refreshExisting = body != null && body.refreshExisting == true
var source = loadDatasource(datasourceId)
if (source == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '数据源不存在'
}
var sourceType = lower(firstValue(source, ['dbType', 'databaseType', 'dataSourceType']))
var structuredSource = sourceType == 'api' || sourceType == 'ftp' || sourceType == 'kafka'
var physicalTables = structuredSource
    ? structuredSourceProbeService.getTablesPage(source, 1, 500, null).rows
    : metadataExplorerService.getTables(dataSourceOf(source))
var registeredMap = registeredMapOf(datasourceId)
// 一次取得此数据源已登记字段。只探查/复用时仅需存在性；刷新并写入时
// 一并预取完整旧字段，供后续逐表比对复用原 tid 和人工治理配置。
var storedColumnRows = refreshExisting && !dryRun
    ? db.select("""
        select c.*
          from db_table_column_t c
          join db_table_t t on t.tid = c.table_id
         where t.datasource_id = #{datasourceId}
           and t.tenant_id = #{tenantId}
           and c.tenant_id = #{tenantId}
           and t.is_del = 0
           and c.is_del = 0
    """)
    : db.select("""
        select distinct c.table_id as tableId
          from db_table_column_t c
          join db_table_t t on t.tid = c.table_id
         where t.datasource_id = #{datasourceId}
           and t.tenant_id = #{tenantId}
           and c.tenant_id = #{tenantId}
           and t.is_del = 0
           and c.is_del = 0
    """)
var storedColumnTableIds = {}
var storedColumnsByTable = {}
for (storedColumn in storedColumnRows == null ? [] : storedColumnRows) {
    var storedTableId = textOf(firstValue(storedColumn, ['tableId', 'table_id']))
    if (blank(storedTableId)) { continue }
    storedColumnTableIds[storedTableId] = true
    if (refreshExisting && !dryRun) {
        if (storedColumnsByTable[storedTableId] == null) { storedColumnsByTable[storedTableId] = [] }
        storedColumnsByTable[storedTableId].add(storedColumn)
    }
}
var overrideMap = {}
for (override in body == null || body.tableOverrides == null ? [] : body.tableOverrides) {
    var overrideName = firstValue(override, ['tableName', 'tableNameEn'])
    if (!blank(overrideName)) {
        overrideMap[lower(overrideName)] = override
    }
}
var rows = []
var createdCount = 0
var reusedCount = 0
var refreshedCount = 0

for (tableInfo in physicalTables == null ? [] : physicalTables) {
    var tableName = tableInfo.tableName
    if (blank(tableName)) {
        continue
    }
    var existing = registeredMap[lower(tableName)]
    var override = overrideMap[lower(tableName)]
    var tableId = existing == null ? newId() : firstValue(existing, ['tid'])
    var action = existing == null ? 'create' : (refreshExisting ? 'refresh' : (override == null ? 'reuse' : 'configure'))
    var needsColumnBootstrap = existing != null && storedColumnTableIds[tableId] != true
    if (action == 'reuse' && needsColumnBootstrap) {
        action = 'bootstrap'
    }
    var comment = firstValue(override, ['tableComment', 'tableNameCn'])
    if (blank(comment)) {
        comment = blank(tableInfo.tableComment) ? tableName : tableInfo.tableComment
    }
    var business = existing == null ? inferBusinessType(tableName, comment) : {
        type: firstValue(existing, ['businessType', 'business_type']),
        reason: firstValue(existing, ['businessTypeReason', 'business_type_reason'])
    }
    if (!blank(firstValue(override, ['businessType']))) {
        business = {
            type: firstValue(override, ['businessType']),
            reason: firstValue(override, ['businessTypeReason'])
        }
    }
    var columns = []
    var fieldCount = tableInfo.columnCount == null ? 0 : tableInfo.columnCount
    var shouldLoadColumns = action == 'create' || action == 'refresh' || needsColumnBootstrap
    if (shouldLoadColumns) {
        columns = structuredSource
            ? structuredSourceProbeService.getColumns(source, tableName)
            : metadataExplorerService.getColumns(dataSourceOf(source), tableName)
        fieldCount = columns == null ? 0 : columns.size()
    } else if (rowValue(existing, ['fieldCount', 'field_count']) != null) {
        fieldCount = rowValue(existing, ['fieldCount', 'field_count'])
    }
    var output = {
        tid: tableId,
        id: tableId,
        assetType: 'table',
        datasourceId: datasourceId,
        dbId: datasourceId,
        tableName: tableName,
        tableNameEn: tableName,
        tableNameCn: comment,
        tableComment: comment,
        tableType: typeName(tableInfo.tableType),
        businessType: blank(business.type) ? '业务表' : business.type,
        businessTypeReason: business.reason,
        recordCount: tableInfo.rowCount == null ? 0 : tableInfo.rowCount,
        fieldCount: fieldCount,
        storageSize: blank(tableInfo.totalSizeFormatted) ? '0 B' : tableInfo.totalSizeFormatted,
        totalSizeBytes: tableInfo.totalSizeBytes == null ? 0 : tableInfo.totalSizeBytes,
        orgId: firstValue(source, ['orgId']),
        orgPath: firstValue(source, ['orgPath']),
        appId: firstValue(source, ['appId']),
        annotated: override == null ? rowValue(existing, ['annotated']) : 1,
        action: action
    }
    if (!dryRun && action != 'reuse') {
        var values = {
            tid: tableId,
            datasource_id: datasourceId,
            table_name: tableName,
            table_name_en: tableName,
            table_name_cn: comment,
            table_comment: comment,
            table_type: output.tableType,
            business_type: output.businessType,
            business_type_reason: output.businessTypeReason,
            annotated: output.annotated == null ? 0 : output.annotated,
            record_count: output.recordCount,
            field_count: output.fieldCount,
            storage_size: output.storageSize,
            total_size_formatted: output.storageSize,
            total_size_bytes: output.totalSizeBytes,
            ddl_create_time: tableInfo.createTime,
            ddl_update_time: tableInfo.updateTime,
            org_id: output.orgId,
            org_path: output.orgPath,
            source_table_id: tableId,
            source_table_name: tableName,
            manage_unit: output.orgId,
            asset_desc: comment,
            tenant_id: tenantId,
            asset_status: existing == null ? 0 : firstValue(existing, ['assetStatus', 'asset_status']),
            flow_status: existing == null ? 0 : firstValue(existing, ['flowStatus', 'flow_status']),
            flow_order_id: null,
            is_del: 0,
            updated_time: nowTime()
        }
        if (existing == null) {
            values.created_time = nowTime()
            values.reg_time = nowTime()
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_t').primary('tid').insert(values)
            createdCount = createdCount + 1
        } else {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('db_table_t').primary('tid').update(values, true)
            refreshedCount = refreshedCount + 1
        }
        if (shouldLoadColumns) {
            saveColumns(tableId, columns, tenantId, storedColumnsByTable[tableId])
        }
        output.assetStatus = values.asset_status
        output.flowStatus = values.flow_status
        output.updatedTime = date_format(now)
        saveEs(tableId, output)
    } else if (action == 'reuse') {
        reusedCount = reusedCount + 1
    } else if (action == 'create') {
        createdCount = createdCount + 1
    } else {
        refreshedCount = refreshedCount + 1
    }
    rows.add(output)
}
// 返回当前业务分支的标准结果。
return {
    dbId: datasourceId,
    dryRun: dryRun,
    refreshExisting: refreshExisting,
    totalScanned: physicalTables == null ? 0 : physicalTables.size(),
    createdCount: createdCount,
    reusedCount: reusedCount,
    refreshedCount: refreshedCount,
    rows: rows
}
