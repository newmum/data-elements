{
  "properties": {},
  "id": "ods_data_agg_task_ensure_01",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "13.按来源表补齐接入任务",
  "createTime": 1785063600000,
  "updateTime": 1790420562971,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dataAggTaskEnsure",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tableId\": \"015c09572d174e89b9bea9c765ef72df\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"created\": true,\n  \"taskId\": \"03ac61c2b0b84c1a87ff11ae1e38b949\",\n  \"task\": {\"tid\":\"03ac61c2b0b84c1a87ff11ae1e38b949\"}\n}",
  "description": "按来源表补齐数据接入任务（POST /ods/dataAggTaskEnsure）。写接口；以 db_table_t 来源表为入口，读取自动目录和目标表，创建 nifi_pipeline_t 画布草稿、data_access_agg_task_t 任务及字段映射；已有任务时直接返回。用于数据接入-任务管理页面的创建任务按钮；使用请求参数 tableId、sourceTableId、tid、repair、forceRebuild、rebuild、catalogId；涉及 da_prop_t、db_table_column_t、db_table_t、da_catalog_t、dwm_center_layer_source_t、db_datasource_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：13.按来源表补齐接入任务
// 路由：POST /ods/dataAggTaskEnsure
// 业务说明：按来源表补齐数据接入任务（POST /ods/dataAggTaskEnsure）。写接口；以 db_table_t 来源表为入口，读取自动目录和目标表，创建 nifi_pipeline_t 画布草稿、data_access_agg_task_t 任务及字段映射；已有任务时直接返回。用于数据接入-任务管理页面的创建任务按钮；使用请求参数 tableId、sourceTableId、tid、repair、forceRebuild、rebuild、catalogId；涉及 da_prop_t、db_table_column_t、db_table_t、da_catalog_t、dwm_center_layer_source_t、db_datasource_t 等业务表。
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
/*
 * 按来源表确保接入任务存在。
 *
 * 业务链路：来源表 => 字段增强 => 目标表（目录）。
 * 如果来源表已经有 data_access_agg_task_t，则直接返回；
 * 否则读取 createTapleApply 自动生成的目录和目标表，写入任务与 NiFi 画布草稿，
 * 让前端能直接打开 data-element-nifi 继续查看/调整流程。
 */
import cn.hutool.core.util.IdUtil
import jsons
import metadataAsset
import targetTableSchema
import apiPullConfig
import tenantRuntime

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
var jdbcUrl = (dbType, cfg, row) => {
  var url = first(cfg, ['jdbcURL', 'jdbcUrl', 'url'])
  if (blank(url)) { url = first(row, ['jdbc_url', 'jdbcURL', 'jdbcUrl', 'url']) }
  if (!blank(url)) { return text(url) }
  var type = text(dbType).trim().toUpperCase()
  var host = first(cfg, ['host', 'dbMetaIp']) || first(row, ['host', 'db_meta_ip']) || '127.0.0.1'
  var port = first(cfg, ['port', 'dbMetaPort']) || first(row, ['port', 'db_meta_port'])
  var database = first(cfg, ['database', 'serviceName', 'sid', 'dbName', 'dbMetaDbName']) || first(row, ['dbName', 'db_name']) || ''
  if (type == 'DM' || type == 'DAMENG') { return 'jdbc:dm://' + host + ':' + (blank(port) ? '5236' : port) + '/' + database }
  if (type == 'ORACLE' || type == 'ORACLE12' || type == 'ORACLE_12') {
    var connectionType = text(first(cfg, ['connectionType', 'jdbcType'])).trim().toUpperCase()
    if (connectionType == 'SID') { return 'jdbc:oracle:thin:@' + host + ':' + (blank(port) ? '1521' : port) + ':' + database }
    return 'jdbc:oracle:thin:@//' + host + ':' + (blank(port) ? '1521' : port) + '/' + database
  }
  if (type == 'POSTGRES' || type == 'POSTGRESQL' || type == 'TDSQL_PG' || type == 'GAUSSDB' || type == 'OPENGAUSS') { return 'jdbc:postgresql://' + host + ':' + (blank(port) ? '5432' : port) + '/' + database }
  if (type == 'SQLSERVER' || type == 'MSSQL' || type == 'MS_SQL') { return 'jdbc:sqlserver://' + host + ':' + (blank(port) ? '1433' : port) + ';databaseName=' + database }
  if (type == 'KINGBASE') { return 'jdbc:kingbase8://' + host + ':' + (blank(port) ? '54321' : port) + '/' + database }
  if (type == 'CLICKHOUSE') { return 'jdbc:clickhouse://' + host + ':' + (blank(port) ? '8123' : port) + '/' + database }
  return 'jdbc:mysql://' + host + ':' + (blank(port) ? '3306' : port) + '/' + database
}
var safeIdentifier = (value) => text(value).trim().matches('^[A-Za-z_][A-Za-z0-9_$]*$')
// Registrations from production commonly persist a dictionary table as
// "SCHEMA.TABLE".  Keep both names separately: SQL can qualify the table
// safely while datasource matching and metadata lookups use the table name.
var dictionaryTableReference = (value) => {
    var raw = text(value).trim()
    var dot = raw.indexOf('.')
    if (dot < 0) {
        return safeIdentifier(raw) ? { schema: '', table: raw } : { schema: '', table: '' }
    }
    if (dot == 0 || dot == raw.length() - 1 || raw.substring(dot + 1).indexOf('.') >= 0) {
        return { schema: '', table: '' }
    }
    var schema = raw.substring(0, dot)
    var table = raw.substring(dot + 1)
    return safeIdentifier(schema) && safeIdentifier(table)
        ? { schema: schema, table: table } : { schema: '', table: '' }
}
var qualifiedDictionaryTable = (relation, dictionarySource) => {
    var table = text(relation == null ? '' : relation.dictionaryTable).trim()
    var schema = text(relation == null ? '' : relation.dictionarySchema).trim()
    if (blank(schema) && dictionarySource != null) { schema = text(dictionarySource.schema).trim() }
    return safeIdentifier(schema) ? schema + '.' + table : table
}
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
// Step 4's current save writes this snapshot on db_table_t. Earlier production
// saves attached it to the generated catalog in da_prop_t.  Follow sourceTableId
// to preserve dictionary enrichment for both registration generations.
var configHasFields = (config) => config != null && config.fields != null && config.fields.size() > 0
var governanceConfigFromProperties = (properties) => {
    var fallback = {}
    for (property in (properties == null ? [] : properties)) {
        var propertyName = text(first(property, ['propName', 'prop_name'])).trim().toLowerCase()
        if (propertyName != 'fieldgovernanceconfig' && propertyName != 'field_governance_config'
            && propertyName != 'fieldgovernancecfg') { continue }
        var candidate = parseGovernanceConfig(first(property, ['propValue', 'prop_value']))
        if (configHasFields(candidate)) { return candidate }
        fallback = candidate
    }
    return fallback
}
var governanceConfigOf = (table) => {
    var persisted = parseGovernanceConfig(first(table, [
        'fieldGovernanceConfig', 'field_governance_config', 'fieldGovernanceCfg'
    ]))
    if (configHasFields(persisted)) { return persisted }
    var tableId = text(first(table, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(tableId)) { return persisted }
    var direct = governanceConfigFromProperties(db.table('da_prop_t').where()
        .eq('parent_id', tableId).eq('is_del', 0).select())
    if (configHasFields(direct)) { return direct }
    var sourceLinks = db.table('da_prop_t').where()
        .eq('prop_name', 'sourceTableId').eq('prop_value', tableId).eq('is_del', 0).select()
    for (link in sourceLinks) {
        var catalogId = text(first(link, ['parentId', 'parent_id'])).trim()
        if (blank(catalogId)) { continue }
        var catalogConfig = governanceConfigFromProperties(db.table('da_prop_t').where()
            .eq('parent_id', catalogId).eq('is_del', 0).select())
        if (configHasFields(catalogConfig)) { return catalogConfig }
    }
    return configHasFields(direct) ? direct : persisted
}
// A mandatory-standard relation is an enumerated code alignment.  It has no
// dictionary table, but it must still materialize and populate <code>_cn.
var mandatoryStandardMappings = (relation) => {
    var result = []
    if (relation == null) {
        return result
    }
    var forceEnabled = text(relation.forceStandardEnabled).trim().toLowerCase()
    var sourceType = text(relation.sourceType).trim().toLowerCase()
    var mode = text(relation.mode).trim().toLowerCase()
    // Production registrations may also persist a small fixed dictionary as
    // sourceType/mode=enum. It is executed exactly like a mandatory-standard
    // mapping: no lookup table is needed, but the label must be written to the
    // generated <code>_cn target field.
    if (!(forceEnabled == 'true' || forceEnabled == '1' || forceEnabled == 'yes'
        || sourceType == 'standard' || mode == 'standard'
        || sourceType == 'enum' || mode == 'enum')) {
        return result
    }
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
// A field's checked dictionary association is persisted independently of the
// detailed relation JSON.  Production registrations may only have these two
// column values, so resolve the executable lookup from the registered
// dictionary table instead of requiring the source relation JSON to be whole.
var registeredDictionaryRelations = (sourceTable) => {
    var result = []
    var sourceTableId = text(first(sourceTable, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(sourceTableId)) { return result }
    var sourceColumns = db.table('db_table_column_t').where()
        .eq('table_id', sourceTableId).eq('is_del', 0).select()
    for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
        var sourceName = text(first(sourceColumn, ['columnName', 'column_name'])).trim()
        var enabled = text(first(sourceColumn, ['enableCodeTable', 'enable_code_table', 'isDict', 'is_dict'])).trim().toLowerCase()
        var dictionaryTableId = text(first(sourceColumn, ['codeTableId', 'code_table_id', 'dictId', 'dict_id'])).trim()
        if (blank(sourceName) || (enabled != '1' && enabled != 'true' && enabled != 'yes' && blank(dictionaryTableId))) {
            continue
        }
        var dictionaryTable = blank(dictionaryTableId) ? null : db.table('db_table_t').where()
            .eq('tid', dictionaryTableId).eq('is_del', 0).selectOne()
        if (dictionaryTable == null) { continue }
        var dictionaryConfig = governanceConfigOf(dictionaryTable)
        var profile = dictionaryConfig.dictionaryProfile
        if (profile == null && dictionaryConfig.dictionaryProfiles != null && dictionaryConfig.dictionaryProfiles.size() > 0) {
            profile = dictionaryConfig.dictionaryProfiles[0]
        }
        if (profile == null && dictionaryConfig.dictionaryCategories != null && dictionaryConfig.dictionaryCategories.size() > 0) {
            profile = dictionaryConfig.dictionaryCategories[0]
        }
        var keyField = text(first(profile, ['codeField', 'code_field', 'dictionaryKeyField', 'dictionary_key_field'])).trim()
        var labelField = text(first(profile, ['labelField', 'label_field', 'nameField', 'name_field', 'dictionaryLabelField', 'dictionary_label_field'])).trim()
        for (dictionaryField in (dictionaryConfig.fields == null ? [] : dictionaryConfig.fields)) {
            var fieldName = text(first(dictionaryField, ['columnName', 'column_name', 'fieldName', 'field_name'])).trim()
            var role = text(first(dictionaryField, ['dictionaryRole', 'dictionary_role'])).trim().toLowerCase()
            if (blank(keyField) && role == 'code') { keyField = fieldName }
            if (blank(labelField) && role == 'label') { labelField = fieldName }
        }
        // The dictionary profile may be an early registration. Use its primary
        // key and conventional name column only as metadata fallback, never as
        // a reason to reject an otherwise saved source-field association.
        var dictionaryColumns = db.table('db_table_column_t').where()
            .eq('table_id', dictionaryTableId).eq('is_del', 0).select()
        for (dictionaryColumn in (dictionaryColumns == null ? [] : dictionaryColumns)) {
            var columnName = text(first(dictionaryColumn, ['columnName', 'column_name'])).trim()
            var normalizedName = columnName.toLowerCase()
            if (blank(keyField) && (text(first(dictionaryColumn, ['primaryKey', 'primary_key'])).trim() == '1'
                || text(first(dictionaryColumn, ['isUnique', 'is_unique'])).trim() == '1'
                || normalizedName.indexOf('code') >= 0)) {
                keyField = columnName
            }
            if (blank(labelField) && (normalizedName == 'name' || normalizedName == 'label'
                || normalizedName.endsWith('_name') || normalizedName.endsWith('_label'))) {
                labelField = columnName
            }
        }
        var dictionaryReference = dictionaryTableReference(first(dictionaryTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']))
        if (safeIdentifier(sourceName) && safeIdentifier(dictionaryReference.table)
            && safeIdentifier(keyField) && safeIdentifier(labelField)) {
            result.add({
                sourceName: sourceName,
                relationKind: 'DICTIONARY',
                dictionaryTable: dictionaryReference.table,
                dictionarySchema: dictionaryReference.schema,
                dictionaryTableId: dictionaryTableId,
                dictionaryKeyField: keyField,
                dictionaryLabelField: labelField,
                conditions: []
            })
        }
    }
    return result
}
// Step 4 persists each enabled dictionary or mandatory-standard relation with
// the source table. Dictionary values are queried at runtime; mandatory
// standards compile to a deterministic enum mapping without a database call.
var dictionaryRelations = (sourceTable) => {
    var config = governanceConfigOf(sourceTable)
    var result = {}
    for (field in (config.fields == null ? [] : config.fields)) {
        var relation = first(field, ['dictionaryRelation', 'dictionary_relation', 'dictionaryConfig', 'dictionary_config'])
        var sourceName = text(first(field, ['columnName', 'column_name', 'fieldName', 'field_name'])).trim()
        var sourceType = text(relation == null ? '' : relation.sourceType).trim().toLowerCase()
        var dictionaryReference = dictionaryTableReference(relation == null ? '' : first(relation, ['dictionaryTable', 'dictionary_table', 'dictTable', 'dict_table']))
        var dictionaryTable = dictionaryReference.table
        var keyField = text(relation == null ? '' : first(relation, ['dictionaryKeyField', 'dictionary_key_field', 'keyField', 'key_field', 'codeField', 'code_field'])).trim()
        var labelField = text(relation == null ? '' : first(relation, ['dictionaryLabelField', 'dictionary_label_field', 'labelField', 'label_field', 'nameField', 'name_field'])).trim()
        var standardValues = mandatoryStandardMappings(relation)
        if (relation != null && text(relation.enabled).toLowerCase() != 'false'
            && safeIdentifier(sourceName) && standardValues.size() > 0) {
            result[sourceName.toLowerCase()] = {
                sourceName: sourceName,
                relationKind: 'MANDATORY_STANDARD',
                standardValues: standardValues,
                multiValue: text(relation.multiValue).toLowerCase() == 'true',
                multiValueSeparator: blank(relation.multiValueSeparator) ? ',' : text(relation.multiValueSeparator)
            }
        } else if (relation != null && text(relation.enabled).toLowerCase() != 'false' && sourceType != 'enum'
            && safeIdentifier(sourceName) && safeIdentifier(dictionaryTable)
            && safeIdentifier(keyField) && safeIdentifier(labelField)) {
            result[sourceName.toLowerCase()] = {
                sourceName: sourceName,
                relationKind: 'DICTIONARY',
                dictionaryTable: dictionaryTable,
                dictionarySchema: dictionaryReference.schema,
                dictionaryTableId: text(first(relation, ['dictionaryTableId', 'dictionary_table_id'])).trim(),
                dictionaryKeyField: keyField,
                dictionaryLabelField: labelField,
                conditions: relation.conditions == null ? [] : relation.conditions,
                multiValue: text(relation.multiValue).toLowerCase() == 'true',
                multiValueSeparator: blank(relation.multiValueSeparator) ? ',' : text(relation.multiValueSeparator)
            }
        }
    }
    // Preserve an explicit source relation when present because it may carry
    // category/status conditions. Fill only the associations that production
    // persisted at the column level.
    for (registeredRelation in registeredDictionaryRelations(sourceTable)) {
        var key = registeredRelation.sourceName.toLowerCase()
        if (result[key] == null) {
            result[key] = registeredRelation
        }
    }
    return result
}
// Resolve governed outputs from the materialized target table.  An explicit
// source-to-target binding takes precedence, but imported historical targets
// may contain an incomplete binding array.  For any unbound <source>_cn
// output, the source registration remains authoritative and restores the
// dictionary/standard transformation from that registered relation.
var targetFieldTransformBindings = (sourceTable, targetTable, targetColumns) => {
    var sourceRelations = dictionaryRelations(sourceTable)
    var config = governanceConfigOf(targetTable)
    var rawBindings = config.fieldTransformBindings == null ? [] : config.fieldTransformBindings
    var targetNames = {}
    for (targetColumn in (targetColumns == null ? [] : targetColumns)) {
        targetNames[text(first(targetColumn, ['columnName', 'column_name'])).toLowerCase()] = true
    }
    var result = []
    var seenTargets = {}
    for (rawBinding in rawBindings) {
        if (text(rawBinding.enabled).toLowerCase() == 'false') { continue }
        var sourceName = text(first(rawBinding, ['sourceField', 'governanceField'])).trim()
        var targetName = text(rawBinding.targetField).trim()
        var relation = sourceRelations[sourceName.toLowerCase()]
        var expectedKind = text(rawBinding.ruleType).trim().toUpperCase()
        if (relation == null || !safeIdentifier(targetName) || targetNames[targetName.toLowerCase()] == null
            || seenTargets[targetName.toLowerCase()] != null
            || (!blank(expectedKind) && expectedKind != relation.relationKind)) {
            continue
        }
        result.add({ sourceField: relation.sourceName, targetField: targetName, relationKind: relation.relationKind,
            sourceColumnId: text(rawBinding.sourceColumnId), targetColumnId: text(rawBinding.targetColumnId), legacy: false })
        seenTargets[targetName.toLowerCase()] = true
    }
    // Reconcile incomplete imported binding metadata.  Do not overwrite an
    // accepted explicit target above; only add a relation for an actual target
    // translation column that does not yet have a valid binding.
    for (targetColumn in (targetColumns == null ? [] : targetColumns)) {
        var targetName = text(first(targetColumn, ['columnName', 'column_name'])).trim()
        if (!targetName.toLowerCase().endsWith('_cn')) { continue }
        var sourceName = targetName.substring(0, targetName.length() - 3)
        var relation = sourceRelations[sourceName.toLowerCase()]
        if (relation == null || seenTargets[targetName.toLowerCase()] != null) { continue }
        result.add({ sourceField: relation.sourceName, targetField: targetName, relationKind: relation.relationKind,
            sourceColumnId: text(first(targetColumn, ['sourceTableColumnId', 'source_table_column_id'])),
            targetColumnId: text(targetColumn.tid), legacy: true })
        seenTargets[targetName.toLowerCase()] = true
    }
    return result
}
// 字典关联的附加条件（如 category_code=case_status）必须进入 NiFi 查询，
// 否则同一通用码表会匹配到其他业务分类的错误表述。
var dictionaryLookupConditions = (relation) => {
    var conditions = []
    for (condition in (relation == null || relation.conditions == null ? [] : relation.conditions)) {
        var field = text(condition.field).trim()
        var operator = text(condition.operator).trim().toUpperCase()
        var value = text(condition.value)
        if (safeIdentifier(field) && (operator == '=' || operator == '!=' || operator == '<>'
            || operator == '>' || operator == '>=' || operator == '<' || operator == '<=')) {
            conditions.add(field + ' ' + operator + " '" + value.replace("'", "''") + "'")
        } else if (safeIdentifier(field) && (operator == 'IN' || operator == 'NOT IN')) {
            var values = []
            for (rawValue in value.split(',')) {
                var normalizedValue = text(rawValue).trim()
                if (!blank(normalizedValue)) {
                    values.add("'" + normalizedValue.replace("'", "''") + "'")
                }
            }
            if (values.size() > 0) {
                conditions.add(field + ' ' + operator + ' (' + values.join(', ') + ')')
            }
        }
    }
    return conditions
}
var readProp = (catalogId, name) => {
    if (blank(catalogId)) {
        return ''
    }
    var row = db.table('da_prop_t').where()
        .eq('parent_id', catalogId)
        .eq('prop_name', name)
        .eq('is_del', 0)
        .selectOne()
    return row == null ? '' : text(row.propValue)
}
var findCatalogBySource = (sourceTableId) => {
    var props = db.table('da_prop_t').where()
        .eq('prop_name', 'sourceTableId')
        .eq('prop_value', sourceTableId)
        .eq('is_del', 0)
        .select()
    for (prop in (props == null ? [] : props)) {
        var asset = db.table('da_catalog_t').where()
            .eq('tid', prop.parentId)
            .eq('is_del', 0)
            .selectOne()
        if (asset != null) {
            return text(prop.parentId)
        }
    }
    return ''
}
var colName = (row) => first(row, ['columnName', 'column_name'])
var readCfg = (row) => datasourceConnectionConfig('read', first(row, ['tid']), null, false)
// 数据上报/数据推送的数据文件已由上游落入 ODS 非结构化存储。
// 创建任务时读取 fjghcc 域绑定的 FTP/SFTP，不能把其 api 类型误作数据拉取 API。
var pushAccessMode = (datasource, cfg) => {
    var mode = first(cfg, ['accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode'])
    if (blank(mode)) {
        var pool = parseGovernanceConfig(first(datasource, ['poolCfg', 'pool_cfg']))
        mode = first(pool, ['accessMode', 'access_mode', 'dataAccessMode', 'data_access_mode'])
    }
    return text(mode).trim().toLowerCase()
}
var trimSlashes = (value) => {
    var result = text(value).trim()
    while (result.startsWith('/')) { result = result.substring(1) }
    while (result.endsWith('/')) { result = result.substring(0, result.length() - 1) }
    return result
}
var resolveOdsPushFtp = (logicalDatasourceId, sourceTableName) => {
    var binding = db.table('dwm_center_layer_source_t').where()
        .eq('target_type', 'domain').eq('layer_code', 'fjghcc').eq('is_del', 0)
        .orderBy('updated_time', 'desc').selectOne()
    var bindingDatasourceId = binding == null ? '' : first(binding, ['datasourceId', 'datasource_id'])
    if (blank(bindingDatasourceId)) {
        exit 400, '资源中心 ODS 数据域 fjghcc 未绑定 FTP/SFTP 数据源'
    }
    var storageId = text(bindingDatasourceId).trim()
    var storage = db.table('db_datasource_t').where().eq('tid', storageId).eq('is_del', 0).selectOne()
    if (storage == null) { exit 404, '资源中心 ODS 数据域 fjghcc 绑定的数据源不存在' }
    // readCfg is the same normalized/decrypted connection object used by normal task sources.
    // Avoid re-merging heterogeneous config objects here: FTP fields are already authoritative in it.
    var cfg = readCfg(storage)
    if (cfg == null) { cfg = {} }
    var dbType = text(first(storage, ['dbType', 'db_type', 'databaseType', 'database_type'])).trim().toUpperCase()
    var protocol = text(first(cfg, ['ftpProtocol', 'ftp_protocol', 'protocol'])).trim().toLowerCase()
    if ((dbType != 'FTP' && dbType != 'SFTP') || (protocol != '' && protocol != 'ftp' && protocol != 'sftp')) {
        exit 400, '资源中心 ODS 数据域 fjghcc 绑定的数据源不是 FTP/SFTP 类型'
    }
    if (protocol == '') { protocol = dbType == 'SFTP' ? 'sftp' : 'ftp' }
    var root = text(first(cfg, ['ftpPath', 'ftp_path', 'remotePath', 'path'])).trim()
    while (root.endsWith('/')) { root = root.substring(0, root.length() - 1) }
    var sourceId = trimSlashes(logicalDatasourceId)
    var table = trimSlashes(sourceTableName)
    if (blank(root) || blank(sourceId) || blank(table) || sourceId.indexOf('..') >= 0 || table.indexOf('..') >= 0) {
        exit 400, 'ODS FTP 根路径、来源数据源或来源表名配置无效'
    }
    if (!root.startsWith('/')) { root = '/' + root }
    return { datasourceId: storageId, dbType: dbType, protocol: protocol, config: cfg, remotePath: root + '/' + sourceId + '/' + table + '/' }
}
// Dictionary records are allowed to live in another registered datasource.
// Persist that exact connection in the lookup DSL so the canvas compiler does not
// accidentally execute a dictionary query against the source table database.
var dictionaryLookupSource = (relation) => {
    if (relation == null) {
        return { config: {}, schema: '' }
    }
    var dictionaryTableId = text(relation.dictionaryTableId).trim()
    var dictionaryTable = blank(dictionaryTableId) ? null : db.table('db_table_t').where()
        .eq('tid', dictionaryTableId)
        .eq('is_del', 0)
        .selectOne()
    if (dictionaryTable == null) {
        return { config: {}, schema: '' }
    }
    var datasourceId = text(first(dictionaryTable, ['datasourceId', 'datasource_id'])).trim()
    var datasource = blank(datasourceId) ? null : db.table('db_datasource_t').where()
        .eq('tid', datasourceId)
        .eq('is_del', 0)
        .selectOne()
    if (datasource == null) {
        return { config: {}, schema: '' }
    }
    var cfg = readCfg(datasource)
    var dbType = first(datasource, ['dbType', 'db_type', 'databaseType', 'database_type'])
    var database = first(cfg, ['serviceName', 'sid', 'database', 'dbName', 'dbMetaDbName'])
    var schema = first(cfg, ['defaultSchema', 'schema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
    if (blank(schema)) {
        schema = first(dictionaryTable, ['schemaName', 'schema_name', 'owner', 'tableSchema', 'table_schema'])
    }
    return {
        config: {
            datasourceId: datasourceId,
            dbType: dbType,
            host: first(cfg, ['host', 'dbMetaIp']),
            port: first(cfg, ['port', 'dbMetaPort']),
            database: database,
            jdbcUrl: jdbcUrl(dbType, cfg, datasource),
            username: first(cfg, ['username', 'dbMetaUser']),
            password: first(cfg, ['password', 'dbMetaPassword']),
            defaultSchema: schema
        },
        schema: schema
    }
}
// A registration may be re-probed and receive a new table ID while an already
// saved field relation still carries the previous ID. Resolve the current,
// active registered dictionary by name and datasource before rejecting a task.
var dictionaryLookupFallback = (relation, sourceTable) => {
    if (relation == null || sourceTable == null) {
        return { config: {}, schema: '' }
    }
    var sourceDatasourceId = text(first(sourceTable, ['datasourceId', 'datasource_id'])).trim()
    var candidates = db.table('db_table_t').where()
        .eq('table_name', relation.dictionaryTable)
        .eq('is_del', 0)
        .select()
    var dictionaryTable = null
    for (candidate in (candidates == null ? [] : candidates)) {
        var candidateDatasourceId = text(first(candidate, ['datasourceId', 'datasource_id'])).trim()
        if (!blank(sourceDatasourceId) && candidateDatasourceId == sourceDatasourceId) {
            dictionaryTable = candidate
            break
        }
        if (dictionaryTable == null) {
            dictionaryTable = candidate
        }
    }
    if (dictionaryTable == null) {
        return { config: {}, schema: '' }
    }
    var datasourceId = text(first(dictionaryTable, ['datasourceId', 'datasource_id'])).trim()
    var datasource = blank(datasourceId) ? null : db.table('db_datasource_t').where()
        .eq('tid', datasourceId)
        .eq('is_del', 0)
        .selectOne()
    if (datasource == null) {
        return { config: {}, schema: '' }
    }
    var cfg = readCfg(datasource)
    var dbType = first(datasource, ['dbType', 'db_type', 'databaseType', 'database_type'])
    var schema = first(cfg, ['defaultSchema', 'schema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
    if (blank(schema)) {
        schema = first(dictionaryTable, ['schemaName', 'schema_name', 'owner', 'tableSchema', 'table_schema'])
    }
    return {
        config: {
            datasourceId: datasourceId,
            dbType: dbType,
            host: first(cfg, ['host', 'dbMetaIp']),
            port: first(cfg, ['port', 'dbMetaPort']),
            database: first(cfg, ['serviceName', 'sid', 'database', 'dbName', 'dbMetaDbName']),
            jdbcUrl: jdbcUrl(dbType, cfg, datasource),
            username: first(cfg, ['username', 'dbMetaUser']),
            password: first(cfg, ['password', 'dbMetaPassword']),
            defaultSchema: schema
        },
        schema: schema,
        tableId: text(dictionaryTable.tid)
    }
}
var normalizeSourceKey = (dbType, cfg) => {
    var type = text(dbType).trim().toUpperCase()
    if (type == 'MYSQL') { return 'source.mysql' }
    if (type == 'TDSQL_MYSQL') { return 'source.tdsql-mysql' }
    if (type == 'POSTGRES' || type == 'POSTGRESQL') { return 'source.postgresql' }
    if (type == 'GAUSSDB' || type == 'OPENGAUSS') { return 'source.gaussdb' }
    if (type == 'TDSQL_PG') { return 'source.tdsql-pg' }
    if (type == 'ORACLE' || type == 'ORACLE12' || type == 'ORACLE_12') { return 'source.oracle' }
    if (type == 'SQLSERVER' || type == 'MSSQL' || type == 'MS_SQL') { return 'source.sqlserver' }
    if (type == 'MARIADB') { return 'source.mariadb' }
    if (type == 'DB2') { return 'source.db2' }
    if (type == 'DM' || type == 'DAMENG') { return 'source.dameng' }
    if (type == 'KINGBASE') { return 'source.kingbase' }
    if (type == 'GBASE8A') { return 'source.gbase8a' }
    if (type == 'GBASE8S') { return 'source.gbase8s' }
    if (type == 'OSCAR') { return 'source.oscar' }
    if (type == 'HIGHGO') { return 'source.highgo' }
    if (type == 'HAILIANG' || type == 'VASTBASE') { return 'source.hailiang' }
    if (type == 'OCEANBASE') { return 'source.oceanbase' }
    if (type == 'CLICKHOUSE') { return 'source.clickhouse' }
    if (type == 'ELASTICSEARCH') { return 'source.elasticsearch' }
    if (type == 'FTP' || type == 'SFTP') {
        var ftpProtocol = text(first(cfg, ['ftpProtocol', 'ftp_protocol', 'protocol'])).trim().toLowerCase()
        return type == 'SFTP' || ftpProtocol == 'sftp' ? 'source.sftp' : 'source.ftp'
    }
    if (type == 'MINIO') { return 'source.minio' }
    return 'source.mysql'
}
var normalizeSinkType = (dbType) => {
    var type = text(dbType).trim().toUpperCase()
    if (type == 'DM' || type == 'DAMENG') { return 'DM' }
    if (type == 'MYSQL' || type == 'TDSQL_MYSQL') { return 'MySQL' }
    if (type == 'POSTGRES' || type == 'POSTGRESQL' || type == 'TDSQL_PG' || type == 'GAUSSDB' || type == 'OPENGAUSS') { return 'PostgreSQL' }
    if (type == 'ORACLE' || type == 'ORACLE12' || type == 'ORACLE_12') { return 'Oracle' }
    if (type == 'SQLSERVER' || type == 'MSSQL' || type == 'MS_SQL') { return 'SQLSERVER' }
    if (type == 'KINGBASE') { return 'KINGBASE' }
    if (type == 'GBASE8A') { return 'GBASE8A' }
    if (type == 'GBASE8S') { return 'GBASE8S' }
    if (type == 'OSCAR') { return 'OSCAR' }
    if (type == 'HIGHGO') { return 'HIGHGO' }
    if (type == 'OCEANBASE') { return 'OCEANBASE' }
    if (type == 'CLICKHOUSE') { return 'CLICKHOUSE' }
    if (type == 'ELASTICSEARCH') { return 'ELASTICSEARCH' }
    return blank(dbType) ? 'MySQL' : text(dbType)
}
// Huawei MRS Hive is a server-managed target.  Its registered datasource
// intentionally has no browser-side jdbc_url, so the generated DSL must use
// the dedicated Hive sink rather than falling into the generic JDBC sink.
var isServerManagedHuaweiMrsHive = (dbType, cfg) => {
    if (text(dbType).trim().toUpperCase() != 'HIVE') { return false }
    var mode = text(first(cfg, ['metadataAccessMode', 'metadata_access_mode'])).trim().toLowerCase()
    var connectionMode = text(first(cfg, ['hiveConnectionMode', 'hive_connection_mode'])).trim().toLowerCase()
    var profile = text(first(cfg, ['hiveProfile', 'hive_profile'])).trim()
    return mode == 'server-managed-mrs' || connectionMode == 'huawei-mrs' || !blank(profile)
}
var jdbcUrl = (dbType, cfg, row) => {
    var url = first(cfg, ['jdbcURL', 'jdbcUrl', 'url'])
    if (!blank(url)) { return text(url) }
    var type = text(dbType).trim().toUpperCase()
    var host = first(cfg, ['host', 'dbMetaIp']) || first(row, ['host', 'db_meta_ip']) || '127.0.0.1'
    var port = first(cfg, ['port', 'dbMetaPort']) || first(row, ['port', 'db_meta_port'])
    var database = first(cfg, ['database', 'serviceName', 'sid', 'dbName', 'dbMetaDbName']) || first(row, ['dbName', 'db_name']) || ''
    if (type == 'DM' || type == 'DAMENG') { return 'jdbc:dm://' + host + ':' + (blank(port) ? '5236' : port) + '/' + database }
    if (type == 'ORACLE' || type == 'ORACLE12' || type == 'ORACLE_12') {
        var connectionType = text(first(cfg, ['connectionType', 'jdbcType'])).trim().toUpperCase()
        if (connectionType == 'SID') {
            return 'jdbc:oracle:thin:@' + host + ':' + (blank(port) ? '1521' : port) + ':' + database
        }
        return 'jdbc:oracle:thin:@//' + host + ':' + (blank(port) ? '1521' : port) + '/' + database
    }
    if (type == 'KINGBASE') { return 'jdbc:kingbase8://' + host + ':' + (blank(port) ? '54321' : port) + '/' + database }
    if (type == 'POSTGRES' || type == 'POSTGRESQL') { return 'jdbc:postgresql://' + host + ':' + (blank(port) ? '5432' : port) + '/' + database }
    return 'jdbc:mysql://' + host + ':' + (blank(port) ? '3306' : port) + '/' + database + '?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
}
var columnList = (rows) => {
    var list = []
    for (c in (rows == null ? [] : rows)) {
        list.add({
            columnName: colName(c),
            columnComment: first(c, ['columnComment', 'column_comment', 'comment']),
            dataType: first(c, ['dataType', 'data_type', 'columnType', 'column_type']),
            primaryKey: first(c, ['primaryKey', 'primary_key', 'isPk', 'is_pk']),
            isUnique: first(c, ['isUnique', 'is_unique', 'unique', 'uk']),
            ordinalPosition: first(c, ['ordinalPosition', 'ordinal_position'])
        })
    }
    return list
}
var odsUuidExpression = "CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)"
var odsNowExpression = "LOCALTIMESTAMP"
var isOdsSystemColumn = (name) => {
    var n = text(name).toUpperCase()
    return n == 'ODS_UUID' || n == 'ODS_RKSJ' || n == 'ODS_GXSJ'
}
var odsSystemMapping = (name) => {
    var n = text(name).toUpperCase()
    if (n == 'ODS_UUID') {
        return {
            expression: odsUuidExpression,
            to: '/ODS_UUID',
            comment: 'NiFi生成正数ODS主键（秒级时间戳加随机尾数）'
        }
    }
    if (n == 'ODS_RKSJ') {
        return {
            expression: odsNowExpression,
            to: '/ODS_RKSJ',
            comment: 'NiFi写入当前入库时间'
        }
    }
    if (n == 'ODS_GXSJ') {
        return {
            expression: odsNowExpression,
            to: '/ODS_GXSJ',
            comment: 'NiFi写入当前更新时间'
        }
    }
    return null
}
var isStableBusinessKey = (column) => {
    var name = text(colName(column)).toUpperCase()
    if (name == 'ODS_UUID') { return false }
    var pk = text(first(column, ['primaryKey', 'primary_key', 'isPrimaryKey', 'is_primary_key', 'isPk', 'is_pk']))
    var unique = text(first(column, ['isUnique', 'is_unique', 'unique', 'uk']))
    return pk == '1' || pk.toLowerCase() == 'true' || unique == '1' || unique.toLowerCase() == 'true'
}
var stableBusinessKeys = (rows) => {
    var keys = []
    for (c in (rows == null ? [] : rows)) {
        if (isStableBusinessKey(c)) { keys.add(colName(c)) }
    }
    return keys
}
var firstPk = (rows) => {
    var keys = stableBusinessKeys(rows)
    return keys.size() == 0 ? '' : keys[0]
}
// NiFi's ExecuteSQLRecord evaluates one split FlowFile at a time. JSON_TABLE
// retains token position and duplicates; unknown codes remain visible when at
// least one code resolves, matching the registration preview contract.
var multiSqlLiteral = (value) => "'" + text(value).replace("'", "''") + "'"
var mysqlMultiValueLookupSql = (targetName, dbType, dictionaryRows, separator) => {
    var dialect = text(dbType).trim().toUpperCase()
    if (dialect != 'MYSQL') {
        exit 400, '多值翻译目前只支持 MySQL 8 字典或来源数据源；请勿按单值规则部署'
    }
    var delimiter = text(separator)
    if (blank(delimiter) || delimiter.length() > 8 || delimiter.indexOf('"') >= 0
        || delimiter.indexOf('\\') >= 0) {
        exit 400, '多值翻译分隔符须为 1–8 个非引号、非反斜杠字符'
    }
    return "SELECT CASE WHEN SUM(CASE WHEN dict.dict_name IS NOT NULL AND dict.dict_name <> '' "
        + "THEN 1 ELSE 0 END) = 0 THEN NULL ELSE "
        + 'GROUP_CONCAT(COALESCE(NULLIF(dict.dict_name, \'\'), TRIM(tokens.code)) '
        + 'ORDER BY tokens.ord SEPARATOR ' + multiSqlLiteral(delimiter) + ') END AS ' + targetName
        + " FROM JSON_TABLE(CONCAT('[', REPLACE(JSON_QUOTE(?), " + multiSqlLiteral(delimiter)
        + ", CHAR(34,44,34)), ']'), '$[*]' COLUMNS (ord FOR ORDINALITY, "
        + "code VARCHAR(1024) PATH '$')) tokens LEFT JOIN " + dictionaryRows
        + " dict ON CAST(dict.dict_code AS BINARY) = CAST(TRIM(tokens.code) AS BINARY) "
        + "WHERE TRIM(tokens.code) <> ''"
}
var mysqlStandardRows = (values) => {
    var rows = []
    for (value in values) {
        rows.add('SELECT ' + multiSqlLiteral(value.code) + ' AS dict_code, '
            + multiSqlLiteral(value.label) + ' AS dict_name')
    }
    if (rows.size() == 0) { exit 400, '多值标准翻译缺少编码映射' }
    return '(' + rows.join(' UNION ALL ') + ')'
}
var mysqlDictionaryRows = (relation, dictionaryName) => {
    var conditions = dictionaryLookupConditions(relation)
    return '(SELECT ' + relation.dictionaryKeyField + ' AS dict_code, '
        + 'MAX(' + relation.dictionaryLabelField + ') AS dict_name FROM ' + dictionaryName
        + (conditions.size() == 0 ? '' : ' WHERE ' + conditions.join(' AND '))
        + ' GROUP BY ' + relation.dictionaryKeyField + ')'
}

var mappingDsl = (sourceColumns, targetColumns, sourceTable, lookupSources, transformBindings, sourceLookupConfig) => {
    var sourceNames = {}
    var relations = dictionaryRelations(sourceTable)
    for (c in (sourceColumns == null ? [] : sourceColumns)) {
        sourceNames[text(colName(c)).toLowerCase()] = colName(c)
    }
    var bindingsByTarget = {}
    for (binding in (transformBindings == null ? [] : transformBindings)) {
        var bindingTargetName = text(binding.targetField).trim()
        var bindingSourceName = text(binding.sourceField).trim()
        // A binding is persisted metadata, so it may outlive a renamed/deleted
        // column.  Ignore an incomplete binding instead of allowing it to
        // interrupt creation of the entire access task.
        if (blank(bindingTargetName) || blank(bindingSourceName)) { continue }
        bindingsByTarget[bindingTargetName.toLowerCase()] = binding
    }
    var mappings = []
    for (c in (targetColumns == null ? [] : targetColumns)) {
        var name = colName(c)
        var systemMapping = odsSystemMapping(name)
        var binding = bindingsByTarget[text(name).toLowerCase()]
        var sourceName = binding == null ? '' : text(binding.sourceField).trim()
        var relation = blank(sourceName) ? null : relations[sourceName.toLowerCase()]
        if (systemMapping != null) {
            mappings.add(systemMapping)
        } else if (relation != null && relation.relationKind == 'MANDATORY_STANDARD' && safeIdentifier(name)) {
            var standardValueMap = {}
            for (standardValue in relation.standardValues) {
                standardValueMap[standardValue.code] = standardValue.label
            }
            if (relation.multiValue) {
                mappings.add({ from: '/' + relation.sourceName, to: '/' + name,
                    lookup: { sql: mysqlMultiValueLookupSql(name, sourceLookupConfig.dbType,
                            mysqlStandardRows(relation.standardValues), relation.multiValueSeparator),
                        resultColumn: name, onMissing: 'NULL', multiValue: true,
                        multiValueSeparator: relation.multiValueSeparator, dataSource: sourceLookupConfig } })
            } else {
                mappings.add({ from: '/' + relation.sourceName, to: '/' + name,
                    transform: { fn: 'enumMap', args: [standardValueMap, null] } })
            }
        } else if (relation != null && relation.relationKind == 'DICTIONARY' && safeIdentifier(name)) {
            var dictionarySource = lookupSources[sourceName.toLowerCase()]
            if (dictionarySource == null) {
                dictionarySource = dictionaryLookupSource(relation)
            }
            if (dictionarySource == null || dictionarySource.config == null) {
                exit 400, '字段「' + relation.sourceName + '」关联的字典表未找到有效数据源配置，请检查数据源登记和字段关联配置'
            }
            var dictionaryName = qualifiedDictionaryTable(relation, dictionarySource)
            var lookupConditions = dictionaryLookupConditions(relation)
            var lookupSql = 'SELECT ' + relation.dictionaryLabelField + ' AS ' + name
                + ' FROM ' + dictionaryName + ' WHERE ' + relation.dictionaryKeyField + ' = ?'
            if (lookupConditions.size() > 0) {
                lookupSql = lookupSql + ' AND ' + lookupConditions.join(' AND ')
            }
            if (relation.multiValue) {
                lookupSql = mysqlMultiValueLookupSql(name, dictionarySource.config.dbType,
                    mysqlDictionaryRows(relation, dictionaryName), relation.multiValueSeparator)
            }
            mappings.add({
                from: '/' + relation.sourceName,
                to: '/' + name,
                lookup: {
                    sql: lookupSql,
                    resultColumn: name,
                    onMissing: 'NULL',
                    multiValue: relation.multiValue == true,
                    multiValueSeparator: relation.multiValueSeparator,
                    dataSource: dictionarySource.config
                }
            })
        } else if (!blank(name) && sourceNames[text(name).toLowerCase()] != null) {
            mappings.add({ from: '/' + name, to: '/' + name })
        }
    }
    return jsons.toJSONStr({
        version: '1.0',
        passthroughUnmapped: false,
        passthroughCaseSensitive: true,
        onMissingSource: 'NULL',
        onTypeMismatch: 'SKIP_ROW',
        mappings: mappings
    })
}

var sourceTableId = text(body?.tableId || body?.sourceTableId || body?.tid).trim()
var tenantId = tenantRuntime.id()
// Rebuilding an existing task is opt-in. Normal requests preserve a saved
// pipeline even when a newer generator can produce richer mappings.
var forceRebuild = text(body?.repair || body?.forceRebuild || body?.rebuild).trim().toLowerCase()
forceRebuild = forceRebuild == 'true' || forceRebuild == '1' || forceRebuild == 'yes'
if (blank(sourceTableId)) {
    exit 400, 'tableId不能为空'
}

var sourceTable = db.table('db_table_t').where()
    .eq('tid', sourceTableId)
    .eq('is_del', 0)
    .selectOne()
if (sourceTable == null) {
    exit 404, '来源表不存在'
}

// Validate dictionary metadata before retiring an existing task. Otherwise a
// stale relation could delete a usable task and only then report the real issue.
var validatedDictionaryLookupSources = {}
var validatedDictionaryConfig = governanceConfigOf(sourceTable)
for (field in (validatedDictionaryConfig.fields == null ? [] : validatedDictionaryConfig.fields)) {
    var relation = field.dictionaryRelation == null ? field.dictionary_relation : field.dictionaryRelation
    var relationSourceName = text(first(field, ['columnName', 'column_name'])).trim()
    var relationSourceType = text(relation == null ? '' : relation.sourceType).trim().toLowerCase()
    var standardValues = mandatoryStandardMappings(relation)
    if (relation != null && text(relation.enabled).toLowerCase() != 'false'
        && safeIdentifier(relationSourceName) && standardValues.size() > 0) {
        // Mandatory-standard mappings are embedded as a CASE/enumMap rule and
        // intentionally do not need a dictionary datasource registration.
        continue
    }
    var relationDictionaryReference = dictionaryTableReference(relation == null ? '' : relation.dictionaryTable)
    if (relation == null || text(relation.enabled).toLowerCase() == 'false' || relationSourceType == 'enum'
        || !safeIdentifier(relationSourceName) || !safeIdentifier(relationDictionaryReference.table)
        || !safeIdentifier(text(relation.dictionaryKeyField).trim()) || !safeIdentifier(text(relation.dictionaryLabelField).trim())) {
        continue
    }
    relation = {
        sourceName: relationSourceName,
        dictionaryTable: relationDictionaryReference.table,
        dictionarySchema: relationDictionaryReference.schema,
        dictionaryTableId: text(first(relation, ['dictionaryTableId', 'dictionary_table_id'])).trim(),
        dictionaryKeyField: text(relation.dictionaryKeyField).trim(),
        dictionaryLabelField: text(relation.dictionaryLabelField).trim(),
        conditions: relation.conditions == null ? [] : relation.conditions
    }
    var lookupSource = dictionaryLookupSource(relation)
    if (lookupSource == null || lookupSource.config == null || blank(lookupSource.config.datasourceId)) {
        lookupSource = dictionaryLookupFallback(relation, sourceTable)
    }
    if (lookupSource == null || lookupSource.config == null || blank(lookupSource.config.datasourceId)) {
        exit 400, '字段「' + relation.sourceName + '」关联的字典表「' + relation.dictionaryTable
            + '」未找到有效登记数据源，请先在字典关联中重新选择已登记字典表'
    }
    // Persist the repaired registration ID so the next task setup is direct.
    if (!blank(lookupSource.tableId) && relation.dictionaryTableId != lookupSource.tableId) {
        relation.dictionaryTableId = lookupSource.tableId
    }
    validatedDictionaryLookupSources[relation.sourceName.toLowerCase()] = lookupSource
}

// 一个来源表可以物化到多个目标数据源。db_table_t.source_table_id 是目标表
// 与来源表的事实关联；目录中的 targetTableId 仅作为历史单目标数据的兼容回退。
var catalogId = text(body?.catalogId || sourceTable.sourceCatalogId || sourceTable.source_catalog_id)
if (blank(catalogId)) {
    catalogId = findCatalogBySource(sourceTableId)
}
var materializedTargets = db.table('db_table_t').where()
    .eq('source_table_id', sourceTableId)
    .eq('is_del', 0)
    .select()
var targetTables = []
var targetTableIds = []
for (candidateTarget in (materializedTargets == null ? [] : materializedTargets)) {
    var candidateTargetId = text(first(candidateTarget, ['tid']))
    if (!blank(candidateTargetId) && candidateTargetId != sourceTableId) {
        targetTables.add(candidateTarget)
        targetTableIds.add(candidateTargetId)
        if (blank(catalogId)) {
            catalogId = text(first(candidateTarget, ['sourceCatalogId', 'source_catalog_id']))
        }
    }
}
if (targetTables.size() == 0) {
    var legacyTargetTableId = readProp(catalogId, 'targetTableId')
    if (!blank(legacyTargetTableId)) {
        var legacyTargetTable = db.table('db_table_t').where()
            .eq('tid', legacyTargetTableId).eq('is_del', 0).selectOne()
        if (legacyTargetTable != null) {
            targetTables.add(legacyTargetTable)
            targetTableIds.add(legacyTargetTableId)
        }
    }
}
if (targetTables.size() == 0) {
    exit 400, '请先完成物化建表，生成至少一个目标表后才能创建接入任务'
}

// A generated access task must have one mapping for every physical target
// column.  A lower count means a previous batch failed part way through and
// cannot be treated as an existing usable task.
var expectedMappingCount = 0
for (expectedTargetTable in targetTables) {
    var expectedTargetTableId = text(first(expectedTargetTable, ['tid'])).trim()
    if (!blank(expectedTargetTableId)) {
        expectedMappingCount = expectedMappingCount + db.table('db_table_column_t').where()
            .eq('table_id', expectedTargetTableId)
            .eq('is_del', 0)
            .count()
    }
}

var exists = db.table('data_access_agg_task_t').where()
    .eq('source_table_id', sourceTableId)
    .eq('is_del', 0)
    .orderBy('updated_time', 'desc')
    .selectOne()
if (exists != null) {
    var existingMappingCount = db.table('data_access_field_mapping').where()
        .eq('task_id', exists.tid)
        .eq('is_del', 0)
        .count()
    var existingPipelineId = text(exists.pipelineId || exists.pipeline_id)
    var existingPipeline = blank(existingPipelineId) ? null : db.table('nifi_pipeline_t').where()
        .eq('id', existingPipelineId)
        .eq('is_del', 0)
        .selectOne()
    var existingDsl = existingPipeline == null ? '' : text(first(existingPipeline, ['dslJson', 'dsl_json']))
    // mappings is stored as an escaped JSON string inside the pipeline DSL, so
    // quoted-token matching falsely reports a missing dataSource. Match the
    // property names themselves; a real missing lookup still triggers repair.
    var needsDictionaryLookupUpgrade = dictionaryRelations(sourceTable).size() > 0
        && (existingDsl.indexOf('lookup') < 0 || existingDsl.indexOf('dataSource') < 0)
    var needsTargetSinkUpgrade = false
    for (expectedTargetTableId in targetTableIds) {
        if (existingDsl.indexOf(text(expectedTargetTableId)) < 0) {
            needsTargetSinkUpgrade = true
            break
        }
    }
    // A task with fewer mappings than target columns is not a usable
    // user-owned task: it can only be left behind when this endpoint failed
    // during its final mapping batch. Retire that incomplete pair and recreate
    // it below. A complete task remains untouched, even when an upgrade is
    // available.
    // An explicit rebuild is an operator instruction to regenerate the
    // registration-derived draft.  This is necessary after a source datasource
    // or schema binding changes even when the previous mapping count and target
    // sink still look structurally complete.
    if (existingMappingCount < expectedMappingCount || forceRebuild) {
        db.table('data_access_agg_task_t').where().eq('tid', exists.tid).update({
            is_del: 1,
            updated_time: now
        })
        if (existingPipeline != null) {
            db.table('nifi_pipeline_t').where().eq('id', existingPipelineId).update({
                is_del: 1,
                updated_time: now
            })
        }
    } else {
    var repairReasons = []
    if (existingPipeline == null) { repairReasons.add('关联流程不存在或已删除') }
    if (blank(existingDsl)) { repairReasons.add('关联流程没有可读取的画布定义') }
    if (needsDictionaryLookupUpgrade) { repairReasons.add('字典关联配置需要显式升级') }
    if (needsTargetSinkUpgrade) { repairReasons.add('目标表关联发生变化，需要显式确认重建') }
    return {
        created: false,
        taskId: exists.tid,
        task: exists,
        targetTableIds: targetTableIds,
        repairRequired: repairReasons.size() > 0,
        repairReasons: repairReasons
    }
    }
}

var targetTable = targetTables[0]
var targetTableId = text(first(targetTable, ['tid']))

var sourceDbId = text(first(sourceTable, ['datasourceId', 'datasource_id']))
var targetDbId = text(first(targetTable, ['datasourceId', 'datasource_id']))
var sourceDb = db.table('db_datasource_t').where().eq('tid', sourceDbId).eq('is_del', 0).selectOne()
var targetDb = db.table('db_datasource_t').where().eq('tid', targetDbId).eq('is_del', 0).selectOne()
var sourceCfg = readCfg(sourceDb)
var targetCfg = readCfg(targetDb)

var sourceColumns = db.table('db_table_column_t').where()
    .eq('table_id', sourceTableId)
    .eq('is_del', 0)
    .orderBy('ordinal_position', 'asc')
    .select()
var targetColumns = db.table('db_table_column_t').where()
    .eq('table_id', targetTableId)
    .eq('is_del', 0)
    .orderBy('ordinal_position', 'asc')
    .select()
var sourceColumnMap = {}
for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
    sourceColumnMap[text(colName(sourceColumn)).toLowerCase()] = sourceColumn
}
var sourceDictionaryRelations = dictionaryRelations(sourceTable)
var dictionaryLookupSources = validatedDictionaryLookupSources

var now = new Date()
var nowMs = new Date().getTime()
var taskId = IdUtil.getSnowflakeNextIdStr()
var pipelineId = IdUtil.getSnowflakeNextIdStr()
var sourceTableName = first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
var targetTableName = first(targetTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
var taskName = (first(sourceTable, ['tableNameCn', 'table_name_cn', 'tableComment', 'table_comment']) || sourceTableName) + '接入任务'
var sourceDbType = first(sourceDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
var accessMode = pushAccessMode(sourceDb, sourceCfg)
var pushFileSource = (accessMode == 'receive' || accessMode == 'upload' || accessMode == 'push')
    ? resolveOdsPushFtp(sourceDbId, sourceTableName) : null
var targetDbType = first(targetDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
var sourceService = first(sourceCfg, ['serviceName', 'sid', 'database', 'dbName', 'dbMetaDbName'])
var sourceConnectionType = text(first(sourceCfg, ['connectionType', 'jdbcType'])).trim().toUpperCase()
if (sourceConnectionType != 'SID') { sourceConnectionType = 'SERVICE_NAME' }
var sourceSchema = first(sourceCfg, ['defaultSchema', 'schema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
if (blank(sourceSchema)) {
    sourceSchema = first(sourceTable, ['schemaName', 'schema_name', 'owner', 'tableSchema', 'table_schema'])
}
var sourceJdbcUrl = jdbcUrl(sourceDbType, sourceCfg, sourceDb)

// A valid explicit target binding is preferred.  Imported metadata may carry
// an incomplete binding array though, so ensure every registered dictionary or
// mandatory-standard source has its materialized <source>_cn output column.
var missingTranslationColumns = []
for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
    var sourceName = text(colName(sourceColumn))
    var relation = sourceDictionaryRelations[sourceName.toLowerCase()]
    var translationName = relation == null ? '' : relation.sourceName + '_cn'
    var targetColumnNames = {}
    for (targetColumn in (targetColumns == null ? [] : targetColumns)) {
        targetColumnNames[text(colName(targetColumn)).toLowerCase()] = true
    }
    if (relation != null && targetColumnNames[translationName.toLowerCase()] == null) {
        missingTranslationColumns.add({
            columnName: translationName,
            sourceColumn: sourceColumn
        })
    }
}
if (missingTranslationColumns.size() > 0) {
    if (targetTableId == sourceTableId) {
        exit 400, '目标表未物化为独立表，无法添加字典翻译字段，请先重新物化建表'
    }
    var missingTranslationNames = []
    for (translationColumn in missingTranslationColumns) {
        missingTranslationNames.add(translationColumn.columnName)
    }
    var addedTranslationNames = targetTableSchema.ensureTranslationColumns({
        jdbcUrl: jdbcUrl(targetDbType, targetCfg, targetDb),
        username: first(targetCfg, ['username', 'dbMetaUser']),
        password: first(targetCfg, ['password', 'dbMetaPassword'])
    }, targetTableName, missingTranslationNames)
    if (addedTranslationNames.size() > 0) {
        var addedTranslationMap = {}
        for (addedTranslationName in addedTranslationNames) {
            addedTranslationMap[text(addedTranslationName).toLowerCase()] = true
        }
        var translationMetadata = []
        var nextOrdinal = targetColumns == null ? 1 : targetColumns.size() + 1
        for (translationColumn in missingTranslationColumns) {
            var translationName = translationColumn.columnName
            if (addedTranslationMap[translationName.toLowerCase()] != null) {
                var sourceColumn = translationColumn.sourceColumn
                translationMetadata.add({
                    tid: IdUtil.getSnowflakeNextIdStr(),
                    table_id: targetTableId,
                    column_name: translationName,
                    column_comment: text(first(sourceColumn, ['columnComment', 'column_comment'])).trim() + '翻译',
                    data_type: 'varchar',
                    column_type: 'varchar(255)',
                    length: 255,
                    precision_length: 0,
                    scale: 0,
                    nullable: 1,
                    default_value: null,
                    primary_key: 0,
                    auto_increment: 0,
                    is_unique: 0,
                    indexed: 0,
                    ordinal_position: nextOrdinal,
                    charset: null,
                    collation: null,
                    extra: null,
                    source_table_column_id: sourceColumn.tid,
                    is_del: 0,
                    created_time: now,
                    updated_time: now
                })
                nextOrdinal = nextOrdinal + 1
            }
        }
        if (translationMetadata.size() > 0) {
            metadataAsset.batchInsertTableColumns(translationMetadata)
            db.table('db_table_t').where().eq('tid', targetTableId).eq('is_del', 0)
                .update({ field_count: targetColumns.size() + translationMetadata.size(), updated_time: now })
            targetColumns = db.table('db_table_column_t').where()
                .eq('table_id', targetTableId)
                .eq('is_del', 0)
                .orderBy('ordinal_position', 'asc')
                .select()
        }
    }
}
// This target-specific binding is authoritative for field enhancement.  It is
// resolved after any legacy metadata refresh so the canvas sees final columns.
var targetFieldBindings = targetFieldTransformBindings(sourceTable, targetTable, targetColumns)
var targetBindingByField = {}
for (targetFieldBinding in targetFieldBindings) {
    targetBindingByField[text(targetFieldBinding.targetField).toLowerCase()] = targetFieldBinding
}
var sourceColumnsForNifi = columnList(sourceColumns)
var targetColumnsForNifi = columnList(targetColumns)
var primarySourceKeys = []
var primaryTargetKeys = []
for (targetColumn in (targetColumns == null ? [] : targetColumns)) {
    var targetName = text(colName(targetColumn))
    var sourceColumn = sourceColumnMap[targetName.toLowerCase()]
    if (sourceColumn == null) {
        var sourceColumnId = text(first(targetColumn, ['sourceTableColumnId', 'source_table_column_id']))
        if (!blank(sourceColumnId)) {
            for (candidateSource in (sourceColumns == null ? [] : sourceColumns)) {
                if (text(candidateSource.tid) == sourceColumnId) {
                    sourceColumn = candidateSource
                    break
                }
            }
        }
    }
    if (sourceColumn != null && isStableBusinessKey(sourceColumn) && isStableBusinessKey(targetColumn)) {
        primarySourceKeys.add(colName(sourceColumn))
        primaryTargetKeys.add(targetName)
    }
}
var sourcePrimaryKeys = primarySourceKeys.join(',')
var targetPrimaryKeys = primaryTargetKeys.join(',')
var canUpsert = !blank(sourcePrimaryKeys) && !blank(targetPrimaryKeys)

// 生成 data-element-nifi 可读取的基础画布草稿。来源和字段映射只保留一份，
// 每个已物化目标表生成一个独立写入节点，字段映射后的 FlowFile 同时分发到所有目标。
var sourceNodeId = 'source-' + sourceTableId
var mapNodeId = 'map-' + taskId
var sourceNodeConfig = {
    host: first(sourceCfg, ['host', 'dbMetaIp']),
    port: first(sourceCfg, ['port', 'dbMetaPort']),
    database: sourceService,
    sid: sourceService,
    serviceName: sourceService,
    connectionType: sourceConnectionType,
    jdbcUrl: sourceJdbcUrl,
    defaultSchema: sourceSchema,
    schema: sourceSchema,
    username: first(sourceCfg, ['username', 'dbMetaUser']),
    password: first(sourceCfg, ['password', 'dbMetaPassword']),
    sourceDbId: sourceDbId,
    sourceDbName: sourceDb == null ? '' : sourceDb.dbName,
    sourceTableId: sourceTableId,
    sourceTableName: sourceTableName,
    table: sourceTableName,
    sourceColumns: sourceColumnsForNifi,
    incrementalColumn: firstPk(sourceColumns),
    schedulingPeriod: '60 sec',
    syncMode: 'FULL_THEN_INCR',
    initialStrategy: 'START_AT_BEGINNING',
    lookbackSeconds: 5,
    safetyLagSeconds: 1,
    batchSize: 5000,
    maxRowsPerRun: 200000,
    softDeleteEnabled: false,
    deleteReconcileStrategy: 'NONE'
}
// 普通 FTP/SFTP 探查接入也必须使用登记的连接，而不是 JDBC 默认配置。
// 一条任务只读取其来源数据集，不能把目录里的其它表一起送入同一个目标。
if (pushFileSource == null && (text(sourceDbType).trim().toLowerCase() == 'ftp'
        || text(sourceDbType).trim().toLowerCase() == 'sftp')) {
    var directProtocol = text(first(sourceCfg, ['ftpProtocol', 'ftp_protocol', 'protocol'])).trim().toLowerCase()
    if (blank(directProtocol)) { directProtocol = text(sourceDbType).trim().toLowerCase() == 'sftp' ? 'sftp' : 'ftp' }
    var directPort = first(sourceCfg, ['ftpPort', 'ftp_port', 'port'])
    if (blank(directPort)) { directPort = directProtocol == 'sftp' ? 22 : 21 }
    sourceNodeConfig = {
        protocol: directProtocol,
        hostname: first(sourceCfg, ['ftpHost', 'ftp_host', 'hostname', 'host']),
        port: directPort,
        username: first(sourceCfg, ['ftpUsername', 'ftp_username', 'username']),
        password: first(sourceCfg, ['ftpPassword', 'ftp_password', 'password']),
        remotePath: first(sourceCfg, ['ftpPath', 'ftp_path', 'remotePath', 'path']) || '/',
        fileFilterRegex: '(?i)^\\Q' + text(sourceTableName) + '\\E\\.(csv|json|xlsx|xls)$',
        passiveMode: first(sourceCfg, ['ftpPassiveMode', 'ftp_passive_mode', 'passiveMode']),
        schedulingPeriod: '60 sec', syncMode: 'FULL',
        sourceDbId: sourceDbId, registeredDatasourceId: sourceDbId,
        sourceDbName: sourceDb == null ? '' : sourceDb.dbName,
        sourceTableId: sourceTableId, sourceTableName: sourceTableName, table: sourceTableName,
        sourceColumns: sourceColumnsForNifi, fileHeaderMode: 'physical'
    }
}
if (pushFileSource != null) {
    var ftpCfg = pushFileSource.config
    var ftpPort = first(ftpCfg, ['ftpPort', 'ftp_port', 'port'])
    if (blank(ftpPort)) { ftpPort = pushFileSource.protocol == 'sftp' ? 22 : 21 }
    var ftpFileFilter = first(ftpCfg, ['ftpFilePattern', 'ftp_file_pattern', 'fileFilterRegex', 'filePattern'])
    if (blank(ftpFileFilter)) { ftpFileFilter = '.*' }
    sourceNodeConfig = {
        protocol: pushFileSource.protocol,
        hostname: first(ftpCfg, ['ftpHost', 'ftp_host', 'hostname', 'host']),
        port: ftpPort,
        username: first(ftpCfg, ['ftpUsername', 'ftp_username', 'username']),
        password: first(ftpCfg, ['ftpPassword', 'ftp_password', 'password']),
        remotePath: pushFileSource.remotePath,
        fileFilterRegex: ftpFileFilter,
        passiveMode: first(ftpCfg, ['ftpPassiveMode', 'ftp_passive_mode', 'passiveMode']),
        schedulingPeriod: '60 sec', syncMode: 'FULL',
        sourceDbId: sourceDbId, sourceDbName: sourceDb == null ? '' : sourceDb.dbName,
        sourceTableId: sourceTableId, sourceTableName: sourceTableName, table: sourceTableName,
        sourceColumns: sourceColumnsForNifi,
        // 上传报送模板的 CSV/Excel 首行采用登记字段中文名；JSON 仍使用物理英文键。
        fileHeaderMode: accessMode == 'upload' ? 'registered-chinese' : 'physical',
        registeredDatasourceId: pushFileSource.datasourceId,
        logicalSourceDatasourceId: sourceDbId, pushStorageDomain: 'fjghcc'
    }
}
// API 拉取的请求、分页、增量与调度来自第一步的同一条 API 定义。这里仅在
// 明确创建任务时读取并写入 NiFi 画布；不在第二步新增任何规则编辑入口。
if (pushFileSource == null && text(sourceDbType).trim().toLowerCase() == 'api') {
    var apiRuntimeConfig = apiPullConfig.resolvePipelineSource(sourceDbId, text(sourceTableName))
    for (apiKey in apiRuntimeConfig) { sourceNodeConfig[apiKey] = apiRuntimeConfig[apiKey] }
    sourceNodeConfig.sourceDbId = sourceDbId
    sourceNodeConfig.sourceDbName = sourceDb == null ? '' : sourceDb.dbName
    sourceNodeConfig.sourceTableId = sourceTableId
    sourceNodeConfig.sourceTableName = sourceTableName
    sourceNodeConfig.table = sourceTableName
    sourceNodeConfig.sourceColumns = sourceColumnsForNifi
}
// MinIO is an S3-compatible object store, not a JDBC database.  Preserve the
// object-storage connection and the registered object key for the MinIO canvas
// compiler; otherwise the generic MySQL fallback creates an empty task.
if (text(sourceDbType).trim().toLowerCase() == 'minio') {
    var minioEndpoint = text(first(sourceCfg, ['minioEndpoint', 'endpoint', 'url'])).trim()
    if (blank(minioEndpoint)) {
        var minioUseSsl = text(first(sourceCfg, ['minioUseSSL', 'useSSL'])).trim().toLowerCase() == 'true'
        minioEndpoint = (minioUseSsl ? 'https://' : 'http://')
            + text(first(sourceCfg, ['host', 'dbMetaIp'])).trim() + ':'
            + text(first(sourceCfg, ['port', 'dbMetaPort'])).trim()
    }
    var minioObjectKey = text(first(sourceTable, ['tableComment', 'table_comment'])).trim()
    if (minioObjectKey.indexOf('MinIO对象 ') == 0) {
        minioObjectKey = minioObjectKey.substring(8)
    } else {
        minioObjectKey = ''
    }
    sourceNodeConfig.minioEndpoint = minioEndpoint
    sourceNodeConfig.minioAccessKey = first(sourceCfg, ['minioAccessKey', 'accessKey', 'username', 'dbMetaUser'])
    sourceNodeConfig.minioSecretKey = first(sourceCfg, ['minioSecretKey', 'secretKey', 'password', 'dbMetaPassword'])
    sourceNodeConfig.minioBucket = first(sourceCfg, ['minioBucket', 'bucket', 'database', 'dbName', 'serviceName'])
    sourceNodeConfig.minioPrefix = first(sourceCfg, ['minioPrefix', 'objectPrefix', 'prefix'])
    sourceNodeConfig.minioFilePattern = first(sourceCfg, ['minioFilePattern', 'filePattern', 'fileFilterRegex'])
    sourceNodeConfig.minioObjectKey = minioObjectKey
    sourceNodeConfig.syncMode = 'FULL'
}
var dslNodes = [
    {
        id: sourceNodeId,
        manifestKey: pushFileSource == null ? normalizeSourceKey(sourceDbType, sourceCfg) : normalizeSourceKey(pushFileSource.dbType, pushFileSource.config),
        label: pushFileSource == null ? '来源表' : 'FTP 文件来源',
        category: 'source',
        x: 0,
        y: 120,
        config: sourceNodeConfig
    },
    {
        id: mapNodeId,
        // New access tasks use the unified component by default.  It remains
        // compatible with the existing mapping DSL while adding dictionary
        // translation, standard-format processing and source-SQL pushdown.
        manifestKey: 'transform.field-enrichment',
        label: '字段增强',
        category: 'transform',
        x: 540,
        y: 120,
        config: {
            sourceTableId: sourceTableId,
            targetTableId: targetTableId,
            executionMode: 'AUTO',
            mappings: mappingDsl(sourceColumns, targetColumns, sourceTable, dictionaryLookupSources,
                targetFieldBindings, { datasourceId: sourceDbId, dbType: sourceDbType })
        }
    }
]
var dslEdges = [{ id: 'edge-source-map', source: sourceNodeId, target: mapNodeId, outlet: null }]
var targetSummaries = []
for (targetIndex, targetCandidate in targetTables) {
    var candidateTargetId = text(first(targetCandidate, ['tid']))
    var candidateTargetDbId = text(first(targetCandidate, ['datasourceId', 'datasource_id']))
    var candidateTargetDb = db.table('db_datasource_t').where()
        .eq('tid', candidateTargetDbId).eq('is_del', 0).selectOne()
    if (candidateTargetDb == null) {
        exit 404, '目标数据源不存在：' + candidateTargetDbId
    }
    var candidateTargetCfg = readCfg(candidateTargetDb)
    var candidateTargetDbType = first(candidateTargetDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
    var candidateTargetTableName = first(targetCandidate, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
    var candidateTargetColumns = db.table('db_table_column_t').where()
        .eq('table_id', candidateTargetId).eq('is_del', 0)
        .orderBy('ordinal_position', 'asc').select()
    var candidateTargetKeys = []
    for (candidateTargetColumn in (candidateTargetColumns == null ? [] : candidateTargetColumns)) {
        var candidateTargetName = text(colName(candidateTargetColumn))
        var matchingSourceColumn = sourceColumnMap[candidateTargetName.toLowerCase()]
        // 写入幂等性由目标表的业务主键/唯一键决定。来源表的登记元数据
        // 可能尚未标记 primaryKey（例如历史探查数据），但只要该目标键已有
        // 对应的来源字段映射，就可以安全地作为 UPSERT 键，不能退回 INSERT。
        if (matchingSourceColumn != null && isStableBusinessKey(candidateTargetColumn)) {
            candidateTargetKeys.add(candidateTargetName)
        }
    }
    var candidateTargetPrimaryKeys = candidateTargetKeys.join(',')
    var candidateSinkId = 'sink-' + candidateTargetId
    var candidateIsHuaweiMrsHive = isServerManagedHuaweiMrsHive(candidateTargetDbType, candidateTargetCfg)
    var candidateSinkManifestKey = candidateIsHuaweiMrsHive ? 'sink.hive' : 'sink.jdbc'
    var candidateSinkConfig = {}
    if (candidateIsHuaweiMrsHive) {
        var candidateHiveDatabase = first(candidateTargetCfg, ['database', 'dbName', 'dbMetaDbName', 'serviceName'])
        if (blank(candidateHiveDatabase)) {
            exit 400, '华为 MRS Hive 目标数据源缺少已登记的 Hive 数据库名：' + candidateTargetDbId
        }
        candidateSinkConfig = {
            // Keep only the tenant-owned datasource reference and the logical
            // MRS profile here.  The compiler resolves keytab, krb5.conf and
            // HiveServer2/ZooKeeper settings on the server at deployment time.
            dbType: 'HIVE',
            targetDbId: candidateTargetDbId,
            targetDbName: candidateTargetDb.dbName,
            targetTableId: candidateTargetId,
            hiveProfile: first(candidateTargetCfg, ['hiveProfile', 'hive_profile']) || 'default',
            hiveConnectionMode: 'huawei-mrs',
            metadataAccessMode: 'server-managed-mrs',
            database: candidateHiveDatabase,
            targetTableName: candidateTargetTableName,
            table: candidateTargetTableName,
            schema: first(candidateTargetCfg, ['schema', 'defaultSchema', 'currentSchema', 'schemaName'])
                || first(targetCandidate, ['schemaName', 'schema_name'])
                || ((normalizeSinkType(candidateTargetDbType) == 'DM') ? first(candidateTargetCfg, ['database', 'databaseName']) : null),
            targetColumns: columnList(candidateTargetColumns),
            // Materialized Hive tables use the dedicated standard writer;
            // generic JDBC UPSERT is not a valid Hive write operation.
            hiveWriteMode: 'HIVE_RECORD_PUT',
            statementType: 'INSERT',
            updateKeys: ''
        }
    } else {
        candidateSinkConfig = {
            dbType: normalizeSinkType(candidateTargetDbType),
            jdbcUrl: jdbcUrl(candidateTargetDbType, candidateTargetCfg, candidateTargetDb),
            username: first(candidateTargetCfg, ['username', 'dbMetaUser']),
            password: first(candidateTargetCfg, ['password', 'dbMetaPassword']),
            targetDbId: candidateTargetDbId,
            targetDbName: candidateTargetDb.dbName,
            targetTableId: candidateTargetId,
            targetTableName: candidateTargetTableName,
            table: candidateTargetTableName,
            targetColumns: columnList(candidateTargetColumns),
            statementType: candidateTargetPrimaryKeys == '' ? 'INSERT' : 'UPSERT',
            updateKeys: candidateTargetPrimaryKeys
        }
    }
    dslNodes.add({
        id: candidateSinkId,
        manifestKey: candidateSinkManifestKey,
        label: targetTables.size() > 1 ? '目标表 ' + (targetIndex + 1) : '目标表',
        category: 'sink',
        x: 1080,
        y: 120 + targetIndex * 300,
        config: candidateSinkConfig
    })
    dslEdges.add({ id: 'edge-map-sink-' + candidateTargetId, source: mapNodeId, target: candidateSinkId, outlet: null })
    targetSummaries.add({
        targetTableId: candidateTargetId,
        targetTableName: candidateTargetTableName,
        targetDbId: candidateTargetDbId,
        targetDbName: candidateTargetDb.dbName,
        targetDbType: candidateTargetDbType
    })
}
var dsl = { version: 1, nodes: dslNodes, edges: dslEdges }
db.table('nifi_pipeline_t').primary('id').insert({
    id: pipelineId,
    tenant_id: tenantId,
    name: taskName,
    description: '由数据接入页面按来源表自动创建',
    status: 'SAVED',
    dsl_json: jsons.toJSONStr(dsl),
    dsl_version: 1,
    created_at: nowMs,
    updated_at: nowMs,
    is_del: 0
})

db.table('data_access_agg_task_t').primary('tid').insert({
    tid: taskId,
    tenant_id: tenantId,
    data_catalog_id: catalogId,
    task_name: taskName,
    task_type_name: '数据表接入',
    task_type_id: 'table_access',
    task_desc: '来源表自动创建接入任务',
    source_db_id: sourceDbId,
    source_table_id: sourceTableId,
    source_table_primary_key: sourcePrimaryKeys,
    target_db_id: targetDbId,
    target_table_id: targetTableId,
    target_table_primary_key: targetPrimaryKeys,
    schedule_strategy: 0,
    schedule_cycle: 0,
    task_status: 0,
    pipeline_id: pipelineId,
    created_time: now,
    updated_time: now,
    is_del: 0
})

var mappings = []
for (targetIndex, targetColumn in (targetColumns == null ? [] : targetColumns)) {
    var targetName = text(colName(targetColumn))
    var systemMapping = odsSystemMapping(targetName)
    if (systemMapping != null) {
        mappings.add({
            tid: IdUtil.getSnowflakeNextIdStr(),
            task_id: taskId,
            source_field: 'SYSTEM:' + targetName,
            source_field_cn: 'NiFi系统字段',
            source_field_length: 0,
            source_data_type: 'SYSTEM_EXPRESSION',
            target_field: targetName,
            target_field_cn: first(targetColumn, ['columnComment', 'column_comment']),
            target_field_length: first(targetColumn, ['length']),
            target_data_type: first(targetColumn, ['dataType', 'data_type']),
            dict_enable: 0,
            func_enable: 1,
            func_code: 'EXPRESSION',
            func_value: systemMapping.expression,
            sort_no: targetIndex,
            is_del: 0,
            created_time: now,
            updated_time: now
        })
        continue
    }
    var targetBinding = targetBindingByField[targetName.toLowerCase()]
    var dictionarySourceName = targetBinding == null ? '' : text(targetBinding.sourceField).trim()
    var dictionaryRelation = blank(dictionarySourceName) ? null
        : sourceDictionaryRelations[dictionarySourceName.toLowerCase()]
    if (dictionaryRelation != null) {
        var dictionarySourceColumn = sourceColumnMap[text(dictionaryRelation.sourceName).toLowerCase()]
        if (dictionarySourceColumn != null && dictionaryRelation.relationKind == 'MANDATORY_STANDARD') {
            var standardValueMap = {}
            for (standardValue in dictionaryRelation.standardValues) {
                standardValueMap[standardValue.code] = standardValue.label
            }
            mappings.add({
                tid: IdUtil.getSnowflakeNextIdStr(),
                task_id: taskId,
                source_field: colName(dictionarySourceColumn),
                source_field_cn: first(dictionarySourceColumn, ['columnComment', 'column_comment']),
                source_field_length: first(dictionarySourceColumn, ['length']),
                source_data_type: first(dictionarySourceColumn, ['dataType', 'data_type']),
                target_field: targetName,
                target_field_cn: first(targetColumn, ['columnComment', 'column_comment']),
                target_field_length: first(targetColumn, ['length']),
                target_data_type: first(targetColumn, ['dataType', 'data_type']),
                dict_enable: 0,
                func_enable: 1,
                func_code: 'ENUM_MAP',
                // Persist the executable map, not merely a marker.  The canvas
                // may be opened before deployment, so the enhanced component
                // must be able to restore every mandatory-standard category.
                func_value: jsons.toJSONStr({ ruleKind: 'MANDATORY_STANDARD', values: standardValueMap,
                    multiValue: dictionaryRelation.multiValue == true,
                    multiValueSeparator: dictionaryRelation.multiValueSeparator }),
                sort_no: targetIndex,
                is_del: 0,
                created_time: now,
                updated_time: now
            })
            continue
        }
        if (dictionarySourceColumn != null && dictionaryRelation.relationKind == 'DICTIONARY') {
            var dictionaryDatasource = dictionaryLookupSources[dictionarySourceName.toLowerCase()]
            if (dictionaryDatasource == null) {
                dictionaryDatasource = dictionaryLookupSource(dictionaryRelation)
            }
            if (dictionaryDatasource == null || dictionaryDatasource.config == null) {
                exit 400, '字段「' + dictionaryRelation.sourceName + '」关联的字典表未找到有效数据源配置，请检查数据源登记和字段关联配置'
            }
            var dictionaryName = qualifiedDictionaryTable(dictionaryRelation, dictionaryDatasource)
            var dictionaryConditions = dictionaryLookupConditions(dictionaryRelation)
            var dictionaryLookupSql = 'SELECT ' + dictionaryRelation.dictionaryLabelField + ' AS ' + targetName
                + ' FROM ' + dictionaryName + ' WHERE ' + dictionaryRelation.dictionaryKeyField + ' = ?'
            if (dictionaryConditions.size() > 0) {
                dictionaryLookupSql = dictionaryLookupSql + ' AND ' + dictionaryConditions.join(' AND ')
            }
            if (dictionaryRelation.multiValue) {
                dictionaryLookupSql = mysqlMultiValueLookupSql(targetName, dictionaryDatasource.config.dbType,
                    mysqlDictionaryRows(dictionaryRelation, dictionaryName),
                    dictionaryRelation.multiValueSeparator)
            }
            mappings.add({
                tid: IdUtil.getSnowflakeNextIdStr(),
                task_id: taskId,
                source_field: colName(dictionarySourceColumn),
                source_field_cn: first(dictionarySourceColumn, ['columnComment', 'column_comment']),
                source_field_length: first(dictionarySourceColumn, ['length']),
                source_data_type: first(dictionarySourceColumn, ['dataType', 'data_type']),
                target_field: targetName,
                target_field_cn: first(targetColumn, ['columnComment', 'column_comment']),
                target_field_length: first(targetColumn, ['length']),
                target_data_type: first(targetColumn, ['dataType', 'data_type']),
                dict_enable: 1,
                dict_value: dictionaryRelation.dictionaryTable,
                dict_code: dictionaryRelation.dictionaryKeyField + '->' + dictionaryRelation.dictionaryLabelField,
                func_enable: 1,
                func_code: 'LOOKUP',
                // Some deployed metadata schemas keep func_value as VARCHAR.
                // The complete executable contract belongs to the pipeline DSL
                // above; persist a compact, backward-compatible summary here
                // so the mapping batch cannot fail after the pipeline exists.
                func_value: dictionaryRelation.multiValue
                    ? jsons.toJSONStr({ ruleKind: 'DICTIONARY', table: dictionaryRelation.dictionaryTable,
                        keyField: dictionaryRelation.dictionaryKeyField,
                        labelField: dictionaryRelation.dictionaryLabelField,
                        sql: dictionaryLookupSql, multiValue: true,
                        multiValueSeparator: dictionaryRelation.multiValueSeparator,
                        dataSource: { datasourceId: dictionaryDatasource.config.datasourceId,
                            dbType: dictionaryDatasource.config.dbType } })
                    : dictionaryRelation.dictionaryTable + ':'
                        + dictionaryRelation.dictionaryKeyField + '->' + dictionaryRelation.dictionaryLabelField,
                sort_no: targetIndex,
                is_del: 0,
                created_time: now,
                updated_time: now
            })
            continue
        }
    }
    var sourceColumn = sourceColumnMap[targetName.toLowerCase()]
    if (sourceColumn == null && targetColumn.sourceTableColumnId != null) {
        for (candidateSource in (sourceColumns == null ? [] : sourceColumns)) {
            if (text(candidateSource.tid) == text(targetColumn.sourceTableColumnId)) {
                sourceColumn = candidateSource
            }
        }
    }
    if (sourceColumn == null) {
        continue
    }
    mappings.add({
        tid: IdUtil.getSnowflakeNextIdStr(),
        task_id: taskId,
        source_field: colName(sourceColumn),
        source_field_cn: first(sourceColumn, ['columnComment', 'column_comment']),
        source_field_length: first(sourceColumn, ['length']),
        source_data_type: first(sourceColumn, ['dataType', 'data_type']),
        target_field: targetName,
        target_field_cn: first(targetColumn, ['columnComment', 'column_comment']),
        target_field_length: first(targetColumn, ['length']),
        target_data_type: first(targetColumn, ['dataType', 'data_type']),
        dict_enable: 0,
        func_enable: 0,
        sort_no: targetIndex,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}
if (mappings.size() > 0) {
    for (mapping in mappings) { mapping.tenant_id = tenantId }
    db.table('data_access_field_mapping').batchInsert(mappings)
}

var task = db.table('data_access_agg_task_t').where()
    .eq('tid', taskId)
    .eq('is_del', 0)
    .selectOne()
return {
    created: true,
    taskId: taskId,
    pipelineId: pipelineId,
    catalogId: catalogId,
    sourceTableId: sourceTableId,
    targetTableId: targetTableId,
    targetTableIds: targetTableIds,
    targetTables: targetSummaries,
    task: task
}
