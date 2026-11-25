{
  "properties": {},
  "id": "nifi_canvas_template_source_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "39.海通模板来源节点",
  "path": "/nifiTemplateSource",
  "description": "使用已授权的数据源和已登记字段组装画布来源节点。兼容 JDBC、MRS Hive、FTP/SFTP 文件推送和 API 拉取。只读。",
  "parameters": [
    {
      "name": "datasource",
      "description": "已校验的数据源",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "tableAsset",
      "description": "登记表信息",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "columns",
      "description": "来源字段",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "sourceTableName",
      "description": "画布来源表名，用于旧推送文件路径",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiApiTemplateConfig' as apiTemplate
import '@/common/nifiTemplateHiveConnection' as hiveConnection
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as datasourceType
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Integer
import java.lang.Number
import java.lang.String
import java.util.ArrayList
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.regex.Pattern
import tableRead
import tenantRuntime

// @/common/nifiTemplateSource 组装已校验登记数据的来源节点。返回 manifestKey 和 config。
// 仅推送来源需要额外读取本租户的 ODS 文件存储绑定。API 配置复用既有函数。
var blank = value => value == null || (value + '').isBlank()
var first = (row, keys) => {
    for (key in keys) {
        if (!blank(row[key])) {
            return row[key] + ''
        }
    }
    return null
}
var asObject = value => {
    if (value instanceof Map) {
        return value
    }
    try {
        var parsed = blank(value) ? null : mapper.readValue(value + '', Map)
        return parsed instanceof Map ? parsed : {}
    } catch (failure) {
        return {}
    }
}
var port = (row, keys) => {
    for (key in keys) {
        var value = row[key]
        if (value instanceof Number) {
            return value.intValue()
        }
        if (!blank(value)) {
            try {
                return Integer.parseInt((value + '').trim())
            } catch (failure) {
                // 旧的次选字段可继续提供有效端口。
            }
        }
    }
    return null
}
var defaultText = (value, fallback) => value == null ? fallback : value
var typeOf = row => first(row, ['dbType', 'db_type', 'databaseType', 'database_type'])
var tableName = first(tableAsset, ['tableName', 'table', 'name', 'tableCode', 'tableNameCn'])
var source = datasource
var accessMode = first(source, ['accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode'])
var logicalId = null
if (!blank(accessMode) && ['receive', 'upload', 'push'].contains(accessMode.toLowerCase(Locale.ROOT))) {
    logicalId = first(source, ['tid', 'id', 'datasourceId', 'dataSourceId', 'dbId'])
    var registeredName = blank(sourceTableName) ? tableName : sourceTableName
    if (blank(logicalId) || blank(registeredName)) {
        throw new IllegalStateException('数据推送任务缺少来源数据源或来源表，无法定位 ODS 文件目录')
    }
    var binding = tableRead.from('dwm_center_layer_source_t', 'b', tenantRuntime.id())
    binding.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [
        tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0)])
    binding.where(tableRead.equal(tableRead.column('b', 'target_type', 'TEXT'), 'domain'))
    binding.where(tableRead.equal(tableRead.column('b', 'layer_code', 'TEXT'), 'fjghcc'))
    binding.where(tableRead.equal(tableRead.column('b', 'is_del', 'NUMBER'), 0))
    var id = tableRead.column('d', 'tid', 'TEXT')
    binding.where(tableRead.not(tableRead.isNull(id)))
    var matches = binding.rows({tid: id, dbType: tableRead.column('d', 'db_type', 'TEXT'),
        dbName: tableRead.column('d', 'db_name', 'TEXT'), jdbcURL: tableRead.column('d', 'jdbc_url', 'LOB'),
        username: tableRead.column('d', 'username', 'TEXT'), password: tableRead.column('d', 'password', 'TEXT'),
        poolCfg: tableRead.column('d', 'pool_cfg', 'LOB')},
        [tableRead.column('b', 'updated_time', 'TIME'), tableRead.column('b', 'tid', 'TEXT')], ['desc', 'desc'], 1, true)
    if (matches.isEmpty()) {
        throw new IllegalStateException('资源中心 ODS 域 fjghcc 未绑定可用的 FTP 数据源')
    }
    var storage = matches[0]
    source = {}
    source.putAll(asObject(storage.poolCfg))
    source.putAll(storage)
    var manifest = datasourceType.sourceManifest(typeOf(source), source)
    if (manifest != 'source.ftp' && manifest != 'source.sftp') {
        throw new IllegalStateException('资源中心 ODS 域 fjghcc 绑定的数据源不是 FTP/SFTP 类型')
    }
    var root = first(source, ['ftpPath', 'ftp_path', 'remotePath', 'path'])
    if (blank(root)) {
        throw new IllegalStateException('资源中心 ODS 域 fjghcc 的 FTP 根路径未配置')
    }
    root = root.trim().replace('\\', '/').replaceAll('/+$', '')
    if (!root.startsWith('/')) {
        root = '/' + root
    }
    var folderId = logicalId.trim().replaceAll('^/+|/+$', '')
    var folderTable = registeredName.trim().replace('\\', '/').replaceAll('^/+|/+$', '')
    if (folderId.isEmpty() || folderTable.isEmpty() || folderId.contains('..') || folderTable.contains('..')) {
        throw new IllegalArgumentException('来源数据源 ID 或表名不能用于构造 FTP 文件目录')
    }
    source.ftpPath = (root == '/' ? '' : root) + '/' + folderId + '/' + folderTable + '/'
}

var rawType = typeOf(source)
var manifest = datasourceType.sourceManifest(rawType, source)
var sourceId = first(source, ['tid', 'id', 'datasourceId', 'dataSourceId', 'dbId'])
var config = {registeredDatasourceId: sourceId, sourceDbId: sourceId,
    sourceTableId: first(tableAsset, ['tid', 'id', 'tableId'])}
var driverProperties = JdbcDriverPropertyResolver.resolve(source)
if (!driverProperties.isEmpty()) {
    config.jdbcProperties = driverProperties
}
if (manifest == 'source.ftp' || manifest == 'source.sftp') {
    var sftp = manifest == 'source.sftp'
    var ftpPort = port(source, ['ftpPort', 'ftp_port', 'port'])
    config.protocol = sftp ? 'sftp' : first(source, ['ftpProtocol', 'ftp_protocol', 'protocol'])
    for (option in ['ftpsImplicit', 'trustStorePath', 'trustStorePassword', 'nifiTrustStorePath', 'ftpRecursive', 'ftpPassiveMode']) {
        if (!blank(source[option])) {
            config[option] = source[option] + ''
        }
    }
    config.hostname = first(source, ['ftpHost', 'ftp_host', 'hostname', 'host'])
    config.port = ftpPort == null ? (sftp ? 22 : 21) : ftpPort
    config.username = first(source, ['ftpUsername', 'ftp_username', 'username'])
    config.password = first(source, ['ftpPassword', 'ftp_password', 'password'])
    config.remotePath = defaultText(first(source, ['ftpPath', 'ftp_path', 'remotePath', 'path']), '/')
    config.fileFilterRegex = defaultText(first(source, ['ftpFilePattern', 'ftp_file_pattern', 'fileFilterRegex', 'filePattern']), '.*')
    config.passiveMode = first(source, ['ftpPassiveMode', 'ftp_passive_mode', 'passiveMode'])
    config.putAll(BatchSchedulePolicy.markDefault(config))
    if (logicalId == null && !blank(tableName)) {
        config.fileFilterRegex = '(?i)^' + Pattern.quote(tableName) + '\\.(csv|json|xlsx|xls)$'
    }
    // 推送登记文件保留中文表头和工作表名，直接读取 FTP 的模板使用物理字段名。
    config.fileHeaderMode = logicalId == null ? 'physical' : 'registered-chinese'
    config.excelSheetName = first(tableAsset, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment', 'tableName', 'table', 'name', 'tableCode'])
    config.logicalSourceDatasourceId = logicalId
    config.pushStorageDomain = logicalId == null ? null : 'fjghcc'
} else if (manifest == 'source.api') {
    var selected = {}
    for (field in ['apiPullItems', 'api_pull_items', 'poolCfg', 'pool_cfg', 'apiUrl', 'api_url', 'url',
        'apiMethod', 'api_method', 'method', 'apiBody', 'api_body', 'requestBody', 'request_body', 'connectTimeout',
        'connect_timeout', 'connectionTimeout', 'readTimeout', 'read_timeout', 'apiTimeoutSeconds', 'api_timeout_seconds']) {
        if (source.containsKey(field)) {
            selected[field] = source[field]
        }
    }
    config.putAll(apiTemplate(selected, tableName))
} else {
    for (option in ['apiKey', 'authMethod', 'bootstrapServers', 'credentialRef', 'defaultIndex', 'fileFilterRegex',
        'groupId', 'kerberosPrincipal', 'kerberosServiceName', 'maxPollRecords', 'messageFormat', 'minioAccessKey',
        'minioBucket', 'minioEndpoint', 'minioFilePattern', 'minioPrefix', 'minioSecretKey', 'nodes', 'path', 'protocol',
        'queryJson', 'rowStart', 'rowStop', 'saslMechanism', 'schemaRegistryUrl', 'schemaRequired', 'securityProtocol',
        'topic', 'trustStorePath', 'version', 'extraParams', 'compatibleMode']) {
        if (source[option] != null) {
            config[option] = source[option]
        }
    }
    config.host = first(source, ['host', 'dbMetaIp'])
    config.port = port(source, ['port', 'dbMetaPort'])
    config.database = first(source, ['database', 'dbName', 'dbMetaDbName'])
    config.username = first(source, ['username', 'dbMetaUser'])
    config.password = first(source, ['password', 'dbMetaPassword'])
    config.dbType = datasourceType.configType(rawType, source)
    if (manifest == 'source.minio') {
        // 登记使用文件名通配符，画布使用正则；显式正则优先，与登记探查的匹配语义一致。
        var registeredFileRegex = first(source, ['fileFilterRegex'])
        var registeredFileGlob = first(source, ['minioFilePattern', 'filePattern'])
        config.minioFilePattern = !blank(registeredFileRegex) ? '(?i)' + registeredFileRegex
            : (blank(registeredFileGlob) ? '' : '(?i)^' + Pattern.quote(registeredFileGlob)
                .replace('*', '\\E.*\\Q').replace('?', '\\E.\\Q') + '$')
        config.minioFilePatternScope = 'FILE_NAME'
    }
    if (manifest == 'source.oracle') {
        var endpointMode = first(source, ['connectionType', 'connection_type', 'jdbcType', 'jdbc_type'])
        var normalized = endpointMode == null ? '' : endpointMode.trim().toUpperCase(Locale.ROOT).replaceAll('[\\s_-]+', '')
        var mode = normalized == 'SID' ? 'SID' : (['SERVICE', 'SERVICENAME'].contains(normalized) ? 'SERVICE_NAME' : null)
        if (mode != null) {
            config.connectionType = mode
            config.jdbcType = mode
        }
        var service = first(source, ['sid', 'serviceName', 'service_name', 'service', 'database', 'dbName'])
        if (service != null) {
            config.sid = service
            config.serviceName = service
            config.database = service
        }
    }
    if (manifest == 'source.kingbase') {
        var compatibility = first(source, ['compatibleMode', 'compatible_mode'])
        if (compatibility != null) {
            config.compatibleMode = compatibility
        }
    }
    if (manifest == 'source.oceanbase') {
        config.compatibleMode = config.dbType == 'OCEANBASE_ORACLE' ? 'ORACLE' : 'MYSQL'
        var tenant = first(source, ['tenant', 'obTenant', 'ob_tenant'])
        var clusterId = first(source, ['clusterId', 'cluster_id'])
        if (tenant != null) { config.tenant = tenant }
        if (clusterId != null) { config.clusterId = clusterId }
    }
    config.gaussdbVariant = first(source, ['gaussdbVariant', 'gaussdb_variant'])
    config.transportMode = first(source, ['transportMode'])
    config.httpPath = first(source, ['httpPath'])
    config.putAll(hiveConnection(source, config, rawType, false))
    if (!'open-source'.equalsIgnoreCase(config.hiveConnectionMode)
        && (config.hiveProfile != null || 'server-managed-mrs'.equalsIgnoreCase(config.metadataAccessMode)
            || 'huawei-mrs'.equalsIgnoreCase(config.hiveConnectionMode))) {
        config.remove('password')
    }
    if ('KERBEROS'.equalsIgnoreCase(config.authMode)) {
        config.remove('username')
        config.remove('password')
    }
    config.useSSL = false
    config.serverTimezone = 'Asia/Shanghai'
    config.defaultFetchSize = 1000
    config.putAll(BatchSchedulePolicy.markDefault(config))
    config.putAll({syncMode: 'FULL_THEN_INCR', initialStrategy: 'START_AT_BEGINNING', lookbackSeconds: 5,
        safetyLagSeconds: 1, batchSize: 5000, maxRowsPerRun: 20000, softDeleteEnabled: false, deleteReconcileStrategy: 'NONE'})
    // 只用登记环节明确指定的时间字段。主键与任意日期字段均不能替代增量水位。
    var governance = asObject(tableAsset.fieldGovernanceConfig)
    var candidates = [first(governance, ['timestampField', 'timestamp_field'])]
    for (field in (governance.fields == null ? [] : governance.fields)) {
        var flags = [field.isTimestampField, field.is_timestamp_field, field.isTimestamp]
        var explicit = !flags.filter(flag => flag != null && ('true'.equalsIgnoreCase((flag + '').trim())
            || (flag + '').trim() == '1')).isEmpty()
        var roles = field.timeRoles instanceof List ? new ArrayList(field.timeRoles) : [field.timeRoles]
        roles.add(first(field, ['timeRole', 'time_role']))
        if (explicit || !roles.filter(role => role != null && 'timestamp'.equalsIgnoreCase((role + '').trim())).isEmpty()) {
            candidates.add(first(field, ['columnName', 'column_name', 'fieldName', 'field_name', 'name']))
        }
    }
    for (candidate in candidates) {
        if (blank(candidate)) {
            continue
        }
        for (column in columns) {
            var name = column instanceof Map ? first(column, ['columnName', 'column_name', 'fieldName', 'field_name', 'name']) : column.columnName
            if (!blank(name) && candidate.trim().equalsIgnoreCase(name.trim())) {
                config.incrementalColumn = name
                break
            }
        }
        if (config.incrementalColumn != null) {
            break
        }
    }
}
config.table = tableName
config.sourceColumns = columns
return {manifestKey: manifest, config: config}
