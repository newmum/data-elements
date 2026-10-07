{
  "properties": {},
  "id": "f93a8e653ac6413e89660f5d77d45769",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "11.根据表或目录获取建表申请模板",
  "createTime": 1782955344746,
  "updateTime": 1788451429685,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getTableTempalte",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"015c09572d174e89b9bea9c765ef72df\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"propList\": {\n    \"sourceTableId\": \"015c09572d174e89b9bea9c765ef72df\",\n    \"dbId\": \"648b94f1a6a745a3a36149a8788f36f3\",\n    \"nodeId\": \"数据中心节点ID\",\n    \"dbType\": \"mysql\",\n    \"tableNameCn\": \"公安重新探查新增线索表\",\n    \"tableName\": \"police_reexplore_new_case_clue\"\n  },\n  \"tableItems\": [\n    {\n      \"columnName\": \"clue_id\",\n      \"columnComment\": \"线索编号\",\n      \"dataType\": \"varchar\",\n      \"length\": 32,\n      \"primaryKey\": 1,\n      \"nullable\": 0\n    }\n  ]\n}",
  "description": "根据来源数据表或已登记目录获取物化建表模板（POST /ods/getTableTempalte）。只读接口；优先兼容旧目录 da_prop_t.sourceTableId，再直接读取 db_table_t/db_table_column_t，并以第四步 field_governance_config 中已登记的主键为默认主键；默认目标数据源优先读取已启用、数据组织为数据中心且数据源类型为 ODS 的首条数据源，旧资源中心 ODS 分层关联仅作为兼容回退。不依赖 ES，避免 ES 临时不可用导致建表弹框为空。用于数据接入-创建任务前的物化建表抽屉；使用请求参数 tid。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.根据表或目录获取建表申请模板
// 路由：POST /ods/getTableTempalte
// 业务说明：根据来源数据表或已登记目录获取物化建表模板（POST /ods/getTableTempalte）。只读接口；优先兼容旧目录 da_prop_t.sourceTableId，再直接读取 db_table_t/db_table_column_t，并以第四步 field_governance_config 中已登记的主键为默认主键；默认目标数据源优先读取已启用、数据组织为数据中心且数据源类型为 ODS 的首条数据源，旧资源中心 ODS 分层关联仅作为兼容回退。不依赖 ES，避免 ES 临时不可用导致建表弹框为空。用于数据接入-创建任务前的物化建表抽屉；使用请求参数 tid。
/*
 * 物化建表模板读取接口。
 * - 入参 tid 可以是 db_table_t.tid，也可以是旧目录 ID；
 * - 不再把 ES 作为必经链路，ES 不可用时也必须能从 MySQL 主表读取来源表和字段；
 * - 目标数据源默认使用 dataSourceType 字典下贴源层 ODS 分域已关联的数据源。
 */
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import tenantRuntime
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig
import com.linewell.dataelement.metautil.model.enums.DatabaseType
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService

var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
var first = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') {
            return row[key]
        }
    }
    return null
}
var toInt = (value, defaultValue) => {
    if (blank(value)) {
        return defaultValue
    }
    return value::int
}
var lower = (value) => blank(value) ? '' : text(value).toLowerCase()
var isPrimaryKey = (value) => {
    var normalized = text(value).trim().toLowerCase()
    return normalized == '1' || normalized == 'true' || normalized == 'yes'
}

var inputId = text(body?.tid).trim()
if (blank(inputId)) {
    exit 400, 'tid不能为空'
}
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录上下文无效，请重新登录'
}

// 兼容旧链路：如果传入的是目录 ID，则从 da_prop_t.sourceTableId 找来源表。
var catalogId = ''
var sourceTableId = ''
var catalogProp = db.table('da_prop_t').where()
    .eq('parent_id', inputId)
    .eq('prop_name', 'sourceTableId')
    .eq('is_del', 0)
    .selectOne()
if (catalogProp != null) {
    catalogId = inputId
    sourceTableId = text(first(catalogProp, ['propValue', 'prop_value']))
}
if (blank(sourceTableId)) {
    sourceTableId = inputId
}

