{
  "properties": {},
  "id": "nifi_connection_probe_setting_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "74.海通探查数据源配置",
  "path": "/nifiProbeSettings",
  "description": "连接、字段、表探查共用登记源租户校验与托管 Hive 网关参数组装。通过可信租户读取登记配置，限定数据库与表，返回技术探查所需配置。",
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
import java.lang.IllegalArgumentException
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
