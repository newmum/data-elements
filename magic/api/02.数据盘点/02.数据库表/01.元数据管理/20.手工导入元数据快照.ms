{
  "properties": {},
  "id": "metadata_import_snapshot_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "20.手工导入元数据快照",
  "createTime": 1784640000000,
  "updateTime": 1787153759135,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/importSnapshot",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"dbId\": \"00b062694d774515a9155581aa20e06e\",\n  \"dataSourceType\": \"mysql\",\n  \"dryRun\": true,\n  \"tables\": [{\"tableName\": \"police_case_info\", \"tableComment\": \"治安案件信息\", \"tableType\": \"数据表\", \"businessType\": \"业务表\"}],\n  \"fieldMap\": {\"police_case_info\": [{\"columnName\": \"case_id\", \"columnComment\": \"案件标识\", \"columnType\": \"varchar\", \"columnLength\": \"64\", \"primaryKey\": true, \"nullable\": false, \"isTimestamp\": false}, {\"columnName\": \"update_time\", \"columnComment\": \"更新时间\", \"columnType\": \"datetime\", \"primaryKey\": false, \"nullable\": true, \"isTimestamp\": true}]}\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "手工导入元数据快照（POST /dst/database/metadata/importSnapshot）。写入 db_table_t、db_table_column_t 和数据源表数量，不连接物理库；严格校验数据资源名称、表类型、业务类型、字段中文名、字段类型及必填布尔属性，并保存时间戳字段初始配置，使离线导入数据与自动探查数据共用第二步至第五步登记流程。传 dryRun=true 时只校验并返回表/字段数量，不写数据库；使用请求参数 tables、tableRows、fieldMap、dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：20.手工导入元数据快照
// 路由：POST /dst/database/metadata/importSnapshot
// 业务说明：手工导入元数据快照（POST /dst/database/metadata/importSnapshot）。写入 db_table_t、db_table_column_t 和数据源表数量，不连接物理库；严格校验数据资源名称、表类型、业务类型、字段中文名、字段类型及必填布尔属性，并保存时间戳字段初始配置，使离线导入数据与自动探查数据共用第二步至第五步登记流程。传 dryRun=true 时只校验并返回表/字段数量，不写数据库；使用请求参数 tables、tableRows、fieldMap、dryRun。
import tenantRuntime
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import log
import java.util.Map
import java.util.regex.Pattern

/*
 * Metadata import 20: persist offline/manual metadata snapshot.
 * This endpoint never connects to the physical datasource. It writes imported tables to db_table_t
 * and imported columns to db_table_column_t so step 2-5 can reuse exactly the same registered
 * snapshot APIs as auto-explored datasources.
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => blank(value) ? '' : textOf(value).trim().toLowerCase()
var newId = () => IdUtil.getSnowflakeNextIdStr()
var nowTime = () => new Date()
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
var boolValue = (value, defaultValue) => {
    if (value == null) { return defaultValue }
    if (value == true || value == 1) { return true }
    var text = lower(value)
    if (text == 'true' || text == '1' || text == 'yes' || text == 'y' || text == 'shi' || text == '是') { return true }
    if (text == 'false' || text == '0' || text == 'no' || text == 'n' || text == 'fou' || text == '否') { return false }
    return defaultValue
}
var intValue = (value, defaultValue) => {
    if (blank(value)) { return defaultValue }
    try { return value::int } catch (e) { return defaultValue }
}
var longValue = (value, defaultValue) => {
    if (blank(value)) { return defaultValue }
    try { return value::long } catch (e) { return defaultValue }
}
var normalizeType = (value, defaultValue) => {
    var text = textOf(value)
    if (blank(text)) { return defaultValue }
    var low = lower(text)
    if (low == 'view' || low == '视图') { return '视图' }
    if (low == 'dataset' || low == 'resource' || low == '数据集') { return '数据集' }
    return text
}
var normalizeBusinessType = (value) => {
    var text = textOf(value)
    if (blank(text)) { return '业务表' }
    return text
}
var allowedSourceTypes = [
    'mysql', 'oracle', 'oceanbasemysql', 'oceanbaseoracle', 'gaussdb', 'gbase8a',
    'sqlserver', 'hive', 'maxcompute', 'vertica', 'dameng', 'postgresql',
    'kingbase8', 'minio', 'ftp', 'api', 'kafka', 'receive'
]
var allowedTableTypes = ['数据表', '视图', '数据集']
var allowedBusinessTypes = ['业务表', '日志表', '字典表', '过程表', '备份表']
var inOptions = (value, options) => {
    var normalized = lower(value)
    if (blank(normalized)) { return false }
    for (option in options) {
        if (lower(option) == normalized) { return true }
    }
    return false
}
var hasChinese = (value) => {
    if (blank(value)) { return false }
    return Pattern.compile('[\\u4e00-\\u9fff]').matcher(textOf(value)).find()
}
var buildColumnType = (col) => {
    var columnType = firstValue(col, ['columnType', 'typeName', 'dataType', 'fieldType'])
    var len = firstValue(col, ['columnLength', 'length', 'columnSize'])
    if (blank(columnType)) { columnType = 'varchar' }
    if (!blank(len) && textOf(columnType).indexOf('(') < 0) { return textOf(columnType) + '(' + textOf(len) + ')' }
    return textOf(columnType)
}
var existingTableMap = (dbId) => {
    var map = {}
    var rows = db.table('db_table_t').where().eq('datasource_id', dbId).eq('is_del', 0).select()
    for (row in rows == null ? [] : rows) {
        var name = firstValue(row, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
        if (!blank(name)) { map[lower(name)] = row }
    }
    return map
}
var columnsFor = (fieldMap, table) => {
    var tableName = firstValue(table, ['tableName', 'tableNameEn', 'name'])
    var fields = firstValue(table, ['fields', 'columns', 'columnList', 'tableColumns'])
    if (fields != null && fields instanceof java.util.List) { return fields }
    if (fieldMap == null || blank(tableName)) { return [] }
    var direct = fieldMap[tableName]
    if (direct == null) { direct = fieldMap[lower(tableName)] }
    return direct == null ? [] : direct
}
var columnValues = (tableId, columnId, col, index, tenantId) => {
    var columnName = firstValue(col, ['columnName', 'name', 'fieldName', 'column_name'])
    var dataType = firstValue(col, ['dataType', 'typeName', 'fieldType'])
    var columnType = buildColumnType(col)
    if (blank(dataType)) { dataType = columnType }
    return {
        tid: columnId,
        table_id: tableId,
        tenant_id: tenantId,
        column_name: columnName,
        column_comment: firstValue(col, ['columnComment', 'comment', 'fieldComment', 'column_comment']),
        data_type: dataType,
        column_type: columnType,
        length: longValue(firstValue(col, ['columnLength', 'length', 'columnSize']), null),
        precision_length: intValue(firstValue(col, ['precision', 'precisionLength']), null),
        scale: intValue(firstValue(col, ['scale', 'decimalDigits']), null),
        nullable: boolValue(firstValue(col, ['nullable']), true) ? 1 : 0,
        default_value: firstValue(col, ['defaultValue', 'default_value']),
        primary_key: boolValue(firstValue(col, ['primaryKey', 'pk']), false) ? 1 : 0,
        auto_increment: boolValue(firstValue(col, ['autoIncrement']), false) ? 1 : 0,
        is_unique: boolValue(firstValue(col, ['unique', 'isUnique']), false) ? 1 : 0,
        indexed: boolValue(firstValue(col, ['indexed', 'index']), false) ? 1 : 0,
        ordinal_position: intValue(firstValue(col, ['ordinalPosition', 'serialNo', 'sortNo']), index + 1),
        is_del: 0,
        data_standard_id: firstValue(col, ['dataStandardId', 'standardField']),
        is_dict: boolValue(firstValue(col, ['isDict', 'dictionary', 'enableCodeTable']), false) ? 1 : 0,
        dict_name: firstValue(col, ['dictName', 'dictionaryName']),
        updated_time: nowTime()
    }
}
var saveColumns = (tableId, columns, tenantId) => {
    var oldRows = db.table('db_table_column_t').where().eq('table_id', tableId).eq('is_del', 0).select()
    var oldMap = {}
    for (old in oldRows == null ? [] : oldRows) {
        oldMap[lower(firstValue(old, ['columnName', 'column_name']))] = old
    }
    var count = 0
    var index = 0
    for (col in columns == null ? [] : columns) {
        var columnName = firstValue(col, ['columnName', 'name', 'fieldName', 'column_name'])
        if (blank(columnName)) { continue }
        var key = lower(columnName)
        var old = oldMap[key]
        var columnId = old == null ? newId() : firstValue(old, ['tid'])
        var values = columnValues(tableId, columnId, col, index, tenantId)
        if (old == null) {
            values.created_time = nowTime()
            db.table('db_table_column_t').primary('tid').insert(values)
        } else {
            db.table('db_table_column_t').primary('tid').update(values, true)
            oldMap[key] = null
        }
        count = count + 1
        index = index + 1
    }
    for (key in oldMap.keySet()) {
        var old = oldMap[key]
        if (old != null) {
            db.table('db_table_column_t').primary('tid').update({ tid: firstValue(old, ['tid']), is_del: 1, updated_time: nowTime() }, true)
        }
    }
    return count
}
var saveEs = (tableId, doc) => {
    try {
        esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(doc), false)
    } catch (e) {
        log.warn('sync imported table ES failed, tid: {}, error: {}', tableId, e.message)
    }
}

if (body == null) { exit 400, 'request body cannot be empty' }
var dbId = firstValue(body, ['dbId', 'datasourceId', 'tid'])
if (blank(dbId)) { exit 400, 'dbId cannot be empty' }
var datasource = db.table('db_datasource_t').where().eq('tid', dbId).eq('is_del', 0).selectOne()
if (datasource == null) { exit 404, 'datasource not found' }
// 租户只能来自已认证请求的上下文，禁止由导入载荷或数据源记录决定。
var tenantId = tenantRuntime.id()
if (blank(tenantId)) { exit 401, '当前登录租户无效，请重新登录后再导入元数据' }
var sourceType = firstValue(body, ['dataSourceType', 'dbType'])
if (blank(sourceType)) { sourceType = firstValue(datasource, ['dbType', 'db_type']) }
var tables = body.tables == null ? (body.tableRows == null ? [] : body.tableRows) : body.tables
var fieldMap = body.fieldMap == null ? {} : body.fieldMap
if ((tables == null || tables.size() == 0) && fieldMap != null) {
    tables = []
    for (name in fieldMap.keySet()) {
        tables.add({ tableName: name, tableComment: name, businessType: '业务表' })
    }
}
if (tables == null || tables.size() == 0) { exit 400, 'imported table list cannot be empty' }

// 在写库前进行后端强校验，避免绕过 Excel 下拉和前端校验写入不完整元数据。
if (!inOptions(sourceType, allowedSourceTypes)) {
    exit 400, '不支持的数据源类型: ' + textOf(sourceType)
}
var validationErrors = []
var seenTables = {}
for (table in tables) {
    var tableName = firstValue(table, ['tableName', 'tableNameEn', 'name'])
    var comment = firstValue(table, ['tableComment', 'tableNameCn', 'comment'])
    var tableType = firstValue(table, ['tableType', 'type', 'tableKind'])
    var businessType = firstValue(table, ['businessType'])
    if (blank(tableName)) { validationErrors.add('数据表名不能为空') }
    if (blank(comment)) {
        validationErrors.add('数据表' + (blank(tableName) ? '' : '[' + tableName + ']') + '的数据资源名称不能为空')
    } else if (!hasChinese(comment)) {
        validationErrors.add('数据表[' + tableName + ']的数据资源名称必须包含中文')
    } else if (!blank(tableName) && lower(comment) == lower(tableName)) {
        validationErrors.add('数据表[' + tableName + ']的数据资源名称不能直接使用英文表名')
    }
    if (!inOptions(tableType, allowedTableTypes)) {
        validationErrors.add('数据表[' + tableName + ']的表类型无效')
    }
    if (!inOptions(businessType, allowedBusinessTypes)) {
        validationErrors.add('数据表[' + tableName + ']的业务类型无效')
    }
    var tableKey = lower(tableName)
    if (!blank(tableKey)) {
        if (seenTables[tableKey] != null) {
            validationErrors.add('数据表名重复: ' + tableName)
        } else {
            seenTables[tableKey] = true
        }
    }
    var columns = columnsFor(fieldMap, table)
    if (columns == null || columns.size() == 0) {
        validationErrors.add('数据表[' + tableName + ']至少需要一个数据项')
    } else {
        var seenColumns = {}
        var timestampCount = 0
        for (col in columns) {
            var columnName = firstValue(col, ['columnName', 'name', 'fieldName', 'column_name'])
            var columnComment = firstValue(col, ['columnComment', 'comment', 'fieldComment', 'column_comment'])
            var columnType = firstValue(col, ['columnType', 'typeName', 'dataType', 'fieldType'])
            var primaryKeyValue = rowValue(col, ['primaryKey', 'pk'])
            var nullableValue = rowValue(col, ['nullable'])
            var timestampValue = rowValue(col, ['isTimestamp', 'isTimestampField'])
            if (blank(columnName)) { validationErrors.add('数据表[' + tableName + ']存在字段英文名为空的数据项') }
            if (blank(columnComment)) {
                validationErrors.add('字段[' + tableName + '.' + columnName + ']的字段中文名不能为空')
            } else if (!hasChinese(columnComment)) {
                validationErrors.add('字段[' + tableName + '.' + columnName + ']的字段中文名必须包含中文')
            }
            if (blank(columnType)) { validationErrors.add('字段[' + tableName + '.' + columnName + ']的字段类型不能为空') }
            if (primaryKeyValue == null) { validationErrors.add('字段[' + tableName + '.' + columnName + ']的是否主键不能为空') }
            if (nullableValue == null) { validationErrors.add('字段[' + tableName + '.' + columnName + ']的是否可空不能为空') }
            if (timestampValue == null) { validationErrors.add('字段[' + tableName + '.' + columnName + ']的是否时间戳字段不能为空') }
            var columnKey = lower(columnName)
            if (!blank(columnKey)) {
                if (seenColumns[columnKey] != null) {
                    validationErrors.add('数据表[' + tableName + ']字段名重复: ' + columnName)
                } else {
                    seenColumns[columnKey] = true
                }
            }
            if (boolValue(timestampValue, false)) { timestampCount = timestampCount + 1 }
        }
        if (timestampCount > 1) {
            validationErrors.add('数据表[' + tableName + ']只能设置一个时间戳字段')
        }
    }
}
if (validationErrors.size() > 0) {
    var shownErrors = []
    var maxErrors = validationErrors.size() > 8 ? 8 : validationErrors.size()
    var errorIndex = 0
    for (errorItem in validationErrors) {
        if (errorIndex < maxErrors) { shownErrors.add(errorItem) }
        errorIndex = errorIndex + 1
    }
    var suffix = validationErrors.size() > maxErrors ? '；另有' + (validationErrors.size() - maxErrors) + '项错误' : ''
    exit 400, shownErrors.join('；') + suffix
}

// 安全校验模式供 Magic 编辑器和回归测试使用：通过完整规则校验后立即返回，不产生资产数据。
var dryRun = boolValue(body.dryRun, false)
if (dryRun) {
    var validatedFieldCount = 0
    for (table in tables) {
        var validatedColumns = columnsFor(fieldMap, table)
        validatedFieldCount = validatedFieldCount + (validatedColumns == null ? 0 : validatedColumns.size())
    }
    return {
        dryRun: true,
        validated: true,
        dbId: dbId,
        dataSourceType: sourceType,
        tableCount: tables.size(),
        fieldCount: validatedFieldCount
    }
}

var existingMap = existingTableMap(dbId)
var rows = []
var importedFieldCount = 0
for (table in tables) {
    var tableName = firstValue(table, ['tableName', 'tableNameEn', 'name'])
    if (blank(tableName)) { continue }
    var key = lower(tableName)
    var existing = existingMap[key]
    var tableId = existing == null ? newId() : firstValue(existing, ['tid'])
    var columns = columnsFor(fieldMap, table)
    var fieldCount = columns == null ? 0 : columns.size()
    var comment = firstValue(table, ['tableComment', 'tableNameCn', 'comment'])
    if (blank(comment)) { comment = tableName }
    var tableType = normalizeType(firstValue(table, ['tableType', 'type', 'tableKind']), '数据表')
    var existingAnnotated = existing == null ? null : firstValue(existing, ['annotated'])
    var explicitAnnotated = rowValue(table, ['annotated'])
    var annotatedValue = explicitAnnotated == null ? (existingAnnotated == null ? 0 : existingAnnotated) : (boolValue(explicitAnnotated, false) ? 1 : 0)
    var businessType = normalizeBusinessType(firstValue(table, ['businessType']))
    var timestampField = ''
    var governanceFields = []
    for (governanceColumn in columns) {
        var governanceColumnName = firstValue(governanceColumn, ['columnName', 'name', 'fieldName', 'column_name'])
        var governanceTimestamp = boolValue(firstValue(governanceColumn, ['isTimestamp', 'isTimestampField']), false)
        if (governanceTimestamp) { timestampField = governanceColumnName }
        governanceFields.add({
            columnName: governanceColumnName,
            columnComment: firstValue(governanceColumn, ['columnComment', 'comment', 'fieldComment', 'column_comment']),
            standardField: '',
            dictionaryRole: '',
            dictionaryCategoryCode: tableName,
            dictionaryCategoryName: '',
            dictionaryCategoryValue: '',
            isTimestampField: governanceTimestamp,
            dictionaryRelation: null
        })
    }
    var governanceConfig = {
        version: 2,
        tableName: tableName,
        tableComment: comment,
        businessType: businessType,
        dictionaryStructureType: 'single',
        timestampField: timestampField,
        dictionaryProfile: null,
        dictionaryProfiles: [],
        fields: governanceFields
    }
    var values = {
        tid: tableId,
        datasource_id: dbId,
        table_name: tableName,
        table_name_en: tableName,
        table_name_cn: comment,
        table_comment: comment,
        table_type: tableType,
        business_type: businessType,
        business_type_reason: firstValue(table, ['businessTypeReason']) == null ? '离线模板导入' : firstValue(table, ['businessTypeReason']),
        timestamp_field: timestampField,
        field_governance_config: jsons.toJSONStr(governanceConfig),
        annotated: annotatedValue,
        record_count: longValue(firstValue(table, ['recordCount', 'rowCount']), 0),
        field_count: fieldCount,
        storage_size: firstValue(table, ['storageSize', 'totalSizeFormatted']) == null ? '0 B' : firstValue(table, ['storageSize', 'totalSizeFormatted']),
        total_size_formatted: firstValue(table, ['totalSizeFormatted', 'storageSize']) == null ? '0 B' : firstValue(table, ['totalSizeFormatted', 'storageSize']),
        total_size_bytes: longValue(firstValue(table, ['totalSizeBytes', 'totalSizeBytes_num']), 0),
        org_id: firstValue(table, ['orgId']) == null ? firstValue(datasource, ['orgId', 'org_id']) : firstValue(table, ['orgId']),
        org_path: firstValue(table, ['orgPath']),
        data_source_type: sourceType,
        source_table_id: tableId,
        source_table_name: tableName,
        manage_unit: firstValue(datasource, ['orgId', 'org_id']),
        asset_desc: comment,
        tenant_id: tenantId,
        asset_status: 2,
        flow_status: 2,
        flow_order_id: null,
        is_del: 0,
        updated_time: nowTime()
    }
    if (existing == null) {
        values.created_time = nowTime()
        values.reg_time = nowTime()
        db.table('db_table_t').primary('tid').insert(values)
    } else {
        db.table('db_table_t').primary('tid').update(values, true)
    }
    fieldCount = saveColumns(tableId, columns, tenantId)
    importedFieldCount = importedFieldCount + fieldCount
    if (fieldCount != values.field_count) {
        values.field_count = fieldCount
        db.table('db_table_t').primary('tid').update({ tid: tableId, field_count: fieldCount, updated_time: nowTime() }, true)
    }
    var output = {
        tid: tableId,
        id: tableId,
        assetType: 'table',
        datasourceId: dbId,
        dbId: dbId,
        tableName: tableName,
        tableNameEn: tableName,
        tableNameCn: comment,
        tableComment: comment,
        tableType: tableType,
        businessType: businessType,
        businessTypeReason: values.business_type_reason,
        recordCount: values.record_count,
        fieldCount: fieldCount,
        storageSize: values.storage_size,
        totalSizeBytes: values.total_size_bytes,
        annotated: annotatedValue,
        metadataSource: 'template',
        action: existing == null ? 'create' : 'update'
    }
    saveEs(tableId, output)
    rows.add(output)
}
var activeTableCount = db.table('db_table_t').where().eq('datasource_id', dbId).eq('is_del', 0).select().size()
db.table('db_datasource_t').primary('tid').update({ tid: dbId, table_num: activeTableCount, updated_time: nowTime() }, true)
return {
    dbId: dbId,
    importedTableCount: rows.size(),
    importedFieldCount: importedFieldCount,
    tableCount: activeTableCount,
    rows: rows
}