var sourceTable = db.table('db_table_t').where()
    .eq('tid', sourceTableId)
    .eq('is_del', 0)
    .selectOne()
if (sourceTable == null) {
    exit 404, '来源表不存在'
}

// 数据源登记第一步保存的 SSWL（所属网络）决定物化建表的默认接入网络。
// 抽屉前端据此自动选中该网络下已启用的默认数据接入节点。
var sourceDatasource = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId)
    .eq('tid', first(sourceTable, ['datasourceId', 'datasource_id']))
    .eq('is_del', 0)
    .selectOne()
var sourceNetworkCode = sourceDatasource == null ? '' : text(first(sourceDatasource, [
    'storageDomain', 'storage_domain', 'SSWL', 'networkDomain', 'network_domain'
])).trim()
// 物化抽屉需要回显登记第一步选择的接入方式。该值在数据源主表的
// pool_cfg 中保存；历史记录也可能已提升为主表字段，因此两处均兼容读取。
var sourceDatasourceName = sourceDatasource == null ? '' : text(first(sourceDatasource, [
    'dbName', 'db_name', 'datasourceName', 'datasource_name', 'name'
])).trim()
var sourcePoolCfg = {}
if (sourceDatasource != null) {
    try {
        sourcePoolCfg = jsons.toJSONObject(text(first(sourceDatasource, ['poolCfg', 'pool_cfg'])))
    } catch (e) {
        sourcePoolCfg = {}
    }
}
var sourceAccessModeCode = sourceDatasource == null ? '' : text(first(sourceDatasource, [
    'accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode'
])).trim()
if (blank(sourceAccessModeCode)) {
    sourceAccessModeCode = text(first(sourcePoolCfg, [
        'accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode'
    ])).trim()
}
var sourceAccessModeLabels = {
    explore: '数据抽取方式',
    extract: '数据抽取方式',
    extraction: '数据抽取方式',
    receive: '数据推送方式',
    push: '数据推送方式',
    capture: '数据抓取方式',
    pull: '数据抓取方式',
    upload: '数据上报方式',
    report: '数据上报方式'
}
var sourceAccessMode = sourceAccessModeLabels[lower(sourceAccessModeCode)] || sourceAccessModeCode
if (blank(sourceAccessMode)) {
    // 可连接的数据源在早期登记中没有保存 accessMode 时，沿用其既有的默认抽取语义。
    sourceAccessMode = '数据抽取方式'
}

