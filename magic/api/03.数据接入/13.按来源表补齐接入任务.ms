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
  "responseBody": "",
  "description": "按当前租户来源表和所选物化目标表分别补齐独立接入任务。targetTableIds（最多100项）或targetTableId指定目标，省略时处理全部物化目标；首个任务从原来源读取，后续任务从前一个目标表读取；每个任务生成单目标画布及自己的字段映射，重复提交复用已有任务。批量校验租户和角色数据范围，dryRun只返回计划；forceRebuild须明确确认，会替换所选旧任务的画布。返回tasks、createdCount及兼容的首个task/taskId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：13.按来源表补齐接入任务
// 路由：POST /ods/dataAggTaskEnsure
// 业务说明：一次预取来源、所选目标、连接及字典元数据，按所选目标顺序逐级生成独立任务。
// 重复请求复用任务；仅显式重建替换画布，dryRun不写入。事务锁住来源登记，串行处理同一来源的创建请求。
import cn.hutool.core.util.IdUtil
import jsons
import cn.hutool.json.JSONUtil as poolJsons
import apiPullConfig
import tenantRuntime
import dataScope
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService as structuredFileProbe
import java.util.Map
import java.util.List
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType as sourceTypes

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
var firstPresent = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) { return value }
    }
    return null
}
var jdbcUrl = (dbType, cfg, row) => sourceTypes.jdbcUrl(text(dbType), cfg, row)
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
var governancePropertiesByParent = {}
var governanceCatalogIdsBySource = {}
var governanceCatalogById = {}
// This is called for three bounded groups: source, referenced dictionaries,
// and materialized targets.  It never reads a row from inside a table loop.
var loadGovernanceForTableIds = (tableIds) => {
    var uniqueIds = []
    for (tableId in (tableIds == null ? [] : tableIds)) {
        var id = text(tableId).trim()
        if (!blank(id) && !uniqueIds.contains(id)) { uniqueIds.add(id) }
    }
    if (uniqueIds.size() == 0) { return }
    if (uniqueIds.size() > 200) { exit 400, '单次最多处理200个治理关联表' }
    var directProperties = db.table('da_prop_t').where()
        .eq('tenant_id', tenantId).in('parent_id', uniqueIds).eq('is_del', 0).select()
    for (property in (directProperties == null ? [] : directProperties)) {
        var parentId = text(first(property, ['parentId', 'parent_id']))
        if (governancePropertiesByParent[parentId] == null) { governancePropertiesByParent[parentId] = [] }
        governancePropertiesByParent[parentId].add(property)
    }
    var sourceLinkPage = db.table('da_prop_t').where()
        .eq('tenant_id', tenantId).eq('prop_name', 'sourceTableId')
        .in('prop_value', uniqueIds).eq('is_del', 0).page(201, 0)
    if (sourceLinkPage != null && sourceLinkPage.total > 200) {
        exit 400, '来源表关联目录过多，请先核对历史目录'
    }
    var sourceLinks = sourceLinkPage == null || sourceLinkPage.list == null ? [] : sourceLinkPage.list
    var catalogIds = []
    for (link in (sourceLinks == null ? [] : sourceLinks)) {
        var linkedSourceId = text(first(link, ['propValue', 'prop_value']))
        var catalogId = text(first(link, ['parentId', 'parent_id']))
        if (blank(linkedSourceId) || blank(catalogId)) { continue }
        if (governanceCatalogIdsBySource[linkedSourceId] == null) { governanceCatalogIdsBySource[linkedSourceId] = [] }
        if (!governanceCatalogIdsBySource[linkedSourceId].contains(catalogId)) {
            governanceCatalogIdsBySource[linkedSourceId].add(catalogId)
        }
        if (!catalogIds.contains(catalogId)) { catalogIds.add(catalogId) }
    }
    if (catalogIds.size() == 0) { return }
    if (catalogIds.size() > 200) { exit 400, '来源表关联目录过多，请先核对历史目录' }
    var catalogProperties = db.table('da_prop_t').where()
        .eq('tenant_id', tenantId).in('parent_id', catalogIds).eq('is_del', 0).select()
    for (property in (catalogProperties == null ? [] : catalogProperties)) {
        var parentId = text(first(property, ['parentId', 'parent_id']))
        if (governancePropertiesByParent[parentId] == null) { governancePropertiesByParent[parentId] = [] }
        governancePropertiesByParent[parentId].add(property)
    }
    var catalogRows = db.table('da_catalog_t').where()
        .eq('tenant_id', tenantId).in('tid', catalogIds).eq('is_del', 0).select()
    for (catalogRow in (catalogRows == null ? [] : catalogRows)) {
        governanceCatalogById[text(first(catalogRow, ['tid']))] = catalogRow
    }
}
var governanceConfigOf = (table) => {
    var persisted = parseGovernanceConfig(first(table, [
        'fieldGovernanceConfig', 'field_governance_config', 'fieldGovernanceCfg'
    ]))
    if (configHasFields(persisted)) { return persisted }
    var tableId = text(first(table, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(tableId)) { return persisted }
    var direct = governanceConfigFromProperties(governancePropertiesByParent[tableId])
    if (configHasFields(direct)) { return direct }
    for (catalogId in (governanceCatalogIdsBySource[tableId] == null ? [] : governanceCatalogIdsBySource[tableId])) {
        if (blank(catalogId)) { continue }
        var catalogConfig = governanceConfigFromProperties(governancePropertiesByParent[catalogId])
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
var metadataTableById = {}
var metadataColumnsByTableId = {}
var metadataDatasourceById = {}
var metadataTablesByName = {}
// A field's checked dictionary association is persisted independently of the
// detailed relation JSON.  Production registrations may only have these two
// column values, so resolve the executable lookup from the registered
// dictionary table instead of requiring the source relation JSON to be whole.
var registeredDictionaryRelations = (sourceTable) => {
    var result = []
    var sourceTableId = text(first(sourceTable, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(sourceTableId)) { return result }
    var registeredSourceColumns = metadataColumnsByTableId[sourceTableId]
    for (sourceColumn in (registeredSourceColumns == null ? [] : registeredSourceColumns)) {
        var sourceName = text(first(sourceColumn, ['columnName', 'column_name'])).trim()
        var enabled = text(first(sourceColumn, ['enableCodeTable', 'enable_code_table', 'isDict', 'is_dict'])).trim().toLowerCase()
        var dictionaryTableId = text(first(sourceColumn, ['codeTableId', 'code_table_id', 'dictId', 'dict_id'])).trim()
        if (blank(sourceName) || (enabled != '1' && enabled != 'true' && enabled != 'yes' && blank(dictionaryTableId))) {
            continue
        }
        var dictionaryTable = metadataTableById[dictionaryTableId]
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
        var dictionaryColumns = metadataColumnsByTableId[dictionaryTableId]
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
        .eq('tenant_id', tenantId)
        .eq('parent_id', catalogId)
        .eq('prop_name', name)
        .eq('is_del', 0)
        .selectOne()
    return row == null ? '' : text(row.propValue)
}
var findCatalogBySource = (sourceTableId) => {
    for (catalogId in (governanceCatalogIdsBySource[sourceTableId] == null ? [] : governanceCatalogIdsBySource[sourceTableId])) {
        if (governanceCatalogById[catalogId] != null) { return catalogId }
    }
    return ''
}
var colName = (row) => first(row, ['columnName', 'column_name'])
// All source and target datasource rows have already been loaded in this
// request.  Normalize their pool_cfg in memory: calling the shared read
// function for every sink would query db_datasource_t once per target.
var readCfg = (row) => {
    if (row == null) { return {} }
    var config = {}
    var raw = firstPresent(row, ['poolCfg', 'pool_cfg'])
    if (!blank(raw)) {
        try {
            var parsed = raw instanceof Map ? raw : poolJsons.parse(text(raw))
            if (parsed != null) { config.putAll(parsed) }
        } catch (e) {
            exit 500, '数据源连接配置 pool_cfg 不是有效 JSON 对象'
        }
    }
    var master = {
        dbType: ['dbType', 'db_type'],
        jdbcURL: ['jdbcURL', 'jdbcUrl', 'jdbc_url'],
        username: ['username'],
        password: ['password'],
        dbVersion: ['dbVersion', 'db_version'],
        nodeId: ['nodeId', 'node_id'],
        storageDomain: ['storageDomain', 'storage_domain'],
        showConnect: ['showConnect', 'show_connect'],
        connectionStatus: ['connectionStatus', 'connection_status']
    }
    for (key in master.keySet()) {
        var value = firstPresent(row, master[key])
        if (value != null) { config[key] = value }
    }
    return config
}
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
        .eq('tenant_id', tenantId).eq('target_type', 'domain').eq('layer_code', 'fjghcc').eq('is_del', 0)
        .orderBy('updated_time', 'desc').selectOne()
    var bindingDatasourceId = binding == null ? '' : first(binding, ['datasourceId', 'datasource_id'])
    if (blank(bindingDatasourceId)) {
        exit 400, '资源中心 ODS 数据域 fjghcc 未绑定 FTP/SFTP 数据源'
    }
    var storageId = text(bindingDatasourceId).trim()
    var storage = db.table('db_datasource_t').where()
        .eq('tenant_id', tenantId).eq('tid', storageId).eq('is_del', 0).selectOne()
    if (storage == null) { exit 404, '资源中心 ODS 数据域 fjghcc 绑定的数据源不存在' }
    // FTP fields in the tenant-owned datasource row are authoritative.
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
    var dictionaryTable = metadataTableById[dictionaryTableId]
    if (dictionaryTable == null) {
        return { config: {}, schema: '' }
    }
    var datasourceId = text(first(dictionaryTable, ['datasourceId', 'datasource_id'])).trim()
    var datasource = metadataDatasourceById[datasourceId]
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
    var candidates = metadataTablesByName[text(relation.dictionaryTable).toLowerCase()]
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
    var datasource = metadataDatasourceById[datasourceId]
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
    try { return sourceTypes.sourceManifest(text(dbType), cfg) }
    catch (e) { exit 400, '无法生成来源组件：' + e.getMessage() }
}
var normalizeSinkType = (dbType, cfg) => {
    try { return sourceTypes.sinkType(text(dbType), cfg) }
    catch (e) { exit 400, '无法生成目标组件：' + e.getMessage() }
}
var normalizeConfigType = (dbType, cfg) => {
    try { return sourceTypes.configType(text(dbType), cfg) }
    catch (e) { exit 400, '数据源类型配置不完整：' + e.getMessage() }
}
// Huawei MRS Hive is a server-managed target.  Its registered datasource
// intentionally has no browser-side jdbc_url, so the generated DSL must use
// the dedicated Hive sink rather than falling into the generic JDBC sink.
var isServerManagedHuaweiMrsHive = (dbType, cfg) => {
    if (blank(dbType)) { return false }
    if (normalizeConfigType(dbType, cfg) != 'HIVE') { return false }
    var mode = text(first(cfg, ['metadataAccessMode', 'metadata_access_mode'])).trim().toLowerCase()
    var connectionMode = text(first(cfg, ['hiveConnectionMode', 'hive_connection_mode'])).trim().toLowerCase()
    var profile = text(first(cfg, ['hiveProfile', 'hive_profile'])).trim()
    return mode == 'server-managed-mrs' || connectionMode == 'huawei-mrs' || !blank(profile)
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
var declaredKey = (column) => {
    var name = text(colName(column)).toUpperCase()
    var pk = text(first(column, ['primaryKey', 'primary_key', 'isPrimaryKey', 'is_primary_key', 'isPk', 'is_pk']))
    var unique = text(first(column, ['isUnique', 'is_unique', 'unique', 'uk']))
    return pk == '1' || pk.toLowerCase() == 'true' || unique == '1' || unique.toLowerCase() == 'true'
}
var isStableBusinessKey = (column) => text(colName(column)).toUpperCase() != 'ODS_UUID' && declaredKey(column)
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
// NiFi handles token order/duplicates and unknown codes in records, independently of SQL dialect.
// Only the dictionary key/name rows use bounded, parameterized JDBC IN queries.
var multiDictionaryQuery = (relation, dictionaryName) => {
    var conditions = dictionaryLookupConditions(relation)
    return 'SELECT ' + relation.dictionaryKeyField + ', ' + relation.dictionaryLabelField
        + ' FROM ' + dictionaryName + ' WHERE ' + relation.dictionaryKeyField + ' IN (:codes)'
        + (conditions.size() == 0 ? '' : ' AND ' + conditions.join(' AND '))
}

// 字段统一格式只从已经预取的本租户登记快照解析，不增加逐字段查询。
var temporalMappingsOf = (sourceColumns, governanceConfig) => {
    var registered = {}
    for (field in (governanceConfig.fields == null ? [] : governanceConfig.fields)) {
        var name = text(first(field, ['columnName', 'column_name'])).trim().toLowerCase()
        if (!blank(name)) { registered[name] = field }
    }
    var result = {}
    for (column in (sourceColumns == null ? [] : sourceColumns)) {
        var name = text(colName(column)).trim()
        var field = registered[name.toLowerCase()]
        var cleared = field == null ? '' : text(field.standardFieldExplicitlyCleared).trim().toLowerCase()
        if (cleared == 'true' || cleared == '1') { continue }
        var format = text(first(column, ['dataStandardId', 'data_standard_id'])).trim().toUpperCase()
        if (blank(format) && field != null) { format = text(field.standardField).trim().toUpperCase() }
        if (format != 'DATE' && format != 'DATETIME') { continue }
        var sourceType = text(first(column, ['dataType', 'data_type', 'columnType', 'column_type'])).trim().toLowerCase()
        // 已有原生时间列由 Record Reader 保留时间语义；varchar 等需要显式转换。
        if (sourceType.indexOf('date') >= 0 || sourceType.indexOf('time') >= 0) { continue }
        var sqlType = format == 'DATE' ? 'DATE' : 'TIMESTAMP'
        result[name.toLowerCase()] = 'CAST(NULLIF(TRIM(CAST(${field:' + name + '} AS VARCHAR)), \'\') AS ' + sqlType + ')'
    }
    return result
}
var mappingDsl = (sourceColumns, targetColumns, sourceTable, lookupSources, transformBindings, sourceLookupConfig, temporalMappings, preserveSystemFields) => {
    var sourceNames = {}
    var sourceDataTypes = {}
    var relations = preserveSystemFields ? {} : dictionaryRelations(sourceTable)
    for (c in (sourceColumns == null ? [] : sourceColumns)) {
        sourceNames[text(colName(c)).toLowerCase()] = colName(c)
        sourceDataTypes[text(colName(c)).toLowerCase()] = first(c, ['dataType', 'data_type', 'columnType', 'column_type'])
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
        var systemMapping = preserveSystemFields ? null : odsSystemMapping(name)
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
                    lookup: { values: standardValueMap,
                        resultColumn: name, onMissing: 'NULL', multiValue: true,
                        multiValueSeparator: relation.multiValueSeparator } })
            } else {
                mappings.add({ from: '/' + relation.sourceName, to: '/' + name,
                    sourceDataType: sourceDataTypes[text(relation.sourceName).toLowerCase()],
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
            mappings.add({
                from: '/' + relation.sourceName,
                to: '/' + name,
                lookup: {
                    sql: lookupSql,
                    query: relation.multiValue ? multiDictionaryQuery(relation, dictionaryName) : null,
                    resultColumn: name,
                    onMissing: 'NULL',
                    multiValue: relation.multiValue == true,
                    multiValueSeparator: relation.multiValueSeparator,
                    dataSource: dictionarySource.config
                }
            })
        } else if (!blank(name) && sourceNames[text(name).toLowerCase()] != null) {
            var sourceName = sourceNames[text(name).toLowerCase()]
            var temporalExpression = temporalMappings[text(sourceName).toLowerCase()]
            if (!blank(temporalExpression)) {
                mappings.add({ from: '/' + sourceName, to: '/' + name, expression: temporalExpression })
            } else {
                mappings.add({ from: '/' + sourceName, to: '/' + name })
            }
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

return db.transaction(() => {
var sourceTableId = text(body?.tableId || body?.sourceTableId || body?.tid).trim()
var tenantId = text(tenantRuntime.id()).trim()
if (blank(tenantId)) { exit 401, '当前登录租户无效，请重新登录' }
// Rebuilding an existing task is opt-in. Normal requests preserve a saved
// pipeline even when a newer generator can produce richer mappings.
var forceRebuild = text(body?.repair || body?.forceRebuild || body?.rebuild).trim().toLowerCase()
forceRebuild = forceRebuild == 'true' || forceRebuild == '1' || forceRebuild == 'yes'
var now = new Date()
var dryRun = body?.dryRun == true
if (blank(sourceTableId)) {
    exit 400, 'tableId不能为空'
}

var sourceTable = db.selectOne("""
    SELECT * FROM db_table_t
     WHERE tid = #{sourceTableId} AND tenant_id = #{tenantId} AND is_del = 0
     FOR UPDATE
""")
if (sourceTable == null) {
    exit 404, '来源表不存在'
}
var sourceDbId = text(first(sourceTable, ['datasourceId', 'datasource_id']))
var sourceDb = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).eq('tid', sourceDbId).eq('is_del', 0).selectOne()
if (sourceDb == null || dataScope.visibleDataSourcesFor('MENU:2081000000000000002', [sourceDb]).size() != 1) {
    exit 403, '来源数据源不在当前租户或角色数据范围内'
}

var sourceColumns = db.table('db_table_column_t').where()
    .eq('tenant_id', tenantId).eq('table_id', sourceTableId).eq('is_del', 0)
    .orderBy('ordinal_position', 'asc').select()
metadataColumnsByTableId[sourceTableId] = sourceColumns == null ? [] : sourceColumns
loadGovernanceForTableIds([sourceTableId])
var sourceGovernanceConfig = governanceConfigOf(sourceTable)
var sourceTemporalMappings = temporalMappingsOf(sourceColumns, sourceGovernanceConfig)
var dictionaryTableIds = []
var dictionaryTableNames = []
for (sourceColumn in metadataColumnsByTableId[sourceTableId]) {
    var dictionaryId = text(first(sourceColumn, ['codeTableId', 'code_table_id', 'dictId', 'dict_id'])).trim()
    if (!blank(dictionaryId) && !dictionaryTableIds.contains(dictionaryId)) { dictionaryTableIds.add(dictionaryId) }
}
for (field in (sourceGovernanceConfig.fields == null ? [] : sourceGovernanceConfig.fields)) {
    var relation = first(field, ['dictionaryRelation', 'dictionary_relation', 'dictionaryConfig', 'dictionary_config'])
    if (relation == null) { continue }
    var dictionaryId = text(first(relation, ['dictionaryTableId', 'dictionary_table_id'])).trim()
    if (!blank(dictionaryId) && !dictionaryTableIds.contains(dictionaryId)) { dictionaryTableIds.add(dictionaryId) }
    var dictionaryReference = dictionaryTableReference(first(relation, ['dictionaryTable', 'dictionary_table', 'dictTable', 'dict_table']))
    if (!blank(dictionaryReference.table) && !dictionaryTableNames.contains(dictionaryReference.table)) {
        dictionaryTableNames.add(dictionaryReference.table)
    }
}
if (dictionaryTableIds.size() > 200 || dictionaryTableNames.size() > 200) {
    exit 400, '单次最多处理200个字典表关联'
}
var dictionaryRowsById = dictionaryTableIds.size() == 0 ? [] : db.table('db_table_t').where()
    .eq('tenant_id', tenantId).in('tid', dictionaryTableIds).eq('is_del', 0).select()
var dictionaryRowsByName = []
if (dictionaryTableNames.size() > 0) {
    var dictionaryNamePage = db.table('db_table_t').where()
        .eq('tenant_id', tenantId).in('table_name', dictionaryTableNames).eq('is_del', 0).page(201, 0)
    if (dictionaryNamePage != null && dictionaryNamePage.total > 200) {
        exit 400, '同名字典登记表超过200个，请先缩小字典关联范围'
    }
    dictionaryRowsByName = dictionaryNamePage == null || dictionaryNamePage.list == null
        ? [] : dictionaryNamePage.list
}
var dictionaryDatasourceIds = []
for (dictionaryTable in dictionaryRowsById) {
    var dictionaryId = text(first(dictionaryTable, ['tid']))
    metadataTableById[dictionaryId] = dictionaryTable
    var dictionaryDbId = text(first(dictionaryTable, ['datasourceId', 'datasource_id']))
    if (!blank(dictionaryDbId) && !dictionaryDatasourceIds.contains(dictionaryDbId)) { dictionaryDatasourceIds.add(dictionaryDbId) }
}
for (dictionaryTable in dictionaryRowsByName) {
    var dictionaryId = text(first(dictionaryTable, ['tid']))
    metadataTableById[dictionaryId] = dictionaryTable
    var dictionaryDbId = text(first(dictionaryTable, ['datasourceId', 'datasource_id']))
    if (!blank(dictionaryDbId) && !dictionaryDatasourceIds.contains(dictionaryDbId)) { dictionaryDatasourceIds.add(dictionaryDbId) }
}
if (dictionaryDatasourceIds.size() > 200) { exit 400, '单次最多处理200个字典数据源' }
var dictionaryDatasourceRows = dictionaryDatasourceIds.size() == 0 ? [] : db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).in('tid', dictionaryDatasourceIds).eq('is_del', 0).select()
var visibleDictionaryDatasources = dataScope.visibleDataSourcesFor(
    'MENU:2081000000000000002', dictionaryDatasourceRows)
for (dictionaryDatasource in visibleDictionaryDatasources) {
    metadataDatasourceById[text(first(dictionaryDatasource, ['tid']))] = dictionaryDatasource
}
for (dictionaryId in dictionaryTableIds) {
    var dictionaryTable = metadataTableById[dictionaryId]
    if (dictionaryTable != null) {
        var dictionaryDbId = text(first(dictionaryTable, ['datasourceId', 'datasource_id']))
        if (metadataDatasourceById[dictionaryDbId] == null) {
            exit 403, '字典表关联的数据源不在当前租户或角色数据范围内'
        }
    }
}
var governedDictionaryTableIds = []
for (dictionaryId in dictionaryTableIds) {
    if (metadataTableById[dictionaryId] != null) { governedDictionaryTableIds.add(dictionaryId) }
}
loadGovernanceForTableIds(governedDictionaryTableIds)
var dictionaryColumns = governedDictionaryTableIds.size() == 0 ? [] : db.table('db_table_column_t').where()
    .eq('tenant_id', tenantId).in('table_id', governedDictionaryTableIds).eq('is_del', 0)
    .orderBy('ordinal_position', 'asc').select()
for (dictionaryColumn in dictionaryColumns) {
    var dictionaryTableId = text(first(dictionaryColumn, ['tableId', 'table_id']))
    if (metadataColumnsByTableId[dictionaryTableId] == null) { metadataColumnsByTableId[dictionaryTableId] = [] }
    metadataColumnsByTableId[dictionaryTableId].add(dictionaryColumn)
}
for (dictionaryTable in dictionaryRowsByName) {
    var dictionaryDbId = text(first(dictionaryTable, ['datasourceId', 'datasource_id']))
    if (metadataDatasourceById[dictionaryDbId] == null) { continue }
    var dictionaryName = text(first(dictionaryTable, ['tableName', 'table_name'])).toLowerCase()
    if (metadataTablesByName[dictionaryName] == null) { metadataTablesByName[dictionaryName] = [] }
    metadataTablesByName[dictionaryName].add(dictionaryTable)
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
var materializedTargetsPage = db.table('db_table_t').where()
    .eq('tenant_id', tenantId)
    .eq('source_table_id', sourceTableId)
    .eq('is_del', 0)
    .orderBy('created_time', 'asc')
    .page(101, 0)
if (materializedTargetsPage != null && materializedTargetsPage.total > 100) {
    exit 400, '单次最多处理100个物化目标表，请缩小目标范围'
}
var materializedTargets = materializedTargetsPage == null || materializedTargetsPage.list == null
    ? [] : materializedTargetsPage.list
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
            .eq('tenant_id', tenantId).eq('tid', legacyTargetTableId).eq('is_del', 0).selectOne()
        var legacySourceId = text(first(legacyTargetTable, ['sourceTableId', 'source_table_id']))
        if (legacyTargetTable != null && (blank(legacySourceId) || legacySourceId == sourceTableId)) {
            targetTables.add(legacyTargetTable)
            targetTableIds.add(legacyTargetTableId)
        }
    }
}
if (targetTables.size() == 0) {
    exit 400, '请先完成物化建表，生成至少一个目标表后才能创建接入任务'
}
if (targetTables.size() > 100) { exit 400, '单次最多处理100个物化目标表，请缩小目标范围' }

var materializedTargetIds = []
for (target in targetTables) { materializedTargetIds.add(text(target.tid)) }
// 显式选择只处理这些目标；来源关联和租户条件由服务端逐ID核对。
var requestedTargetIds = body?.targetTableIds == null ? [] : body.targetTableIds
if (!(requestedTargetIds instanceof List)) { exit 400, 'targetTableIds必须为数组' }
if (requestedTargetIds.size() > 100) { exit 400, '单次最多处理100个物化目标表' }
if (!blank(body?.targetTableId) && requestedTargetIds.size() == 0) { requestedTargetIds = [text(body.targetTableId).trim()] }
if (requestedTargetIds.size() > 0) {
    var materializedById = {}
    for (candidateTarget in targetTables) { materializedById[text(candidateTarget.tid)] = candidateTarget }
    var selectedTargets = []
    var selectedIds = []
    for (requestedId in requestedTargetIds) {
        var selectedId = text(requestedId).trim()
        if (blank(selectedId) || materializedById[selectedId] == null) {
            exit 403, '所选目标表不存在、不属于当前租户或未关联当前来源表'
        }
        if (!selectedIds.contains(selectedId)) {
            selectedIds.add(selectedId)
            selectedTargets.add(materializedById[selectedId])
        }
    }
    targetTables = selectedTargets
    targetTableIds = selectedIds
}

// Authorize every target database ID before reading columns or creating a
// pipeline.  A mixed authorized/unauthorized target set fails as a whole.
var targetDatasourceIds = []
for (candidateTarget in targetTables) {
    var candidateDbId = text(first(candidateTarget, ['datasourceId', 'datasource_id'])).trim()
    if (blank(candidateDbId)) { exit 400, '物化目标表缺少目标数据源' }
    if (!targetDatasourceIds.contains(candidateDbId)) { targetDatasourceIds.add(candidateDbId) }
}
var targetDatasourceRows = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId).in('tid', targetDatasourceIds).eq('is_del', 0).select()
var visibleTargetDatasources = dataScope.visibleDataSourcesFor(
    'MENU:2081000000000000002', targetDatasourceRows == null ? [] : targetDatasourceRows)
var targetDatasourceById = {}
for (visibleTargetDatasource in visibleTargetDatasources) {
    targetDatasourceById[text(first(visibleTargetDatasource, ['tid']))] = visibleTargetDatasource
}
for (candidateDbId in targetDatasourceIds) {
    if (targetDatasourceById[candidateDbId] == null) {
        exit 403, '物化目标数据源不在当前租户或角色数据范围内'
    }
}
loadGovernanceForTableIds(targetTableIds)

// 一次查询全部所选目标字段，独立任务分别使用自己的目标字段和映射。
var targetColumnRows = db.table('db_table_column_t').where()
    .eq('tenant_id', tenantId).in('table_id', targetTableIds).eq('is_del', 0)
    .orderBy('ordinal_position', 'asc').select()
var targetColumnsById = {}
for (targetColumnRow in (targetColumnRows == null ? [] : targetColumnRows)) {
    var ownerTableId = text(first(targetColumnRow, ['tableId', 'table_id']))
    if (targetColumnsById[ownerTableId] == null) { targetColumnsById[ownerTableId] = [] }
    targetColumnsById[ownerTableId].add(targetColumnRow)
}
for (candidateTargetId in targetTableIds) {
    if (targetColumnsById[candidateTargetId] == null || targetColumnsById[candidateTargetId].size() == 0) {
        exit 409, '物化目标表缺少当前租户的字段元数据，请重新物化建表'
    }
}
// 选择顺序即接入链路顺序：来源→目标1，目标1→目标2，依次递进。
var rootSourceTable = sourceTable
var rootSourceDb = sourceDb
var rootSourceColumns = sourceColumns
var rootSourceTableId = sourceTableId
var hopSourceByTarget = {}
var chainSourceIds = [rootSourceTableId]
var previousTable = rootSourceTable
for (target in targetTables) {
    hopSourceByTarget[text(target.tid)] = previousTable
    if (!chainSourceIds.contains(text(target.tid))) { chainSourceIds.add(text(target.tid)) }
    previousTable = target
}
var originalSourceTemporalMappings = sourceTemporalMappings
var originalDictionaryRelations = dictionaryRelations(rootSourceTable)
// 批量读取已有任务、画布和映射，不能按目标逐条查询。
var existingTasksPage = db.table('data_access_agg_task_t').where()
    .eq('tenant_id', tenantId).in('source_table_id', chainSourceIds).eq('is_del', 0)
    .orderBy('updated_time', 'desc').page(201, 0)
if (existingTasksPage.total > 200) { exit 409, '当前来源已有任务过多，请先核对任务' }
var existingTasks = existingTasksPage.list == null ? [] : existingTasksPage.list
var existingTaskIds = []
var existingPipelineIds = []
for (existingTask in existingTasks) {
    existingTaskIds.add(text(existingTask.tid))
    var existingPipelineId = text(first(existingTask, ['pipelineId', 'pipeline_id']))
    if (!blank(existingPipelineId) && !existingPipelineIds.contains(existingPipelineId)) { existingPipelineIds.add(existingPipelineId) }
}
var existingPipelines = existingPipelineIds.size() == 0 ? [] : db.table('nifi_pipeline_t').where()
    .eq('tenant_id', tenantId).in('id', existingPipelineIds).eq('is_del', 0).select()
var existingPipelineById = {}
for (pipeline in existingPipelines) { existingPipelineById[text(pipeline.id)] = pipeline }
var existingMappingRows = existingTaskIds.size() == 0 ? [] : db.table('data_access_field_mapping').columns('task_id').where()
    .eq('tenant_id', tenantId).in('task_id', existingTaskIds).eq('is_del', 0).select()
var mappingCountByTask = {}
for (mapping in existingMappingRows) {
    var ownerTaskId = text(first(mapping, ['taskId', 'task_id']))
    mappingCountByTask[ownerTaskId] = mappingCountByTask[ownerTaskId] == null ? 1 : mappingCountByTask[ownerTaskId] + 1
}
var existingTaskByTarget = {}
var retireTaskIds = []
var retirePipelineIds = []
var repairReasons = []
var reusedTasks = []
for (existingTask in existingTasks) {
    var pipelineId = text(first(existingTask, ['pipelineId', 'pipeline_id']))
    var pipeline = existingPipelineById[pipelineId]
    var existingDsl = text(first(pipeline, ['dslJson', 'dsl_json']))
    var existingDslObject = blank(existingDsl) ? {} : parseGovernanceConfig(existingDsl)
    var sinkIds = []
    for (node in (existingDslObject.nodes == null ? [] : existingDslObject.nodes)) {
        if (text(node.category).toLowerCase() == 'sink') {
            var sinkId = text(first(node.config, ['targetTableId'])).trim()
            if (!blank(sinkId) && !sinkIds.contains(sinkId)) { sinkIds.add(sinkId) }
        }
    }
    var primaryTargetId = text(first(existingTask, ['targetTableId', 'target_table_id']))
    var coveredIds = sinkIds.size() == 0 ? [primaryTargetId] : sinkIds
    var intersects = targetTableIds.contains(primaryTargetId)
    for (coveredId in coveredIds) { if (targetTableIds.contains(coveredId)) { intersects = true } }
    if (!intersects) { continue }
    var legacyMultiTarget = coveredIds.size() > 1
    var expectedSource = hopSourceByTarget[primaryTargetId]
    var wrongHopSource = expectedSource != null && text(first(existingTask, ['sourceTableId', 'source_table_id'])) != text(expectedSource.tid)
    if (forceRebuild) {
        if (!blank(first(existingTask, ['processGroupId', 'process_group_id']))
            || ['RUNNING', 'DEPLOYED', 'STARTING'].contains(text(pipeline?.status).toUpperCase())) {
            exit 409, '所选历史任务已部署，请先停止并删除该任务，再按新链路创建，目标表及数据可保留'
        }
        // 替换历史多目标画布必须选择其全部仍有效目标，防止遗漏原任务中的其它目标。
        for (coveredId in coveredIds) {
            if (legacyMultiTarget && !targetTableIds.contains(coveredId)
                && materializedTargetIds.contains(coveredId)) {
                exit 409, '重建历史多目标任务须选择该任务的全部物化目标表'
            }
        }
        retireTaskIds.add(text(existingTask.tid))
        if (!blank(pipelineId) && !retirePipelineIds.contains(pipelineId)) { retirePipelineIds.add(pipelineId) }
        continue
    }
    var taskReasons = []
    for (fileNode in (existingDslObject.nodes == null ? [] : existingDslObject.nodes)) {
        if (['source.ftp', 'source.sftp'].contains(text(fileNode.manifestKey))
            && blank(fileNode.config?.sourceFileName) && blank(fileNode.config?.logicalSourceDatasourceId)) {
            taskReasons.add('文件来源仍使用旧表名匹配规则，需确认后重新生成精确文件采集配置')
        }
        if (['source.ftp', 'source.sftp'].contains(text(fileNode.manifestKey)) && body?.sourceFileConfig != null
            && ((body.sourceFileConfig.charset != null && text(body.sourceFileConfig.charset) != text(fileNode.config?.csvEncoding))
                || (body.sourceFileConfig.delimiter != null && text(body.sourceFileConfig.delimiter) != text(fileNode.config?.csvDelimiter)))) {
            taskReasons.add('文件解析参数已变化，请确认后重新生成采集配置')
        }
    }
    if (wrongHopSource) { taskReasons.add('已有任务的来源与所选目标顺序不一致，需确认后按级联链路重建') }
    if (legacyMultiTarget) { taskReasons.add('历史任务包含多个目标库，需确认后拆分为独立任务') }
    if (pipeline == null || blank(existingDsl)) { taskReasons.add('关联流程不存在或没有画布定义') }
    if (sinkIds.size() != 1 || !sinkIds.contains(primaryTargetId)) { taskReasons.add('画布目标表关联需要显式确认重建') }
    if (targetColumnsById[primaryTargetId] != null
        && (mappingCountByTask[text(existingTask.tid)] == null ? 0 : mappingCountByTask[text(existingTask.tid)]) < targetColumnsById[primaryTargetId].size()) {
        taskReasons.add('字段映射少于目标表字段，请确认后显式修复')
    }
    if (dictionaryRelations(sourceTable).size() > 0 && (existingDsl.indexOf('lookup') < 0 || existingDsl.indexOf('dataSource') < 0)) {
        taskReasons.add('字典关联配置需要显式升级')
    }
    if (existingDsl.indexOf('HIVE_RECORD_PUT') >= 0) { taskReasons.add('华为 MRS Hive 写入方式需确认升级为 PutHwHDFS') }
    for (taskReason in taskReasons) { if (!repairReasons.contains(taskReason)) { repairReasons.add(taskReason) } }
    existingTask.repairRequired = taskReasons.size() > 0
    existingTask.repairReasons = taskReasons
    reusedTasks.add(existingTask)
    for (coveredId in coveredIds) {
        if (targetTableIds.contains(coveredId) && existingTaskByTarget[coveredId] == null) { existingTaskByTarget[coveredId] = existingTask }
    }
    if (targetTableIds.contains(primaryTargetId) && existingTaskByTarget[primaryTargetId] == null) { existingTaskByTarget[primaryTargetId] = existingTask }
}
var responseOf = (tasks, createdCount, summaries) => {
    var firstTask = tasks.size() == 0 ? null : tasks[0]
    return {created: createdCount > 0, createdCount: createdCount, taskCount: tasks.size(), tasks: tasks,
        task: firstTask, taskId: firstTask == null ? '' : text(firstTask.tid),
        pipelineId: text(first(firstTask, ['pipelineId', 'pipeline_id'])),
        catalogId: catalogId, sourceTableId: rootSourceTableId, targetTableIds: targetTableIds,
        targetTableId: targetTableIds[0], targetTables: summaries,
        repairRequired: repairReasons.size() > 0, repairReasons: repairReasons,
        dryRun: dryRun, sideEffectsApplied: !dryRun && createdCount > 0}
}
// 已保存画布需要确认时整批保留，避免在提示确认前创建部分任务。
if (repairReasons.size() > 0) { return responseOf(reusedTasks, 0, []) }
var targetsToCreate = []
for (target in targetTables) { if (forceRebuild || existingTaskByTarget[text(target.tid)] == null) { targetsToCreate.add(target) } }
if (targetsToCreate.size() == 0) { return responseOf(reusedTasks, 0, []) }

var sourceCfg = readCfg(rootSourceDb)
var sourceColumnMap = {}
for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) { sourceColumnMap[text(colName(sourceColumn)).toLowerCase()] = sourceColumn }
var sourceDictionaryRelations = dictionaryRelations(sourceTable)
var dictionaryLookupSources = validatedDictionaryLookupSources
var nowMs = now.getTime()
var sourceTableName = first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
var sourceTableLabel = text(first(sourceTable, ['tableNameCn', 'table_name_cn'])).trim()
if (blank(sourceTableLabel)) { sourceTableLabel = text(sourceTableName).trim() }
var sourceDbType = first(sourceDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
var accessMode = pushAccessMode(sourceDb, sourceCfg)
var pushFileSource = (accessMode == 'receive' || accessMode == 'upload' || accessMode == 'push')
    ? resolveOdsPushFtp(sourceDbId, sourceTableName) : null
var sourceService = first(sourceCfg, ['serviceName', 'sid', 'database', 'dbName', 'dbMetaDbName'])
var sourceConnectionType = text(first(sourceCfg, ['connectionType', 'jdbcType'])).trim().toUpperCase()
if (sourceConnectionType != 'SID') { sourceConnectionType = 'SERVICE_NAME' }
var sourceSchema = first(sourceCfg, ['defaultSchema', 'schema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
if (blank(sourceSchema)) {
    sourceSchema = first(sourceTable, ['schemaName', 'schema_name', 'owner', 'tableSchema', 'table_schema'])
}
var sourceManifestKey = pushFileSource == null ? normalizeSourceKey(sourceDbType, sourceCfg)
    : normalizeSourceKey(pushFileSource.dbType, pushFileSource.config)
var sourceJdbcUrl = pushFileSource == null ? jdbcUrl(sourceDbType, sourceCfg, sourceDb) : null

var sourceColumnsForNifi = columnList(sourceColumns)
var sourceNodeConfig = {
    dbType: pushFileSource == null ? normalizeConfigType(sourceDbType, sourceCfg) : 'FTP',
    registeredDatasourceId: sourceDbId,
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
// Preserve the connector's registered options instead of silently taking manifest defaults.
// Server-managed Hive keeps only a tenant-owned reference; client secrets stay on the server.
if (isServerManagedHuaweiMrsHive(sourceDbType, sourceCfg)) {
    sourceNodeConfig.hiveProfile = first(sourceCfg, ['hiveProfile', 'hive_profile']) || 'default'
    sourceNodeConfig.metadataAccessMode = 'server-managed-mrs'
    sourceNodeConfig.hiveConnectionMode = 'huawei-mrs'
    sourceNodeConfig.remove('jdbcUrl')
    sourceNodeConfig.remove('username')
    sourceNodeConfig.remove('password')
} else {
    for (connectionKey in sourceCfg.keySet()) {
        if (sourceNodeConfig[connectionKey] == null) { sourceNodeConfig[connectionKey] = sourceCfg[connectionKey] }
    }
}
if (sourceManifestKey == 'source.oceanbase') {
    sourceNodeConfig.compatibleMode = normalizeConfigType(sourceDbType, sourceCfg) == 'OCEANBASE_ORACLE' ? 'ORACLE' : 'MYSQL'
}
// 普通 FTP/SFTP 探查接入也必须使用登记的连接，而不是 JDBC 默认配置。
// 一条任务只读取其来源数据集，不能把目录里的其它表一起送入同一个目标。
if (pushFileSource == null && (sourceManifestKey == 'source.ftp' || sourceManifestKey == 'source.sftp')) {
    // Resolve from the authenticated, tenant-owned registration. Client parser options
    // cannot replace the connection, path, file filter, sheet or JSON dataset selector.
    var directFile = null
    try {
        directFile = structuredFileProbe.resolveFtpFileSource(sourceCfg, text(sourceTableName), body?.sourceFileConfig)
    } catch (fileError) {
        exit 409, '来源文件无法唯一定位或解析，请检查文件及登记字段后重新创建流程'
    }
    var fileColumnNames = []
    for (fileColumn in directFile.columns) { fileColumnNames.add(text(fileColumn.columnName).toLowerCase()) }
    for (registeredColumn in sourceColumnsForNifi) {
        if (!fileColumnNames.contains(text(registeredColumn.columnName).toLowerCase())) {
            exit 409, '来源文件字段与登记快照不一致，请重新采集来源字段后再创建流程'
        }
    }
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
        remotePath: directFile.remotePath,
        fileFilterRegex: directFile.fileFilterRegex,
        sourceFileName: directFile.fileName,
        fileFormat: directFile.format,
        csvEncoding: directFile.charset, csvDelimiter: directFile.delimiter,
        excelSheetName: directFile.excelSheetName, jsonRecordPath: directFile.jsonRecordPath,
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
if (pushFileSource == null && sourceManifestKey == 'source.api') {
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
if (sourceManifestKey == 'source.minio') {
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

var pipelineRows = []
var taskRows = []
var allMappings = []
var allTargetSummaries = []
var originalSourceNodeConfig = sourceNodeConfig
var originalSourceManifestKey = sourceManifestKey
for (targetTable in targetsToCreate) {
sourceTable = hopSourceByTarget[text(targetTable.tid)]
sourceTableId = text(sourceTable.tid)
var firstHop = sourceTableId == rootSourceTableId
sourceDb = firstHop ? rootSourceDb : targetDatasourceById[text(first(sourceTable, ['datasourceId', 'datasource_id']))]
sourceDbId = text(first(sourceTable, ['datasourceId', 'datasource_id']))
sourceCfg = readCfg(sourceDb)
sourceColumns = firstHop ? rootSourceColumns : targetColumnsById[sourceTableId]
sourceTableName = first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
sourceTableLabel = text(first(sourceTable, ['tableNameCn', 'table_name_cn'])).trim()
if (blank(sourceTableLabel)) { sourceTableLabel = text(sourceTableName).trim() }
sourceDbType = first(sourceDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
sourceManifestKey = firstHop ? originalSourceManifestKey : normalizeSourceKey(sourceDbType, sourceCfg)
sourceTemporalMappings = firstHop ? originalSourceTemporalMappings : {}
sourceDictionaryRelations = firstHop ? originalDictionaryRelations : {}
sourceColumnMap = {}
for (sourceColumn in sourceColumns) { sourceColumnMap[text(colName(sourceColumn)).toLowerCase()] = sourceColumn }
sourceNodeConfig = originalSourceNodeConfig
if (!firstHop) {
    sourceService = first(sourceCfg, ['serviceName', 'sid', 'database', 'dbName', 'dbMetaDbName'])
    sourceSchema = first(sourceCfg, ['defaultSchema', 'schema', 'currentSchema', 'schemaName', 'owner', 'dbSchema'])
        || first(sourceTable, ['schemaName', 'schema_name', 'owner', 'tableSchema', 'table_schema'])
    sourceNodeConfig = {
        dbType: normalizeConfigType(sourceDbType, sourceCfg), registeredDatasourceId: sourceDbId,
        host: first(sourceCfg, ['host', 'dbMetaIp']), port: first(sourceCfg, ['port', 'dbMetaPort']),
        database: sourceService, sid: sourceService, serviceName: sourceService,
        connectionType: text(first(sourceCfg, ['connectionType', 'jdbcType'])).toUpperCase() == 'SID' ? 'SID' : 'SERVICE_NAME',
        jdbcUrl: jdbcUrl(sourceDbType, sourceCfg, sourceDb), defaultSchema: sourceSchema, schema: sourceSchema,
        username: first(sourceCfg, ['username', 'dbMetaUser']), password: first(sourceCfg, ['password', 'dbMetaPassword']),
        sourceDbId: sourceDbId, sourceDbName: first(sourceDb, ['dbName', 'db_name']),
        sourceTableId: sourceTableId, sourceTableName: sourceTableName, table: sourceTableName,
        sourceColumns: columnList(sourceColumns), incrementalColumn: firstPk(sourceColumns),
        schedulingPeriod: '60 sec', syncMode: 'FULL_THEN_INCR', initialStrategy: 'START_AT_BEGINNING',
        lookbackSeconds: 5, safetyLagSeconds: 1, batchSize: 5000, maxRowsPerRun: 200000,
        softDeleteEnabled: false, deleteReconcileStrategy: 'NONE'
    }
    if (isServerManagedHuaweiMrsHive(sourceDbType, sourceCfg)) {
        sourceNodeConfig.hiveProfile = first(sourceCfg, ['hiveProfile', 'hive_profile']) || 'default'
        sourceNodeConfig.metadataAccessMode = 'server-managed-mrs'
        sourceNodeConfig.hiveConnectionMode = 'huawei-mrs'
        sourceNodeConfig.remove('jdbcUrl'); sourceNodeConfig.remove('username'); sourceNodeConfig.remove('password')
    } else {
        for (connectionKey in sourceCfg.keySet()) { if (sourceNodeConfig[connectionKey] == null) { sourceNodeConfig[connectionKey] = sourceCfg[connectionKey] } }
    }
    if (sourceManifestKey == 'source.oceanbase') {
        sourceNodeConfig.compatibleMode = normalizeConfigType(sourceDbType, sourceCfg) == 'OCEANBASE_ORACLE' ? 'ORACLE' : 'MYSQL'
    }
    // 下游沿用汇聚库生成的ODS主键和时间，增量以更新时间游标读取。
    for (column in sourceColumns) {
        if (text(colName(column)).toUpperCase() == 'ODS_GXSJ') { sourceNodeConfig.incrementalColumn = colName(column) }
    }
}

var targetTableId = text(targetTable.tid)
var targetDbId = text(first(targetTable, ['datasourceId', 'datasource_id']))
var targetDb = targetDatasourceById[targetDbId]
var targetCfg = readCfg(targetDb)
var targetDbType = first(targetDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
var targetTableName = first(targetTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
var targetColumns = targetColumnsById[targetTableId]
var taskId = IdUtil.getSnowflakeNextIdStr()
var pipelineId = IdUtil.getSnowflakeNextIdStr()
var targetDbLabel = text(first(targetDb, ['dbName', 'db_name'])).trim()
if (blank(targetDbLabel)) { targetDbLabel = targetDbId }
var taskName = sourceTableLabel + '接入任务 → ' + targetDbLabel + ' / ' + targetTableName
if (taskName.length() > 200) { taskName = taskName.substring(0, 200) }
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
    exit 409, '目标表「' + targetTableName + '」缺少字典翻译字段，请先重新物化建表；未创建或替换任务'
}
// This target-specific binding is authoritative for field enhancement.  It is
// resolved after any legacy metadata refresh so the canvas sees final columns.
var targetFieldBindings = firstHop ? targetFieldTransformBindings(sourceTable, targetTable, targetColumns) : []
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
    if (sourceColumn != null && (firstHop ? isStableBusinessKey(sourceColumn) : declaredKey(sourceColumn))
        && (firstHop ? isStableBusinessKey(targetColumn) : declaredKey(targetColumn))) {
        primarySourceKeys.add(colName(sourceColumn))
        primaryTargetKeys.add(targetName)
    }
}
var sourcePrimaryKeys = primarySourceKeys.join(',')
var targetPrimaryKeys = primaryTargetKeys.join(',')
var canUpsert = !blank(sourcePrimaryKeys) && !blank(targetPrimaryKeys)

// 每个独立任务生成来源、自己的字段映射和唯一目标节点。
var sourceNodeId = 'source-' + sourceTableId
var mapNodeId = 'map-' + taskId
var dslNodes = [
    {
        id: sourceNodeId,
        manifestKey: sourceManifestKey,
        label: sourceTableLabel,
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
        label: '字段映射',
        category: 'transform',
        x: 540,
        y: 120,
        config: {
            sourceTableId: sourceTableId,
            targetTableId: targetTableId,
            executionMode: 'AUTO',
            mappings: mappingDsl(sourceColumns, targetColumns, sourceTable, dictionaryLookupSources,
                targetFieldBindings, { datasourceId: sourceDbId, dbType: sourceDbType }, sourceTemporalMappings, !firstHop)
        }
    }
]
var dslEdges = [{ id: 'edge-source-map', source: sourceNodeId, target: mapNodeId, outlet: null }]
var targetSummaries = []
for (targetIndex, targetCandidate in [targetTable]) {
    var candidateTargetId = text(first(targetCandidate, ['tid']))
    var candidateTargetDbId = text(first(targetCandidate, ['datasourceId', 'datasource_id']))
    var candidateTargetDb = targetDatasourceById[candidateTargetDbId]
    var candidateTargetCfg = readCfg(candidateTargetDb)
    var candidateTargetDbType = first(candidateTargetDb, ['dbType', 'db_type', 'databaseType', 'database_type'])
    var candidateTargetTableName = first(targetCandidate, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
    var candidateTargetTableLabel = text(first(targetCandidate, ['tableNameCn', 'table_name_cn'])).trim()
    if (blank(candidateTargetTableLabel)) { candidateTargetTableLabel = text(candidateTargetTableName).trim() }
    var candidateTargetColumns = targetColumnsById[candidateTargetId]
    var candidateTargetKeys = []
    for (candidateTargetColumn in (candidateTargetColumns == null ? [] : candidateTargetColumns)) {
        var candidateTargetName = text(colName(candidateTargetColumn))
        var matchingSourceColumn = sourceColumnMap[candidateTargetName.toLowerCase()]
        // 写入幂等性由目标表的业务主键/唯一键决定。来源表的登记元数据
        // 可能尚未标记 primaryKey（例如历史探查数据），但只要该目标键已有
        // 对应的来源字段映射，就可以安全地作为 UPSERT 键，不能退回 INSERT。
        if (matchingSourceColumn != null && (firstHop ? isStableBusinessKey(candidateTargetColumn) : declaredKey(candidateTargetColumn))) {
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
                || ((normalizeSinkType(candidateTargetDbType, candidateTargetCfg) == 'DM') ? first(candidateTargetCfg, ['database', 'databaseName']) : null),
            targetColumns: columnList(candidateTargetColumns),
            // The production-proven MRS path converts records to CSV and
            // writes them through PutHwHDFS; generic JDBC UPSERT is invalid.
            hiveWriteMode: 'LINEWELL_HDFS',
            statementType: 'INSERT',
            updateKeys: ''
        }
    } else {
        candidateSinkConfig = {
            dbType: normalizeSinkType(candidateTargetDbType, candidateTargetCfg),
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
        label: candidateTargetTableLabel,
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
var dsl = { version: 1, nodes: dslNodes, edges: dslEdges, accessChain: {rootSourceTableId: rootSourceTableId, targetTableIds: targetTableIds} }
pipelineRows.add({
    id: pipelineId,
    tenant_id: tenantId,
    name: taskName,
    description: '由数据接入页面按来源表和目标表创建独立任务',
    status: 'SAVED',
    dsl_json: jsons.toJSONStr(dsl),
    dsl_version: 1,
    created_at: nowMs,
    updated_at: nowMs,
    is_del: 0
})

taskRows.add({
    tid: taskId,
    tenant_id: tenantId,
    data_catalog_id: catalogId,
    task_name: taskName,
    task_type_name: '数据表接入',
    task_type_id: 'table_access',
    task_desc: '来源表到单个目标表的独立接入任务',
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
    var systemMapping = firstHop ? odsSystemMapping(targetName) : null
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
                        query: multiDictionaryQuery(dictionaryRelation, dictionaryName), multiValue: true,
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
        func_enable: blank(sourceTemporalMappings[text(colName(sourceColumn)).toLowerCase()]) ? 0 : 1,
        func_code: blank(sourceTemporalMappings[text(colName(sourceColumn)).toLowerCase()]) ? null : 'EXPRESSION',
        func_value: sourceTemporalMappings[text(colName(sourceColumn)).toLowerCase()],
        sort_no: targetIndex,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}
for (mapping in mappings) { mapping.tenant_id = tenantId; allMappings.add(mapping) }
for (summary in targetSummaries) { allTargetSummaries.add(summary) }
}
if (!dryRun) {
    if (retireTaskIds.size() > 0) {
        db.table('data_access_field_mapping').where().eq('tenant_id', tenantId).in('task_id', retireTaskIds).eq('is_del', 0)
            .update({is_del: 1, updated_time: now})
        db.table('data_access_agg_task_t').where().eq('tenant_id', tenantId).in('tid', retireTaskIds).eq('is_del', 0)
            .update({is_del: 1, updated_time: now})
    }
    if (retirePipelineIds.size() > 0) {
        db.table('nifi_pipeline_t').where().eq('tenant_id', tenantId).in('id', retirePipelineIds).eq('is_del', 0)
            .update({is_del: 1, updated_at: nowMs})
    }
    db.table('nifi_pipeline_t').batchInsert(pipelineRows)
    db.table('data_access_agg_task_t').batchInsert(taskRows)
    if (allMappings.size() > 0) { db.table('data_access_field_mapping').batchInsert(allMappings) }
}
var results = []
for (target in targetTables) {
    var targetId = text(target.tid)
    var reused = existingTaskByTarget[targetId]
    if (!forceRebuild && reused != null) { results.add(reused); continue }
    for (taskRow in taskRows) {
        if (text(taskRow.target_table_id) == targetId) {
            results.add({tid: taskRow.tid, taskName: taskRow.task_name, pipelineId: taskRow.pipeline_id,
                sourceTableId: taskRow.source_table_id, rootSourceTableId: rootSourceTableId, targetTableId: targetId, targetDbId: taskRow.target_db_id,
                repairRequired: false, repairReasons: []})
            break
        }
    }
}
var response = responseOf(results, taskRows.size(), allTargetSummaries)
response.plannedCreateCount = taskRows.size()
if (dryRun) { response.created = false; response.createdCount = 0 }
return response
})
