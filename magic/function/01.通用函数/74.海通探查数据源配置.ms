{
  "properties": {},
  "id": "nifi_connection_probe_setting_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "74.海通探查数据源配置",
  "path": "/nifiProbeSettings",
  "description": "连接探查使用当前租户的登记源与托管 Hive 参数；画布直连物化建表使用此函数从受支持的 JDBC URL 解析实际目标库。",
  "parameters": [
    { "name": "manifest", "dataType": "Object", "type": "java.lang.Object" },
    { "name": "config", "dataType": "Object", "type": "java.lang.Object" },
    { "name": "operation", "dataType": "Object", "type": "java.lang.Object" },
    { "name": "table", "dataType": "Object", "type": "java.lang.Object" },
    { "name": "keyword", "dataType": "Object", "type": "java.lang.Object" },
    { "name": "limit", "dataType": "Object", "type": "java.lang.Object" }
  ],
  "options": []
}
================================
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.mongo.MongoDatasource
import java.lang.IllegalArgumentException
import java.net.URI
import nifiConnectionProbe
import tenantRuntime

// @/common/nifiProbeSettings 为连接、字段和表探查准备当前租户登记配置。
// 托管华为数仓只传网关操作和库表标识，由服务端持有协议凭据。

var first = (source, keys) => {
    for (key in keys) {
        var value = source[key] == null ? '' : (source[key] + '').trim()
        if (value != '') {
            return value
        }
    }
    return ''
}

// MongoDB 按节点填写的数据库名创建集合。预览只需要库名，执行时才验证连接参数。
if (operation == 'mongoDdl' || operation == 'mongoDdlPreview') {
    if (tenantRuntime.id() == null) { exit 401, '无法识别当前租户，请重新登录' }
    var database = first(config, ['database'])
    if (database == '' || !database.matches('[\\p{L}\\p{N}_-]{1,63}')) {
        exit 400, '请填写有效的 MongoDB 目标数据库名'
    }
    var target = new DataSourceConfig()
    target.setDatabaseType(DatabaseType.MONGODB)
    target.setDatabase(database)
    if (operation == 'mongoDdl') {
        var port = first(config, ['port'])
        target.setHost(first(config, ['host']))
        target.setUsername(first(config, ['username']))
        target.setPassword(config.password == null ? '' : config.password + '')
        target.setConnectorProperties({authSource: first(config, ['authSource']), ssl: config.ssl == true})
        try {
            if (port != '') { target.setPort(port::int) }
            MongoDatasource.settings(target)
        } catch (error) {
            exit 400, 'MongoDB 目标连接参数有误，请检查主机、端口和账号配置'
        }
    }
    return target
}

// 画布直连建表以 JDBC URL 中的库为准。仅接受明确的单主机、单库地址及常用连接选项。
if (operation == 'jdbcDdl') {
    if (tenantRuntime.id() == null) { exit 401, '无法识别当前租户，请重新登录' }
    var url = first(config, ['jdbcUrl'])
    if (!url.startsWith('jdbc:') || url.length() > 2048) { exit 400, '请填写有效的目标 JDBC URL' }
    var address = null
    try { address = URI.create(url.substring(5)) }
    catch (error) { exit 400, '目标 JDBC URL 格式有误' }
    var scheme = address.getScheme()
    var mappedTypes = {mysql:'mysql', mariadb:'mariadb', postgresql:'postgresql', kingbase8:'kingbase',
        highgo:'highgo', vertica:'vertica'}
    var type = mappedTypes.get(scheme)
    if (type == null) { exit 400, '此 JDBC 连接格式暂不支持直连建表，请选择已登记的目标库' }
    var path = address.getPath()
    if (address.getHost() == null || address.getUserInfo() != null || address.getFragment() != null
        || path == null || !path.matches('/[A-Za-z0-9_\\p{L}-]{1,128}')) {
        exit 400, '请在 JDBC URL 中填写单个主机及明确的目标数据库名，例如 jdbc:mysql://主机:3306/库名'
    }
    var schema = null
    var query = address.getRawQuery()
    if (query != null && query != '') {
        var optionNames = []
        var allowed = ['usessl','requiressl','verifyservercertificate','sslmode','ssl',
            'servertimezone','connectiontimezone','characterencoding','useunicode','connecttimeout',
            'sockettimeout','allowpublickeyretrieval','currentschema']
        for (entry in query.split('&')) {
            var keys = entry.split('=', 2)
            if (keys.length != 2 || !keys[1].matches('[A-Za-z0-9_/%+.:~-]+')) {
                exit 400, '目标 JDBC URL 包含无法识别的连接参数'
            }
            var option = keys[0].toLowerCase()
            if (!allowed.contains(option) || optionNames.contains(option)) {
                exit 400, '直连建表遇到不支持或重复的 JDBC 参数，请检查连接设置或使用已登记的目标库'
            }
            optionNames.add(option)
            if (option == 'currentschema') {
                if (!['postgresql', 'kingbase8', 'highgo'].contains(scheme) || !keys[1].matches('[A-Za-z_][A-Za-z0-9_]{0,127}')) {
                    exit 400, '请在 JDBC URL 中指定一个有效的 currentSchema'
                }
                schema = keys[1]
            }
        }
    }
    var requestedType = first(config, ['dbType']).toLowerCase()
    if (scheme == 'mysql' && ['doris', 'starrocks', 'oceanbasemysql'].contains(requestedType)) { type = requestedType }
    var target = new DataSourceConfig()
    target.setDatabaseType(DatabaseType.fromCode(type))
    target.setHost(address.getHost())
    if (address.getPort() > 0) { target.setPort(address.getPort()) }
    target.setDatabase(path.substring(1))
    target.setSchema(schema)
    target.setJdbcUrl(url)
    target.setUsername(first(config, ['username']))
    target.setPassword(config.password == null ? '' : config.password + '')
    return target
}
var registeredHive = manifest == 'source.hive' || manifest == 'sink.hive'
    || (manifest == 'sink.jdbc' && 'HIVE'.equalsIgnoreCase(config.dbType == null ? null : config.dbType + ''))