// 字段字典关联和强制对标均由登记第四步写入来源表治理快照。物化模板在此统一派生
// <源字段>_cn，确保抽屉、DDL、目标元数据和后续 NiFi 映射使用同一字段口径。
var parseGovernanceConfig = (value) => {
    if (value == null || blank(value)) {
        return {}
    }
    try {
        return value instanceof Map ? value : jsons.toJSONObject(text(value))
    } catch (e) {
        return {}
    }
}
// New registrations persist this snapshot on db_table_t. Historical registrations
// persist it on the generated catalog's properties, linked back by sourceTableId.
// Read both locations so a re-opened production table keeps its dictionary rules.
var configHasFields = (config) => config != null && config.fields != null && config.fields.size() > 0
var governanceConfigFromProperties = (properties) => {
    var fallback = {}
    for (property in (properties == null ? [] : properties)) {
        var propertyName = text(first(property, ['propName', 'prop_name'])).trim().toLowerCase()
        if (propertyName != 'fieldgovernanceconfig' && propertyName != 'field_governance_config'
            && propertyName != 'fieldgovernancecfg') {
            continue
        }
        var candidate = parseGovernanceConfig(first(property, ['propValue', 'prop_value']))
        if (configHasFields(candidate)) {
            return candidate
        }
        fallback = candidate
    }
    return fallback
}
var governanceConfigOf = (table) => {
    var persisted = parseGovernanceConfig(first(table, [
        'fieldGovernanceConfig', 'field_governance_config', 'fieldGovernanceCfg'
    ]))
    if (configHasFields(persisted)) {
        return persisted
    }
    var tableId = text(first(table, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(tableId)) {
        return persisted
    }
    var direct = governanceConfigFromProperties(db.table('da_prop_t').where()
        .eq('parent_id', tableId)
        .eq('is_del', 0)
        .select())
    if (configHasFields(direct)) {
        return direct
    }
    // Step 4 historically wrote a catalog property set. Locate that catalog by
    // its sourceTableId property, then read the sibling governance snapshot.
    var sourceLinks = db.table('da_prop_t').where()
        .eq('prop_name', 'sourceTableId')
        .eq('prop_value', tableId)
        .eq('is_del', 0)
        .select()
    for (link in sourceLinks) {
        var catalogId = text(first(link, ['parentId', 'parent_id'])).trim()
        if (blank(catalogId)) { continue }
        var catalogConfig = governanceConfigFromProperties(db.table('da_prop_t').where()
            .eq('parent_id', catalogId)
            .eq('is_del', 0)
            .select())
        if (configHasFields(catalogConfig)) {
            return catalogConfig
        }
    }
    return configHasFields(direct) ? direct : persisted
}
var safeIdentifier = (value) => text(value).trim().matches('^[A-Za-z_][A-Za-z0-9_$]*$')
var isForceStandardEnabled = (relation) => {
    if (relation == null) { return false }
    var value = text(relation.forceStandardEnabled).trim().toLowerCase()
    var sourceType = text(relation.sourceType).trim().toLowerCase()
    var mode = text(relation.mode).trim().toLowerCase()
    // Fixed enumerations use the same generated <code>_cn output contract as
    // mandatory standards, including older snapshots that only persist enum.
    return value == 'true' || value == '1' || value == 'yes'
        || sourceType == 'standard' || mode == 'standard'
        || sourceType == 'enum' || mode == 'enum'
}
var mandatoryStandardMappings = (relation) => {
    var result = []
    if (!isForceStandardEnabled(relation)) { return result }
    var values = relation.forceStandardValueMappings == null ? [] : relation.forceStandardValueMappings
    if (values.size() == 0) { values = relation.enumItems == null ? [] : relation.enumItems }
    var seen = {}
    for (value in values) {
        var code = text(first(value, ['sourceCode', 'sourceValue', 'value', 'code'])).trim()
        var label = text(first(value, ['standardLabel', 'label', 'sourceLabel', 'name'])).trim()
        if (!blank(code) && !blank(label) && seen[code] == null) {
            result.add({ code: code, label: label })
            seen[code] = true
        }
    }
    return result
}
// The column registration is the authoritative, minimal contract for the
// materialization template. A checked dictionary association is persisted as
// enable_code_table/code_table_id, so template generation does not need to
// inspect the dictionary's physical key, label or join conditions.
var registeredDictionaryRelations = (table) => {
    var result = {}
    var tableId = text(first(table, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(tableId)) { return result }
    var columns = db.table('db_table_column_t').where()
        .eq('table_id', tableId)
        .eq('is_del', 0)
        .select()
    for (column in columns) {
        var sourceName = text(first(column, ['columnName', 'column_name'])).trim()
        var enabled = text(first(column, ['enableCodeTable', 'enable_code_table', 'isDict', 'is_dict'])).trim().toLowerCase()
        var dictionaryId = text(first(column, ['codeTableId', 'code_table_id', 'dictId', 'dict_id'])).trim()
        var bound = enabled == '1' || enabled == 'true' || enabled == 'yes' || !blank(dictionaryId)
        if (bound && !blank(sourceName) && !sourceName.toLowerCase().endsWith('_cn')) {
            result[sourceName.toLowerCase()] = {
                relationKind: 'DICTIONARY',
                dictionaryTableId: dictionaryId
            }
        }
    }
    return result
}
var dictionaryRelations = (table) => {
    var registered = registeredDictionaryRelations(table)
    if (registered.size() > 0) {
        return registered
    }
    // Fallback only for historical registrations that predate the per-column
    // code-table fields. This fallback deliberately does not block template
    // generation on a dictionary key/label configuration being incomplete.
    var config = governanceConfigOf(table)
    var result = {}
    for (field in (config.fields == null ? [] : config.fields)) {
        var relation = first(field, ['dictionaryRelation', 'dictionary_relation', 'dictionaryConfig', 'dictionary_config'])
        var sourceName = text(first(field, ['columnName', 'column_name', 'fieldName', 'field_name'])).trim()
        var sourceType = text(relation == null ? '' : relation.sourceType).trim().toLowerCase()
        var standardValues = mandatoryStandardMappings(relation)
        if (relation != null && text(relation.enabled).toLowerCase() != 'false'
            && safeIdentifier(sourceName) && standardValues.size() > 0) {
            result[sourceName.toLowerCase()] = {
                relationKind: 'MANDATORY_STANDARD',
                standardValues: standardValues
            }
        } else if (relation != null && text(relation.enabled).toLowerCase() != 'false' && sourceType != 'enum'
            && !blank(sourceName)) {
            result[sourceName.toLowerCase()] = { relationKind: 'DICTIONARY' }
        }
    }
    return result
}
var appendDictionaryTranslationColumns = (items, table) => {
    var relations = dictionaryRelations(table)
    var existing = {}
    for (item in items) {
        existing[text(item.columnName).toLowerCase()] = true
    }
    var result = []
    for (item in items) {
        result.add(item)
        var sourceName = text(item.columnName).trim()
        var translationName = sourceName + '_cn'
        var relation = relations[sourceName.toLowerCase()]
        if (!sourceName.toLowerCase().endsWith('_cn') && relation != null && existing[translationName.toLowerCase()] == null) {
            result.add({
                columnName: translationName,
                columnComment: text(item.columnComment).trim() + (relation.relationKind == 'MANDATORY_STANDARD' ? '标准名称' : '翻译'),
                dataType: 'varchar',
                columnType: 'varchar(255)',
                length: 255,
                precisionLength: 0,
                scale: 0,
                primaryKey: 0,
                nullable: 1,
                defaultValue: null,
                ordinalPosition: result.size(),
                autoIncrement: 0,
                indexed: 0,
                isUnique: 0,
                dictionaryTranslationField: true,
                dictionarySourceField: sourceName
            })
            existing[translationName.toLowerCase()] = true
        }
    }
    return result
}

var mapDataType = (sourceType, columnType, lengthValue) => {
    var t = lower(sourceType)
    var ct = lower(columnType)
    if (blank(t)) {
        t = ct
    }
    if (t.indexOf('char') >= 0 || t == 'string' || t == 'keyword') {
        return 'varchar'
    }
    if (t == 'text' || t == 'clob' || t.indexOf('longvarchar') >= 0) {
        return 'text'
    }
    if (t == 'number' || t == 'numeric' || t == 'decimal' || t == 'float' || t == 'double' || t == 'real') {
        return 'decimal'
    }
    if (t == 'bigint' || t == 'long') {
        return 'bigint'
    }
    if (t == 'int' || t == 'integer' || t == 'smallint' || t == 'tinyint') {
        return 'int'
    }
    if (t == 'date') {
        return 'date'
    }
    if (t == 'datetime' || t == 'timestamp' || t.indexOf('time') >= 0) {
        return 'datetime'
    }
    if (t == 'boolean' || t == 'bool') {
        return 'tinyint'
    }
    if (t.indexOf('blob') >= 0 || t == 'binary' || t == 'varbinary') {
        return 'blob'
    }
    if (t == 'json' || t == 'object' || t == 'array') {
        return 'json'
    }
    return 'varchar'
}
// 物化建表必须以已登记数据源的实际字段定义为准。登记快照早期导入的数据
// 可能只保留“字符串”等展示类型并且 length 为 0；直接用快照会导致每列都回退到 255。
// 探查不可用时仍回退到快照，避免临时网络故障阻断建表抽屉打开。
// 连接细节由 datasourceConnectionConfig 从 pool_cfg 解密并统一返回；
// db_datasource_t 只保留 jdbc_url 等登记字段，不能假定 host/port 是直接列。
var dataSourceConfigOf = (source, connection) => {
    var config = new DataSourceConfig()
    var typeCode = first(source, ['dbType', 'db_type', 'databaseType', 'database_type'])
    try {
        config.setDatabaseType(blank(typeCode) ? DatabaseType.MYSQL : DatabaseType.fromCode(lower(typeCode)))
    } catch (e) {
        config.setDatabaseType(DatabaseType.MYSQL)
    }
    var host = first(connection, ['host', 'dbMetaIp']) || first(source, ['host', 'dbIp', 'db_ip'])
    var port = first(connection, ['port', 'dbMetaPort']) || first(source, ['port', 'dbPort', 'db_port'])
    var databaseName = first(connection, ['database', 'dbName', 'dbMetaDbName']) || first(source, ['database', 'dbName', 'db_name', 'dbMetaDbName'])
    var username = first(connection, ['username', 'dbMetaUser']) || first(source, ['username', 'dbUser', 'db_user', 'dbMetaUser'])
    var password = first(connection, ['password', 'dbMetaPassword']) || first(source, ['password', 'dbPassword', 'db_password', 'dbMetaPassword'])
    config.setHost(blank(host) ? '' : text(host))
    config.setPort(blank(port) ? config.getDatabaseType().getDefaultPort() : toInt(port, config.getDatabaseType().getDefaultPort()))
    config.setDatabase(text(databaseName))
    config.setUsername(text(username))
    config.setPassword(text(password))
    // 复用测试连接时登记的 JDBC URL，兼容 Oracle Service Name、达梦 Schema 等连接形式。
    config.setJdbcUrl(first(connection, ['jdbcURL', 'jdbcUrl']) || first(source, ['jdbcURL', 'jdbcUrl', 'jdbc_url']))
    config.setSchema(first(connection, ['schema', 'defaultSchema', 'currentSchema']) || first(source, ['schema', 'defaultSchema', 'default_schema', 'currentSchema', 'schemaName', 'schema_name']))
    return config
}
var sourcePhysicalColumns = (source, tableName) => {
    var result = []
    if (source == null || blank(tableName)) {
        return result
    }
    try {
        // 连接参数从统一配置解析，避免直接使用数据库中可能已加密的密码字段。
        var connection = datasourceConnectionConfig('read', first(source, ['tid']), null, false)
        var physicalColumns = metadataExplorerService.getColumns(dataSourceConfigOf(source, connection), tableName)
        for (physical in (physicalColumns == null ? [] : physicalColumns)) {
            if (!blank(physical.columnName)) {
                // MetadataExplorer 返回的是 Java ColumnInfo；后续模板逻辑按 Map 的
                // 多字段兼容读取（first）工作，故先转为普通对象，不能直接混用两者。
                result.add({
                    columnName: physical.columnName,
                    columnComment: physical.columnComment,
                    dataType: physical.dataType,
                    columnType: physical.columnType,
                    length: physical.length,
                    precision: physical.precision,
                    scale: physical.scale,
                    nullable: physical.nullable,
                    primaryKey: physical.primaryKey,
                    ordinalPosition: physical.ordinalPosition
                })
            }
        }
    } catch (e) {
        // 模板查询保持可用：探查失败时由登记快照和 normalizeLength 的既有逻辑兜底。
    }
    return result
}
var normalizeLength = (targetType, lengthValue, precisionValue) => {
    var lengthNum = toInt(lengthValue, 0)
    var precisionNum = toInt(precisionValue, 0)
    if (targetType == 'varchar') {
        return lengthNum > 0 ? lengthNum : 255
    }
    if (targetType == 'decimal') {
        return precisionNum > 0 ? precisionNum : (lengthNum > 0 ? lengthNum : 18)
    }
    if (targetType == 'int') {
        return lengthNum > 0 ? lengthNum : 10
    }
    if (targetType == 'bigint') {
        return lengthNum > 0 ? lengthNum : 20
    }
    return lengthNum
}
var normalizeNullable = (value) => {
    if (value == null || blank(value)) {
        return 1
    }
    var normalized = text(value).trim().toLowerCase()
    if (normalized == 'false' || normalized == 'no' || normalized == '0') {
        return 0
    }
    return 1
}
var materializationTimeType = (dbType) => {
    var code = text(dbType).trim().toLowerCase()
    if (code.indexOf('oceanbase') >= 0 || code == 'oracle' || code == 'dameng' || code == 'dm') {
        return 'date'
    }
    if (code == 'postgresql' || code == 'postgres' || code == 'kingbase' || code == 'kingbase8'
        || code == 'gaussdb' || code == 'opengauss' || code == 'gauss'
        || code == 'vertica' || code == 'hetu' || code == 'trino' || code == 'presto'
        || code == 'hive') {
        return 'timestamp'
    }
    if (code == 'sqlserver') {
        return 'datetime2'
    }
    return 'datetime'
}
var odsSystemColumns = (dbType) => {
    var timeType = materializationTimeType(dbType)
    return [
    {
        columnName: 'ODS_UUID',
        columnComment: 'ODS主键',
        dataType: 'varchar',
        columnType: 'varchar',
        length: 32,
        precisionLength: 0,
        scale: 0,
        primaryKey: 1,
        nullable: 0,
        defaultValue: null,
        ordinalPosition: 0,
        autoIncrement: 0,
        indexed: 1,
        isUnique: 1,
        odsSystemColumn: true
    },
    {
        columnName: 'ODS_RKSJ',
        columnComment: 'ODS入库时间',
        dataType: timeType,
        columnType: timeType,
        length: 0,
        precisionLength: 0,
        scale: 0,
        primaryKey: 0,
        nullable: 0,
        defaultValue: null,
        ordinalPosition: 0,
        autoIncrement: 0,
        indexed: 0,
        isUnique: 0,
        odsSystemColumn: true
    },
    {
        columnName: 'ODS_GXSJ',
        columnComment: 'ODS更新时间',
        dataType: timeType,
        columnType: timeType,
        length: 0,
        precisionLength: 0,
        scale: 0,
        primaryKey: 0,
        nullable: 0,
        defaultValue: null,
        ordinalPosition: 0,
        autoIncrement: 0,
        indexed: 0,
        isUnique: 0,
        odsSystemColumn: true
    }
    ]
}
var appendOdsSystemColumns = (items, dbType) => {
    var existing = {}
    var hasSourcePrimaryKey = false
    for (item in items) {
        if (!blank(item.columnName)) {
            existing[text(item.columnName).toLowerCase()] = true
            hasSourcePrimaryKey = hasSourcePrimaryKey || toInt(item.primaryKey, 0) == 1
        }
    }
    for (fixed in odsSystemColumns(dbType)) {
        var key = text(fixed.columnName).toLowerCase()
        if (existing[key] == null) {
            // 来源表已有业务主键时，ODS_UUID 仅作为系统追踪字段；无主键时才承担主键职责。
            if (text(fixed.columnName).toUpperCase() == 'ODS_UUID') {
                fixed.primaryKey = hasSourcePrimaryKey ? 0 : 1
            }
            fixed.ordinalPosition = items.size()
            items.add(fixed)
            existing[key] = true
        }
    }
    return items
}

// 第四步登记的字段治理快照是用户确认后的主键口径。物理探查列可能没有
// primary_key 标记，因此建表模板必须优先合并该快照，不能把 ODS_UUID 当作默认主键。
var registeredPrimaryKeys = {}
var registeredFields = governanceConfigOf(sourceTable).fields
for (registeredField in (registeredFields == null ? [] : registeredFields)) {
    var registeredColumnName = text(first(registeredField, ['columnName', 'column_name'])).trim().toLowerCase()
    if (!blank(registeredColumnName) && isPrimaryKey(first(registeredField, ['primaryKey', 'primary_key', 'isPrimaryKey', 'is_primary_key']))) {
        registeredPrimaryKeys[registeredColumnName] = true
    }
}

var sourceTableName = text(first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])).trim()
var physicalColumns = sourcePhysicalColumns(sourceDatasource, sourceTableName)
var physicalColumnsByName = {}
for (physical in (physicalColumns == null ? [] : physicalColumns)) {
    if (!blank(physical.columnName)) {
        physicalColumnsByName[lower(physical.columnName)] = physical
    }
}

