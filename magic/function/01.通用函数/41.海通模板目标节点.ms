{
  "properties": {},
  "id": "nifi_canvas_template_sink_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "41.海通模板目标节点",
  "path": "/nifiTemplateSink",
  "description": "使用已授权的目标登记表和字段生成目标节点配置，保留 JDBC、Hive 写入模式、稳定业务键和表名大小写规则。",
  "parameters": [
    {
      "name": "datasource",
      "description": "已校验的目标数据源",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "tableAsset",
      "description": "目标表登记信息及兼容字段",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "columns",
      "description": "目标字段",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "explicitTableName",
      "description": "全画布使用目录约定的目标表名",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiTemplateHiveConnection' as hiveConnection
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as datasourceType
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver
import java.lang.Integer
import java.lang.Number
import java.util.Locale
import java.util.Map

// @/common/nifiTemplateSink 组装已校验的目标登记表及字段的写入配置。无写入或探查。
var first = (row, keys) => {
    for (key in keys) {
        var value = row[key]
        if (value != null && !(value + '').isBlank()) {
            return value + ''
        }
    }
    return null
}
var rawType = first(datasource, ['dbType', 'db_type', 'databaseType', 'database_type'])
var type = datasourceType.sinkType(rawType, datasource)
var config = {
    dbType: type,
    targetDbId: first(datasource, ['tid', 'id', 'datasourceId', 'dataSourceId', 'dbId']),
    targetTableId: first(tableAsset, ['tid', 'id', 'tableId']),
    gaussdbVariant: first(datasource, ['gaussdbVariant', 'gaussdb_variant'])
}
var props = JdbcDriverPropertyResolver.resolve(datasource)
if (!props.isEmpty()) {
    config.jdbcProperties = props
}
var port = null
for (key in ['port', 'dbMetaPort']) {
    var value = datasource[key]
    if (value instanceof Number) {
        port = value.intValue()
        break
    }
    if (value != null && !(value + '').isBlank()) {
        try {
            port = Integer.parseInt((value + '').trim())
            break
        } catch (failure) {
            // 兼容历史登记中的次选端口键。
        }
    }
}
config.putAll(hiveConnection(datasource, {dbType: type, port: port, host: first(datasource, ['host', 'dbMetaIp']),
    database: first(datasource, ['database', 'dbName', 'dbMetaDbName'])}, rawType, true))
if (!'KERBEROS'.equalsIgnoreCase(config.authMode)) {
    config.username = first(datasource, ['username', 'dbMetaUser'])
    config.password = first(datasource, ['password', 'dbMetaPassword'])
}
var name = explicitTableName != null ? explicitTableName : first(tableAsset, ['tableName', 'targetTableName', 'table', 'name', 'tableCode', 'tableNameCn'])
var vendor = type.toUpperCase(Locale.ROOT)
var oracle = ['ORACLE', 'OCEANBASE_ORACLE', 'OCEANBASEORACLE'].contains(vendor)
var identifier = value => value.startsWith('"') && value.endsWith('"') ? value : value.toUpperCase(Locale.ROOT)
if (name != null) {
    name = name.trim()
    if ((oracle || ['DM', 'DAMENG'].contains(vendor)) && !name.contains('.')) {
        var schema = first(datasource, ['schema', 'defaultSchema', 'currentSchema'])
        name = (schema == null ? '' : identifier(schema.trim()) + '.') + identifier(name)
    }
}
config.table = name
config.targetColumns = columns
// 系统生成的 ODS_UUID 只标识一次入库，业务去重必须使用稳定主键或唯一字段。
var updateKeys = []
for (column in columns) {
    var columnName = column.columnName
    if (columnName == null || columnName.isBlank() || 'ODS_UUID'.equalsIgnoreCase(columnName)) {
        continue
    }
    var flags = column instanceof Map ? [column.primaryKey, column.primary_key, column.isPrimaryKey, column.is_primary_key,
        column.isPk, column.is_pk, column.isUnique, column.is_unique] : [column.primaryKey]
    if (!flags.filter(flag => flag != null && ('true'.equalsIgnoreCase((flag + '').trim()) || (flag + '').trim() == '1')).isEmpty()) {
        updateKeys.add(columnName)
    }
}
var hive = 'HIVE'.equalsIgnoreCase(type)
config.updateKeys = hive ? '' : updateKeys.join(',')
config.statementType = hive ? 'INSERT' : (oracle ? 'MERGE' : (updateKeys.isEmpty() ? 'INSERT' : 'UPSERT'))
if (hive) {
    config.hiveWriteMode = 'LINEWELL_HDFS'
} else {
    config.writerType = oracle ? 'LINEWELL_PUT_DATABASE_RECORD' : 'NIFI_PUT_DATABASE_RECORD'
}
return {manifestKey: hive ? 'sink.hive' : 'sink.jdbc', config: config}
