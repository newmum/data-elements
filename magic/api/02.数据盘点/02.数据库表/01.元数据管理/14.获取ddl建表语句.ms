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
import cn.hutool.json.JSONUtil as poolJsons
import tenantRuntime
import tablePartitions
import canvasTableDdl
import portableDdl
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.metautil.model.dto.ColumnInfo
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService
import java.util.Map
import java.util.List

/*
 * 元数据管理 14：生成建表 DDL。
 *
 * 只读接口。根据 propList.tableName/tableNameCn 和 tableItems 字段定义生成目标库方言的 CREATE TABLE。
 * 传 dbId 时按目标数据库类型生成；不传 dbId 时使用默认 MySQL 配置，仅用于模板预览。
 */

// 来源DDL转换只处理文本与字段模型，不查询业务资源或校验租户。
if (body?.importSourceDdl == true || body?.convertSourceDdl == true) {
    try {
        var model = portableDdl.read(body.sourceDdl, body.sourceDatabaseType)
        if (body?.convertSourceDdl != true) { return model }
        var config = new DataSourceConfig()
        config.setDatabaseType(DatabaseType.fromCode(body.targetDatabaseType))
        config.setCharset(body?.targetCharset)
        config.setNationalCharset(body?.targetNationalCharset)
        var converted = portableDdl.prepare(model.columns, config)
        var ddl = metadataExplorerService.generateCreateTableDdl(config,
            body.tableName == null ? model.tableName : body.tableName, converted.columns, model.tableComment)
        return {ddl:ddl, tableItems:converted.tableItems, warnings:converted.warnings, sourceWarnings:model.warnings}
    } catch (error) {
        exit 400, '来源DDL转换失败，请核对来源数据库类型和建表语法：' + error.message
    }
}

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var ddlFailureTip = (failure) => {
    var reason = failure == null ? '' : textOf(failure.getMessage())
    if (blank(reason)) { return '请检查字段类型、长度和分区配置后重试' }
    if (reason.contains('请配置1至') || reason.contains('LIST分区必须填写枚举值')) {
        return '请填写至少一个区域编码或枚举值，用逗号分隔，例如350100,350200；每个值会生成一个分区，最多2000项'
    }
    if (reason.contains('LIST分区值重复')) { return '区域编码或枚举值有重复，请删除重复项；数字字段中的01与1也表示同一个值' }
    if (reason.contains('不是有效数字')) { return '所选分区字段是数字类型，请填写数字枚举值，不要填写中文名称或引号' }
    if (reason.contains('分区时间边界必须')) { return '所选分区字段是日期或时间类型。按区域分区请改选区域编码字段；按时间分区可选择按月分区，或填写YYYY-MM-DD格式的日期值' }
    if (reason.contains('分区字段不在目标字段中') || reason.contains('分区字段或名称无效')) { return '请选择目标表中实际存在的分区字段，然后重新生成建表语句' }
    if (reason.contains('RANGE分区上界必须严格递增')) { return '范围分区的结束值必须从小到大排列，不能重复或倒序' }
    if (reason.contains('DEFAULT/MAXVALUE必须位于最后')) { return '用于接收其他值或未来数据的兜底分区必须放在最后' }
    if (reason.contains('分区名称或Hive分桶值重复')) { return '分区名称或分区目录有重复，请为每个分区设置不同的名称' }
    if (reason.contains('该MySQL目标不支持LIST DEFAULT')) { return '当前目标库不支持区域分区的默认兜底分区，请明确列出区域编码或调整分区规则' }
    if (reason.contains('按月分区请选择目标')) { return '按月分区需要日期或时间字段，请更换分区字段；按区域编码划分请选择区域／枚举分区' }
    return reason
}
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
// Editable target type/length are authoritative. A previous native columnType
// must not keep a field as TEXT after the operator selects bounded VARCHAR.
var resolvedColumnType = item => {
    var selectedRaw = firstValue(item, ['dataType','type'])
    var selected = blank(selectedRaw) ? '' : textOf(selectedRaw).trim()
    var native = firstValue(item, ['columnType'])
    var base = selected.toLowerCase().replaceAll('\\s*\\(.*$', '').trim()
    var origin = portableDdl.originTypeForEditor(base, firstValue(item,['sourceColumnType','sourceDataType']),
        firstValue(item,['sourceDatabaseType']))
    if (!blank(origin)) { return origin }
    if (['text','tinytext','mediumtext','longtext','clob','nclob'].contains(base)) { return base }
    if (['varchar','varchar2','nvarchar','nvarchar2','char','nchar'].contains(base)
        && rowValue(item, ['length']) != null && rowValue(item, ['length'])::long > 0) {
        return selected.replaceAll('\\s*\\(.*$', '') + '(' + rowValue(item, ['length']) + ')'
    }
    if (!blank(selected) && !blank(native)) {
        var original = textOf(native).toLowerCase().replaceAll('\\s*\\(.*$', '').trim()
        if (original != base && ['varchar','char','text','blob','date','datetime','timestamp','int','bigint','decimal','float','double','tinyint'].contains(base)) {
            if (['varchar','char'].contains(base) && ['text','clob','nclob','longtext'].contains(original)) {
                return selected
            }
            return selected
        }
    }
    return blank(native) ? selected : native
}
var tenantId = body?.canvas == true || body?.propList?.dbId != null ? tenantRuntime.id() : null

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
    dsConfig.setPort(blank(port) ? null : port::int)
    dsConfig.setDatabase(textOf(databaseName))
    dsConfig.setUsername(blank(username) ? '' : textOf(username))
    dsConfig.setPassword(password == null ? '' : textOf(password))
    // Oracle SID and Service Name use different URL formats; reuse the tested JDBC URL.
    dsConfig.setConnectorProperties(asset)
    dsConfig.setJdbcUrl(firstValue(asset, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    dsConfig.setSchema(schema)
    dsConfig.setCharset(firstValue(asset, ['charset', 'characterSet', 'character_set', 'encoding']))
    dsConfig.setNationalCharset(firstValue(asset, ['nationalCharset', 'nationalCharacterSet', 'ncharCharset']))
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

// 显式打开DDL时只探查当前来源；全部目标一次预取并逐ID鉴权，不逐目标补查。
if (body?.partitionProbe == true) {
    var probeTenant = textOf(tenantRuntime.id())
    if (blank(probeTenant)) { exit 401, '当前登录租户无效' }
    var source = db.table('db_table_t').where().eq('tenant_id', probeTenant)
        .eq('tid', body.sourceTableId).eq('is_del', 0).selectOne()
    if (source == null) { exit 403, '来源表不存在或不在当前租户范围内' }
    var sourceDbId = textOf(firstValue(source, ['datasourceId', 'datasource_id']))
    var targetIds = body.targetDbIds
    if (!(targetIds instanceof List) || targetIds.size() < 1 || targetIds.size() > 20) { exit 400, '分区探查一次最多20个目标库' }
    var ids = [sourceDbId]
    for (id in targetIds) { if (!ids.contains(textOf(id))) { ids.add(textOf(id)) } }
    var rows = db.table('db_datasource_t').where().eq('tenant_id', probeTenant).eq('is_del', 0).in('tid', ids).select()
    var visible = (rows == null ? [] : rows)
    var byId = {}
    for (row in visible) { byId[textOf(rowValue(row, ['tid']))] = row }
    for (id in ids) { if (byId[id] == null) { exit 403, '来源或目标数据源不存在或已删除' } }
    var support = {}
    for (id in targetIds) {
        var targetType = DatabaseType.fromCode(textOf(firstValue(byId[textOf(id)], ['dbType','db_type'])))
        var allowed = targetType != null && tablePartitions.supported(targetType)
        if (targetType == DatabaseType.HIVE) {
            var targetPoolRaw = rowValue(byId[textOf(id)], ['poolCfg','pool_cfg'])
            var targetPool = blank(targetPoolRaw) ? {} : (targetPoolRaw instanceof Map ? targetPoolRaw : poolJsons.parse(textOf(targetPoolRaw)))
            allowed = !blank(firstValue(targetPool,['hiveProfile','hive_profile']))
                || textOf(firstValue(targetPool,['hiveConnectionMode','hive_connection_mode']) || '').toLowerCase() == 'huawei-mrs'
                || ['true','1'].contains(textOf(firstValue(targetPool,['usePlatformHiveConfig','use_platform_hive_config']) || '').toLowerCase())
                || textOf(firstValue(byId[textOf(id)],['dbType','db_type'])).toLowerCase() == 'mrshive'
        }
        support[textOf(id)] = allowed
    }
    var sourceRow = byId[sourceDbId]
    var sourceCode = textOf(firstValue(sourceRow, ['dbType','db_type'])).toLowerCase()
    var detected = {status:'unsupported', message:'当前来源不自动探查分区，可手工配置目标分区'}
    if (['oracle','oceanbaseoracle','oceanbase_oracle','dameng'].contains(sourceCode)) {
        var rawPool = rowValue(sourceRow, ['poolCfg','pool_cfg'])
        var pool = blank(rawPool) ? {} : (rawPool instanceof Map ? rawPool : poolJsons.parse(textOf(rawPool)))
        var connection = connectionProperties.resolve(probeTenant, sourceDbId)
        connection.dbType = sourceCode
        try { detected = metadataExplorerService.getPartitionInfo(dataSourceOf(connection), firstValue(source,['tableName','table_name'])) }
        catch (e) { detected = {status:'unknown', message:'来源分区探查未完成，请检查连接和目录权限；可手工配置'} }
    }
    return {source:detected, targetSupport:support, sideEffectsApplied:false}
}

// The materialization dialog generates DDL for several selected target
// databases in one request. Read and authorize the whole bounded set first;
// the dialect conversion below is CPU-only and does not query each datasource.
if (body?.batchGenerate == true) {
    if (body?.canvas != true) { exit 400, '批量生成建表语句仅支持画布物化流程' }
    var tenantId = textOf(tenantRuntime.id())
    var submittedTargets = body?.targets
    if (!(submittedTargets instanceof List) || submittedTargets.size() < 1 || submittedTargets.size() > 20) {
        exit 400, '一次最多为20个目标库生成建表语句'
    }
    var batchDbIds = []
    for (submittedTarget in submittedTargets) {
        var submittedProps = submittedTarget?.propList
        var submittedItems = submittedTarget?.tableItems
        var submittedDbId = textOf(submittedProps?.dbId)
        if (blank(submittedDbId) || submittedDbId.length() > 64
            || blank(submittedProps?.tableName) || !(submittedItems instanceof List)
            || submittedItems.size() < 1 || submittedItems.size() > 500) {
            exit 400, '批量建表语句包含无效目标库、表名或字段列表'
        }
        if (batchDbIds.contains(submittedDbId)) { exit 400, '批量建表语句包含重复目标库' }
        batchDbIds.add(submittedDbId)
    }
    var registeredRows = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('is_del', 0).in('tid', batchDbIds).select()
    var visibleRows = (registeredRows == null ? [] : registeredRows)
    var registeredById = {}
    for (registeredRow in visibleRows) {
        registeredById[textOf(rowValue(registeredRow, ['tid']))] = registeredRow
    }
    for (batchDbId in batchDbIds) {
        if (registeredById[batchDbId] == null) {
            exit 403, '目标库不存在或已删除'
        }
    }
    var batchTimeType = (databaseType) => {
        var code = databaseType == null ? '' : textOf(databaseType.getCode()).trim().toLowerCase()
        if (code.indexOf('oceanbase') >= 0 || code == 'oracle' || code == 'dameng') { return 'DATE' }
        if (code == 'postgresql' || code == 'kingbase' || code == 'gaussdb'
            || code == 'vertica' || code == 'hetu' || code == 'hive') { return 'TIMESTAMP' }
        if (code == 'sqlserver') { return 'DATETIME2' }
        return 'DATETIME'
    }
    var generated = []
    for (submittedTarget in submittedTargets) {
        var submittedProps = submittedTarget.propList
        var submittedItems = submittedTarget.tableItems
        var submittedDbId = textOf(submittedProps.dbId)
        var registered = registeredById[submittedDbId]
        var targetDisplayName = firstValue(registered, ['dbName', 'db_name', 'name']) || '所选目标数据源'
        var poolRaw = rowValue(registered, ['poolCfg', 'pool_cfg'])
        var pool = blank(poolRaw) ? {} : (poolRaw instanceof Map ? poolRaw : poolJsons.parse(textOf(poolRaw)))
        if (!(pool instanceof Map)) { exit 500, '目标库 pool_cfg 不是有效 JSON 对象' }
        var targetTypeCode = firstValue(registered, ['dbType', 'db_type'])
        if (blank(targetTypeCode)) { targetTypeCode = firstValue(pool, ['dbType']) }
        if (blank(targetTypeCode)) { exit 400, '目标库未配置数据库类型' }
        var targetType
        try { targetType = DatabaseType.fromCode(textOf(targetTypeCode).toLowerCase()) }
        catch (e) { exit 400, '目标库数据库类型不受支持' }
        var batchConfig = new DataSourceConfig()
        batchConfig.setDatabaseType(targetType)
        batchConfig.setDatabase(firstValue(pool, ['database', 'dbName', 'dbMetaDbName'])
            || firstValue(registered, ['dbName', 'db_name']))
        batchConfig.setUsername(firstValue(registered, ['username']) || firstValue(pool, ['username', 'user']))
        batchConfig.setSchema(firstValue(pool, ['schema', 'defaultSchema', 'currentSchema', 'schemaName']))
        batchConfig.setCharset(firstValue(pool, ['charset', 'characterSet', 'character_set', 'encoding']))
        batchConfig.setNationalCharset(firstValue(pool, ['nationalCharset', 'nationalCharacterSet', 'ncharCharset']))
        var requestedTableName = textOf(submittedProps.tableName).trim()
        if (targetType == DatabaseType.HIVE) { requestedTableName = requestedTableName.toLowerCase() }
        var batchColumns = []
        for (item in submittedItems) {
            var columnInfo = new ColumnInfo()
            columnInfo.setSourceDatabaseType(firstValue(item, ['sourceDatabaseType']) || (body?.sourceDatabaseType == null ? null : textOf(body.sourceDatabaseType)))
            var columnName = firstValue(item, ['columnName', 'name'])
            var normalizedColumnName = blank(columnName) ? '' : textOf(columnName).trim().toUpperCase()
            var dataType = firstValue(item, ['dataType', 'type'])
            var columnType = resolvedColumnType(item)
            if (normalizedColumnName == 'ODS_RKSJ' || normalizedColumnName == 'ODS_GXSJ') {
                dataType = batchTimeType(targetType)
                columnType = dataType
            }
            columnInfo.setColumnName(columnName)
            columnInfo.setQuotedIdentifier(rowValue(item, ['quotedIdentifier']) == true)
            columnInfo.setColumnComment(firstValue(item, ['columnComment', 'comment']))
            columnInfo.setDataType(dataType)
            columnInfo.setColumnType(columnType)
            if (rowValue(item, ['length']) != null) { columnInfo.setLength(rowValue(item, ['length'])::long) }
            if (rowValue(item, ['precisionLength']) != null) {
                columnInfo.setPrecision(rowValue(item, ['precisionLength'])::int)
            }
            if (rowValue(item, ['scale']) != null) { columnInfo.setScale(rowValue(item, ['scale'])::int) }
            columnInfo.setNullable(rowValue(item, ['nullable']) == 1 || rowValue(item, ['nullable']) == true)
            columnInfo.setDefaultValue(rowValue(item, ['defaultValue']))
            columnInfo.setPrimaryKey(rowValue(item, ['primaryKey']) == 1 || rowValue(item, ['primaryKey']) == true)
            columnInfo.setAutoIncrement(rowValue(item, ['autoIncrement']) == 1 || rowValue(item, ['autoIncrement']) == true)
            columnInfo.setUnique(rowValue(item, ['isUnique', 'unique']) == 1
                || rowValue(item, ['isUnique', 'unique']) == true)
            columnInfo.setIndexed(rowValue(item, ['indexed']) == 1 || rowValue(item, ['indexed']) == true)
            if (rowValue(item, ['ordinalPosition']) != null) {
                columnInfo.setOrdinalPosition(rowValue(item, ['ordinalPosition'])::int)
            }
            columnInfo.setCharset(rowValue(item, ['charset']))
            columnInfo.setLengthSemantics(rowValue(item, ['lengthSemantics']))
            if (rowValue(item, ['characterLength']) != null) { columnInfo.setCharacterLength(rowValue(item, ['characterLength'])::long) }
            if (rowValue(item, ['byteLength']) != null) { columnInfo.setByteLength(rowValue(item, ['byteLength'])::long) }
            columnInfo.setCollation(rowValue(item, ['collation']))
            columnInfo.setExtra(rowValue(item, ['extra']))
            batchColumns.add(columnInfo)
        }
        try {
            var converted = portableDdl.prepare(batchColumns, batchConfig)
            var ddl = metadataExplorerService.generateCreateTableDdl(batchConfig,
                requestedTableName, converted.columns, submittedProps.tableNameCn)
            var partitionPlan = tablePartitions.normalize(submittedTarget.partitionPlan, converted.columns, targetType)
            var constraintPreview = tablePartitions.enrichConstraints(ddl, submittedTarget.sourceConstraints, batchConfig, partitionPlan)
            ddl = constraintPreview.ddl
            var previewWarnings = []
            previewWarnings.addAll(converted.warnings)
            previewWarnings.addAll(constraintPreview.warnings)
            ddl = tablePartitions.apply(ddl, partitionPlan, batchConfig)
            // 仅处理自动生成的DDL；手工提交语句不进入这些生成规则。
            generated.add({dbId: submittedDbId, requestedTableName: requestedTableName,
                tableName: requestedTableName, ddl: ddl, warnings: previewWarnings, tableItems: converted.tableItems,
                partitionPlan:partitionPlan, partitionSupported:tablePartitions.supported(targetType)})
        } catch (e) {
            exit 400, '目标数据源“' + targetDisplayName + '”未能生成建表语句：' + ddlFailureTip(e)
        }
    }
    return {batchGenerate: true, results: generated}
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
var targetConnection = {}
var registeredTypeCode = null
if (body.canvas == true) {
    if (blank(propList.dbId)) { exit 400, '请选择已登记的目标库' }
    var registered = connectionProperties.resolve(tenantRuntime.id(), textOf(propList.dbId))
    if (registered == null || registered.isEmpty()) { exit 404, '当前租户中不存在所选目标数据源' }
    targetConnection = registered
    registeredTypeCode = registered.dbType
    // DDL generation only needs target dialect/scope; managed Hive credentials stay on the server.
    dsConfig = new DataSourceConfig()
    var targetType = DatabaseType.fromCode(textOf(registered.dbType).toLowerCase())
    if (targetType == null) { exit 400, '目标数据源未配置数据库类型' }
    dsConfig.setDatabaseType(targetType)
    dsConfig.setConnectorProperties(registered)
    dsConfig.setDatabase(firstValue(registered, ['database', 'dbName', 'dbMetaDbName']))
    dsConfig.setUsername(firstValue(registered, ['username', 'user']))
    dsConfig.setSchema(firstValue(registered, ['schema', 'defaultSchema', 'currentSchema', 'schemaName']))
} else if (propList != null && !blank(propList.dbId)) {
    var dbAsset = loadAsset(propList.dbId)
    if (dbAsset == null) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, 'dbId参数不正确'
    }
    targetConnection = dbAsset
    registeredTypeCode = firstValue(dbAsset, ['dbType', 'databaseType'])
    // DDL 预览只依赖目标库方言和可选的库/模式名，并不会建立 JDBC 连接。
    // 因此不能复用 dataSourceOf 的“主机、账号、连接参数完整”校验：
    // 该校验属于实际建表/连通性测试，若用于预览会把本可生成的 DDL 错误地拦截。
    dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(databaseTypeOf(firstValue(dbAsset, ['dbType', 'databaseType', 'dataSourceType'])))
    dsConfig.setConnectorProperties(dbAsset)
    dsConfig.setDatabase(firstValue(dbAsset, ['database', 'dbName', 'dbMetaDbName']))
    dsConfig.setUsername(firstValue(dbAsset, ['username', 'dbUser', 'user']))
    dsConfig.setSchema(firstValue(dbAsset, ['schema', 'defaultSchema', 'currentSchema', 'schemaName', 'owner', 'dbSchema']))
} else {
    dsConfig = new DataSourceConfig()
    dsConfig.setDatabaseType(databaseTypeOf(propList == null ? null : propList.dbType))
    if(propList != null){dsConfig.setConnectorProperties(propList)}
    dsConfig.setHost('localhost')
    dsConfig.setPort(3306)
    dsConfig.setDatabase('test')
    dsConfig.setUsername('root')
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
    columnInfo.setSourceDatabaseType(firstValue(item, ['sourceDatabaseType']) || (body.sourceDatabaseType == null ? null : textOf(body.sourceDatabaseType)))
    var columnName = firstValue(item, ['columnName', 'name'])
    var normalizedColumnName = blank(columnName) ? '' : textOf(columnName).trim().toUpperCase()
    var dataType = firstValue(item, ['dataType', 'type'])
    var columnType = resolvedColumnType(item)
    if (normalizedColumnName == 'ODS_RKSJ' || normalizedColumnName == 'ODS_GXSJ') {
        dataType = materializationTimeType(dsConfig.getEffectiveDatabaseType())
        columnType = dataType
    }
    columnInfo.setColumnName(columnName)
    columnInfo.setQuotedIdentifier(rowValue(item, ['quotedIdentifier']) == true)
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
    columnInfo.setLengthSemantics(rowValue(item, ['lengthSemantics']))
    if (rowValue(item, ['characterLength']) != null) { columnInfo.setCharacterLength(rowValue(item, ['characterLength'])::long) }
    if (rowValue(item, ['byteLength']) != null) { columnInfo.setByteLength(rowValue(item, ['byteLength'])::long) }
    columnInfo.setCollation(rowValue(item, ['collation']))
    columnInfo.setExtra(rowValue(item, ['extra']))
    columns.add(columnInfo)
}

// 返回当前业务分支的标准结果。
try {
    var targetTableName = textOf(propList.tableName).trim()
    if (dsConfig.getEffectiveDatabaseType() == DatabaseType.HIVE) { targetTableName = targetTableName.toLowerCase() }
    var converted = portableDdl.prepare(columns, dsConfig)
    var ddl = metadataExplorerService.generateCreateTableDdl(dsConfig, targetTableName, converted.columns, propList.tableNameCn)
    var partitionPlan = tablePartitions.normalize(body?.partitionPlan, converted.columns, dsConfig.getEffectiveDatabaseType())
    ddl = tablePartitions.enrichConstraints(ddl, body?.sourceConstraints, dsConfig, partitionPlan).ddl
    return tablePartitions.apply(ddl, partitionPlan, dsConfig)
} catch (e) {
    exit 400, '未能生成建表语句：' + ddlFailureTip(e)
}