var columns = db.table('db_table_column_t').where()
    .eq('table_id', sourceTableId)
    .eq('is_del', 0)
    .orderBy('ordinal_position', 'asc')
    .select()
// 一些历史登记记录只保存了表级信息。快照为空时不能丢弃已成功探查的
// 物理字段，否则抽屉只能显示 ODS 系统字段。
if ((columns == null || columns.size() == 0) && physicalColumns != null && physicalColumns.size() > 0) {
    columns = physicalColumns
}

var tableItems = []
for (col in (columns == null ? [] : columns)) {
    var columnName = first(col, ['columnName', 'column_name'])
    var physicalColumn = physicalColumnsByName[lower(columnName)]
    // 优先采用实际源库类型和长度；未探查到时保持历史登记快照的兼容行为。
    var sourceType = physicalColumn == null || blank(physicalColumn.dataType)
        ? first(col, ['dataType', 'data_type']) : physicalColumn.dataType
    var columnType = physicalColumn == null || blank(physicalColumn.columnType)
        ? first(col, ['columnType', 'column_type']) : physicalColumn.columnType
    var sourceLength = physicalColumn == null || toInt(physicalColumn.length, 0) <= 0
        ? first(col, ['length']) : physicalColumn.length
    var sourcePrecision = physicalColumn == null || toInt(physicalColumn.precision, 0) <= 0
        ? first(col, ['precisionLength', 'precision_length']) : physicalColumn.precision
    var sourceScale = physicalColumn == null || toInt(physicalColumn.scale, 0) < 0
        ? first(col, ['scale']) : physicalColumn.scale
    var targetType = mapDataType(sourceType, columnType, sourceLength)
    tableItems.add({
        sourceColumnId: first(col, ['tid']),
        columnName: columnName,
        columnComment: first(col, ['columnComment', 'column_comment']) || columnName,
        sourceDataType: sourceType,
        sourceColumnType: columnType,
        dataType: targetType,
        columnType: targetType,
        length: normalizeLength(targetType, sourceLength, sourcePrecision),
        precisionLength: toInt(sourcePrecision, 0),
        scale: toInt(sourceScale, 0),
        primaryKey: isPrimaryKey(first(col, ['primaryKey', 'primary_key', 'isPrimaryKey', 'is_primary_key'])) || registeredPrimaryKeys[text(columnName).trim().toLowerCase()] == true ? 1 : 0,
        nullable: normalizeNullable(first(col, ['nullable'])),
        defaultValue: first(col, ['defaultValue', 'default_value']),
        ordinalPosition: toInt(first(col, ['ordinalPosition', 'ordinal_position']), 0),
        autoIncrement: toInt(first(col, ['autoIncrement', 'auto_increment']), 0),
        indexed: toInt(first(col, ['indexed']), 0),
        isUnique: toInt(first(col, ['isUnique', 'is_unique']), 0)
    })
}
tableItems = appendDictionaryTranslationColumns(tableItems, sourceTable)