if (!registeredHive) {
    return {config: config, gateway: null}
}

var datasourceId = first(config, ['registeredDatasourceId', 'selectedDatabaseId', 'datasourceId', 'dbId',
    'sourceDbId', 'targetDbId', 'dataSourceId'])
var registered = {}
if (datasourceId != '') {
    // 按可信租户查找登记记录，客户端信息不得为已登记连接覆盖托管方式或凭据。
    registered = connectionProperties.resolve(tenantRuntime.id(), datasourceId)
    if (registered.isEmpty() || !'HIVE'.equalsIgnoreCase(first(registered, ['dbType']))) {
        throw new IllegalArgumentException('当前租户中不存在所选 Hive 数据源')
    }
    for (key in ['hiveProfile', 'metadataAccessMode', 'hiveConnectionMode', 'jdbcUrl', 'jdbcURL',
        'host', 'port', 'database', 'username', 'password']) {
        config.remove(key)
    }
    config.putAll(registered)
    config.registeredDatasourceId = datasourceId
}

var profile = first(config, ['hiveProfile', 'hive_profile'])
var mode = first(config, ['metadataAccessMode', 'metadata_access_mode'])
var connectionMode = first(config, ['hiveConnectionMode', 'hive_connection_mode'])
if ('jdbc'.equalsIgnoreCase(mode) || 'open-source'.equalsIgnoreCase(connectionMode)) {
    return {config: config, gateway: null}
}
if (profile == '' && !'server-managed-mrs'.equalsIgnoreCase(mode) && !'huawei-mrs'.equalsIgnoreCase(connectionMode)) {
    return {config: config, gateway: null}
}
if (operation == 'columns' && (table == null || table == '')) {
    return {config: config, gateway: null}
}

var database = ''
if (datasourceId == '') {
    // 首次连接可以测试服务端默认配置，只接受用户选择的库名。
    if (!'default'.equalsIgnoreCase(profile)) {
        throw new IllegalArgumentException('华为 MRS Hive 节点缺少已登记数据源标识')
    }
    database = first(config, ['database', 'dbName'])
} else {
    var storedMode = first(registered, ['metadataAccessMode', 'metadata_access_mode'])
    var storedConnectionMode = first(registered, ['hiveConnectionMode', 'hive_connection_mode'])
    if (!'server-managed-mrs'.equalsIgnoreCase(storedMode) && !'huawei-mrs'.equalsIgnoreCase(storedConnectionMode)
        && first(registered, ['hiveProfile']) == '') {
        throw new IllegalArgumentException('已登记数据源不是服务端托管的华为 MRS Hive')
    }
    database = first(registered, ['database', 'dbName', 'dbMetaDbName', 'schema'])
}
if (database == '') {
    throw new IllegalArgumentException('华为 MRS Hive 配置缺少数据库名')
}
var request = {action: operation, contract: operation == 'columns' ? 'gateway' : 'nifi', database: database}
if (table != null && table != '') {
    // 带库名的表只允许使用已校验的库，字段结果由网关按原始列标签返回。
    var name = nifiConnectionProbe.tableIdentifier(table)
    if (name.schema != null && !database.equalsIgnoreCase(name.schema)) {
        throw new IllegalArgumentException('Hive 表所属数据库与所选数据源不一致')
    }
    request.table = name.table
}
if (keyword != null && !keyword.isBlank()) {
    request.keyword = keyword
}
if (limit != null) {
    request.limit = limit
}
return {config: config, gateway: request}