// 目标库以数据组织字典为源：dataSourceType -> ODS -> 分域（例如原始库）；
// 每个分域的目标数据源由分层分域配置维护，和物化弹窗下拉严格使用同一关系。
var dataSourceTypeRoot = db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('dict_code', 'dataSourceType')
    .eq('parent_id', '0')
    .eq('is_del', 0)
    .selectOne()
var odsNode = dataSourceTypeRoot == null ? null : db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('parent_id', dataSourceTypeRoot.tid)
    .eq('dict_code', 'ods')
    .eq('is_del', 0)
    .selectOne()
var odsDomains = odsNode == null ? [] : db.table('sym_dict_t').where()
    .eq('tenant_id', tenantId)
    .eq('parent_id', odsNode.tid)
    .eq('is_del', 0)
    .orderBy('sort_no', 'asc')
    .select()
var domainIds = []
for (odsDomain in (odsDomains == null ? [] : odsDomains)) {
    domainIds.add(text(odsDomain.tid))
}
var domainRelations = domainIds.size() == 0 ? [] : db.table('dwm_center_layer_source_t').where()
    .eq('tenant_id', tenantId)
    .eq('target_type', 'domain')
    .in('target_id', domainIds)
    .eq('is_del', 0)
    .orderBy('updated_time', 'desc')
    .select()
var targetDatasource = null
var targetDomain = null
for (odsDomain in (odsDomains == null ? [] : odsDomains)) {
    var domainId = text(odsDomain.tid)
    var relation = null
    for (candidate in (domainRelations == null ? [] : domainRelations)) {
        if (text(candidate.targetId) == domainId && !blank(candidate.datasourceId)) {
            relation = candidate
            break
        }
    }
    if (relation == null) {
        continue
    }
    var candidateDatasource = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId)
        .eq('tid', relation.datasourceId)
        .eq('is_del', 0)
        .eq('show_connect', 1)
        .selectOne()
    if (candidateDatasource != null) {
        targetDatasource = candidateDatasource
        targetDomain = odsDomain
        break
    }
}

// Source metadata may preserve an Oracle/DM owner prefix (for example
// TEST0809.CAR_TURN_OVER). That prefix is needed for source probing, but it
// must never become part of a physical target table name. Both task management
// and the canvas consume this template, so normalize it once here.
var targetTableSourceName = sourceTableName
var ownerSeparator = targetTableSourceName.lastIndexOf('.')
if (ownerSeparator >= 0 && ownerSeparator < targetTableSourceName.length() - 1) {
    targetTableSourceName = targetTableSourceName.substring(ownerSeparator + 1)
}
var tablePrefix = 'ODS'
var tableName = targetTableSourceName.toUpperCase().startsWith(tablePrefix + '_') ? targetTableSourceName : tablePrefix + '_' + targetTableSourceName
var tableNameCn = first(sourceTable, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']) || tableName
var dbId = targetDatasource == null ? '' : text(first(targetDatasource, ['tid']))
var dbName = targetDatasource == null ? '' : text(first(targetDatasource, ['dbName', 'db_name']))
var nodeId = targetDatasource == null ? '' : text(first(targetDatasource, ['nodeId', 'node_id']))
var dbType = targetDatasource == null ? '' : text(first(targetDatasource, ['dbType', 'db_type']))
tableItems = appendOdsSystemColumns(tableItems, dbType)

return {
    propList: {
        sourceTableId: sourceTableId,
        catalogId: catalogId,
        sourceDatasourceName: sourceDatasourceName,
        sourceAccessMode: sourceAccessMode,
        sourceAccessModeCode: sourceAccessModeCode,
        sourceNetworkCode: sourceNetworkCode,
        dbId: dbId,
        dbName: dbName,
        nodeId: nodeId,
        dbType: dbType,
        sourceTableName: sourceTableName,
        targetLayerCode: tablePrefix,
        targetDomainId: targetDomain == null ? '' : text(targetDomain.tid),
        targetDomainName: targetDomain == null ? '' : text(targetDomain.dictName),
        assetType: 'table',
        tableNameCn: tableNameCn,
        tableName: tableName,
        tableType: '数据表',
        recordCount: toInt(first(sourceTable, ['recordCount', 'record_count']), 0),
        storageSize: first(sourceTable, ['storageSize', 'storage_size']) || '0 KB',
        businessType: first(sourceTable, ['businessType', 'business_type']) || '业务表',
        relatedDirectory: '自动生成目录'
    },
    tableItems: tableItems
}
