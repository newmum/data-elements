{
  "properties": {},
  "id": "e3320ec899a24426adf5f77e2dab36f9",
  "script": null,
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "02.数据接入物化建表",
  "createTime": 1782955344712,
  "updateTime": 1790843736643,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/createTapleApply",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"015c09572d174e89b9bea9c765ef72df\",\n  \"propList\": {\"dbId\":\"62d25cc8bf1344d6b9f41fed23af4eaf\",\"tableName\":\"police_reexplore_new_case_clue_TARGET\",\"tableNameCn\":\"公安重新探查新增线索表\"},\n  \"tableItems\": []\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"17fe19f8f69849f88a34352550039ee3\",\n  \"targetTableId\": \"17fe19f8f69849f88a34352550039ee3\",\n  \"catalogId\": \"faae1bc3ebaa4611aded5ed4a52d6a03\",\n  \"sourceTableId\": \"015c09572d174e89b9bea9c765ef72df\",\n  \"physicalTableReused\": false\n}",
  "description": "数据接入物化建表（POST /ods/createTapleApply）。写接口；接收来源表 ID 或旧目录 ID，幂等创建或复用目标库同名物理表，写入 db_table_t/db_table_column_t，自动生成或更新代表目标表的数据目录 da_asset_t/da_prop_t，并同步 ES。用于数据接入-创建任务链路中的来源表=>目标表(目录)物化步骤；使用请求参数 tid、sourceTableId、tableId、sourceCatalogId、catalogId、id、propList、tableItems。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据接入物化建表
// 路由：POST /ods/createTapleApply
// 业务说明：数据接入物化建表（POST /ods/createTapleApply）。写接口；接收来源表 ID 或旧目录 ID，幂等创建或复用目标库同名物理表，写入 db_table_t/db_table_column_t，自动生成或更新代表目标表的数据目录 da_asset_t/da_prop_t，并同步 ES。用于数据接入-创建任务链路中的来源表=>目标表(目录)物化步骤；使用请求参数 tid、sourceTableId、tableId、sourceCatalogId、catalogId、id、propList、tableItems。
import tenantRuntime
import metadataAsset
/*
 * 物化建表并自动补目录。
 *
 * 最新接入链路：
 * 来源表（右侧列表点击的 db_table_t.tid）=> 字段映射 => 目标表（物化到目标库）
 * 目标表会自动生成/更新一条目录资产，目录属性同时保存 sourceTableId 与 targetTableId，
 * 以便继续复用原来的目录/NiFi 模板逻辑。
 */
import cn.hutool.core.util.IdUtil
import jsons
import metadataAsset
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import '@post:/dst/database/metadata/getCreateTableDDL' as getCreateTableDDL
import '@post:/dst/database/metadata/createTable' as createTable

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
var upsertCatalogProp = (catalogId, name, value, now) => {
    var old = db.table('da_prop_t').where()
        .eq('parent_id', catalogId)
        .eq('prop_name', name)
        .eq('is_del', 0)
        .selectOne()
    if (old == null) {
        db.table('da_prop_t').primary('tid').insert({
            tid: IdUtil.getSnowflakeNextIdStr(),
            parent_id: catalogId,
            prop_name: name,
            prop_value: value,
            prop_type: '0',
            data_type: 'catalog',
            is_del: 0,
            created_time: now,
            updated_time: now
        })
    } else {
        db.table('da_prop_t').where()
            .eq('tid', old.tid)
        .update({ prop_value: value, updated_time: now })
    }
}
// ODS ingestion timestamps must use the target database's native temporal type.
// OceanBase Oracle mode uses DATE; Oracle/DM share the same date-time semantics,
// PostgreSQL-like engines and Hive use TIMESTAMP, and SQL Server uses DATETIME2.
var materializationTimeType = (dbType) => {
    var code = text(dbType).trim().toLowerCase()
    if (code == 'oceanbaseoracle' || code == 'oceanbase_oracle' || code == 'oracle' || code == 'dameng' || code == 'dm') {
        return 'date'
    }
    if (code == 'postgresql' || code == 'postgres' || code == 'kingbase' || code == 'kingbase8'
        || code == 'gaussdb' || code == 'opengauss' || code == 'gauss'
        || code == 'highgo' || code == 'vastbase' || code == 'hailiang' || code == 'tdsql_pg' || code == 'tdsql-pg'
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
var ensureOdsSystemColumns = (items, dbType) => {
    var normalized = []
    var hasBusinessPrimaryKey = false
    for (item in (items == null ? [] : items)) {
        if (blank(item.columnName)) {
            continue
        }
        var key = text(item.columnName).toLowerCase()
        if (key == 'ods_uuid' || key == 'ods_rksj' || key == 'ods_gxsj') {
            continue
        }
        var format = text(item.standardField || item.dataStandardId).trim().toUpperCase()
        if (format == 'DATE' || format == 'DATETIME') {
            var timeType = format == 'DATE' ? 'date' : materializationTimeType(dbType)
            item.dataType = timeType
            item.columnType = timeType
            item.length = 0
            item.precisionLength = 0
            item.scale = 0
            item.defaultValue = null
        }
        // Preserve a source business primary key. ODS_UUID is an ingestion id and
        // must not replace it when the target needs to support MERGE/UPSERT.
        var primaryKeyValue = text(item.primaryKey).trim().toLowerCase()
        hasBusinessPrimaryKey = hasBusinessPrimaryKey || primaryKeyValue == '1' || primaryKeyValue == 'true'
        normalized.add(item)
    }
    for (fixed in odsSystemColumns(dbType)) {
        if (text(fixed.columnName).toUpperCase() == 'ODS_UUID') {
            fixed.primaryKey = hasBusinessPrimaryKey ? 0 : 1
            fixed.indexed = hasBusinessPrimaryKey ? 0 : 1
            fixed.isUnique = hasBusinessPrimaryKey ? 0 : 1
        }
        fixed.ordinalPosition = normalized.size()
        normalized.add(fixed)
    }
    return normalized
}
var safeIdentifier = (value) => text(value).trim().matches('^[A-Za-z_][A-Za-z0-9_$]*$')
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
// New registrations keep the snapshot on db_table_t; historical catalog saves
// keep it in da_prop_t and link the catalog through sourceTableId.  Materialize
// requests must accept both formats so existing dictionary registrations work.
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
// dictionary table.  The materialization UI may suggest a sibling <code>_cn
// field, but its actual output column is persisted as an explicit binding
// below and is never inferred from this naming convention at runtime.
var mandatoryStandardMappings = (relation) => {
    var result = []
    if (relation == null) {
        return result
    }
    var forceEnabled = text(relation.forceStandardEnabled).trim().toLowerCase()
    var sourceType = text(relation.sourceType).trim().toLowerCase()
    var mode = text(relation.mode).trim().toLowerCase()
    // A small fixed dictionary may be persisted as sourceType/mode=enum.
    // It uses the same deterministic code-to-label mapping as a mandatory
    // standard and must retain its output binding during materialization.
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
// Production may persist a checked dictionary association only on the source
// column. Resolve that durable registration into a relation before writing the
// target's field-transform bindings; the target must not depend on optional
// source relation JSON being complete.
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
        var dictionaryName = text(first(dictionaryTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])).trim()
        if (safeIdentifier(sourceName) && safeIdentifier(dictionaryName)
            && safeIdentifier(keyField) && safeIdentifier(labelField)) {
            result.add({
                sourceName: sourceName,
                relationKind: 'DICTIONARY',
                dictionaryTable: dictionaryName,
                dictionaryTableId: dictionaryTableId,
                dictionaryKeyField: keyField,
                dictionaryLabelField: labelField,
                conditions: []
            })
        }
    }
    return result
}
// Dictionary relations are stored with the Step 4 field-governance snapshot.
// Only registered business/log fields carry registrationSavedPhase=business.
var dictionaryRelations = (sourceTable) => {
    var config = governanceConfigOf(sourceTable)
    var result = {}
    for (field in (config.fields == null ? [] : config.fields)) {
        var relation = first(field, ['dictionaryRelation', 'dictionary_relation', 'dictionaryConfig', 'dictionary_config'])
        var sourceName = text(first(field, ['columnName', 'column_name', 'fieldName', 'field_name'])).trim()
        var sourceType = text(relation == null ? '' : relation.sourceType).trim().toLowerCase()
        var dictionaryTable = text(relation == null ? '' : first(relation, ['dictionaryTable', 'dictionary_table', 'dictTable', 'dict_table'])).trim()
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
                dictionaryKeyField: keyField,
                dictionaryLabelField: labelField,
                multiValue: text(relation.multiValue).toLowerCase() == 'true',
                multiValueSeparator: blank(relation.multiValueSeparator) ? ',' : text(relation.multiValueSeparator)
            }
        }
    }
    // Explicit source rules retain their category conditions. Column-level
    // dictionary bindings fill only relations missing from older snapshots.
    for (registeredRelation in registeredDictionaryRelations(sourceTable)) {
        var key = registeredRelation.sourceName.toLowerCase()
        if (result[key] == null) {
            result[key] = registeredRelation
        }
    }
    return result
}
var appendDictionaryTranslationColumns = (items, sourceTable) => {
    var relations = dictionaryRelations(sourceTable)
    var names = {}
    for (item in items) {
        names[text(item.columnName).toLowerCase()] = true
    }
    var result = []
    for (item in items) {
        result.add(item)
        var sourceName = text(item.columnName).trim()
        var relation = relations[sourceName.toLowerCase()]
        var translationName = sourceName + '_cn'
        if (relation != null && names[translationName.toLowerCase()] == null) {
            result.add({
                columnName: translationName,
                columnComment: text(item.columnComment).trim() + '翻译',
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
            names[translationName.toLowerCase()] = true
        }
    }
    return result
}

// A target table owns the output-side association of its transformed fields.
// The source table's fieldGovernanceConfig owns the rule definition itself.
// Keeping the binding on the target makes one source safely materializable to
// multiple targets, even when each target uses a different output field name.
var buildFieldTransformBindings = (sourceTable, sourceColumns, targetTableId, tableItems, columns) => {
    var relations = dictionaryRelations(sourceTable)
    var sourceByName = {}
    for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
        sourceByName[text(sourceColumn.columnName).toLowerCase()] = sourceColumn
    }
    var bindings = []
    for (index, item in (tableItems == null ? [] : tableItems)) {
        var targetColumn = columns == null ? null : columns[index]
        var sourceName = text(item.dictionarySourceField).trim()
        var relation = relations[sourceName.toLowerCase()]
        var sourceColumn = sourceByName[sourceName.toLowerCase()]
        if (targetColumn == null || relation == null || sourceColumn == null || blank(item.columnName)) {
            continue
        }
        bindings.add({
            bindingId: targetTableId + ':' + text(targetColumn.tid),
            sourceTableId: text(sourceTable.tid),
            sourceColumnId: text(sourceColumn.tid),
            sourceField: relation.sourceName,
            targetTableId: targetTableId,
            targetColumnId: text(targetColumn.tid),
            targetField: text(item.columnName).trim(),
            ruleType: relation.relationKind,
            governanceField: relation.sourceName,
            enabled: true,
            origin: 'materialization'
        })
    }
    return bindings
}

var inputId = text(body?.tid || body?.sourceTableId || body?.tableId || body?.sourceCatalogId || body?.catalogId || body?.id).trim()
var propList = body?.propList
var tableItems = body?.tableItems
// 租户只来自当前认证请求的 TenantContext，不能从 principal、请求体或历史资产推断。
var tenantId = tenantRuntime.id()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再提交物化建表'
}
if (blank(inputId)) {
    exit 400, 'tid不能为空'
}
if (propList == null) {
    exit 400, 'propList不能为空'
}
if (blank(propList.dbId)) {
    exit 400, 'propList.dbId不能为空'
}
if (blank(propList.tableName)) {
    exit 400, 'propList.tableName不能为空'
}
propList.tableName = text(propList.tableName).trim()
if (tableItems == null || tableItems.size() == 0) {
    exit 400, 'tableItems不能为空'
}

// 兼容旧入口：如果 tid 是目录，则读取目录的来源表；否则 tid 就是来源表。
var catalogId = ''
var sourceTableId = ''
var inputCatalog = null
try {
    inputCatalog = esCommonService.getDocument('dataassets', inputId)
} catch (e) {
    // ES 只作为目录快速判断；失败时继续使用 MySQL 元数据兜底。
    inputCatalog = null
}
if (inputCatalog != null && text(inputCatalog.assetType) == 'catalog') {
    catalogId = inputId
    sourceTableId = text(inputCatalog.sourceTableId)
}
if (blank(sourceTableId)) {
    var sourceProp = db.table('da_prop_t').where()
        .eq('parent_id', inputId)
        .eq('prop_name', 'sourceTableId')
        .eq('is_del', 0)
        .selectOne()
    if (sourceProp != null) {
        catalogId = inputId
        sourceTableId = text(sourceProp.propValue)
    }
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
tableItems = appendDictionaryTranslationColumns(tableItems, sourceTable)

var sourceDatasource = db.table('db_datasource_t').where()
    .eq('tid', sourceTable.datasourceId)
    .eq('is_del', 0)
    .selectOne()

var targetDatasource = db.table('db_datasource_t').where()
    .eq('tenant_id', tenantId)
    .eq('tid', propList.dbId)
    .eq('is_del', 0)
    .selectOne()
if (targetDatasource == null) {
    exit 404, '目标数据源不存在'
}
if (targetDatasource.showConnect == 0) {
    exit 400, '目标数据源未提供连接信息'
}
// The submitted rows are editable client data, so normalize the two protected
// ODS time columns again from the authoritative target datasource before DDL
// generation and metadata persistence.
tableItems = ensureOdsSystemColumns(tableItems, targetDatasource.dbType)
// Keep the submit boundary aligned with the target datasource selector. In addition
// to completed registrations, a datasource explicitly bound to a current-tenant ODS
// domain is a governed materialization target and must not be rejected merely
// because it is managed as a warehouse target rather than a normal registered source.
var targetAssetStatus = text(first(targetDatasource, ['assetStatus', 'asset_status']))
var targetRegistered = ['1', '2'].contains(targetAssetStatus)
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
var targetOdsDomainBound = false
if (odsNode != null) {
    var odsDomains = db.table('sym_dict_t').where()
        .eq('tenant_id', tenantId)
        .eq('parent_id', odsNode.tid)
        .eq('is_del', 0)
        .select()
    var odsDomainIds = []
    for (odsDomain in (odsDomains == null ? [] : odsDomains)) {
        odsDomainIds.add(text(odsDomain.tid))
    }
    if (odsDomainIds.size() > 0) {
        var odsTargetBinding = db.table('dwm_center_layer_source_t').where()
            .eq('tenant_id', tenantId)
            .eq('target_type', 'domain')
            .in('target_id', odsDomainIds)
            .eq('datasource_id', propList.dbId)
            .eq('is_del', 0)
            .selectOne()
        targetOdsDomainBound = odsTargetBinding != null
    }
}
if (!targetRegistered && !targetOdsDomainBound) {
    exit 400, '目标数据源未完成登记且未配置为贴源层 ODS 分域的数据源，不能用于物化建表'
}
// A manually edited statement is validated against the selected tenant-owned
// datasource and exact target table before any placeholder metadata is changed.
var submittedDdl = text(body?.ddl).trim()
if (!blank(submittedDdl)) {
    var validationBody = body
    body = { canvas: true, validateOnly: true, ddl: submittedDdl,
        dbId: propList.dbId, tableName: propList.tableName }
    createTable()
    body = validationBody
}
// 表名由用户在物化抽屉中最终确认。按当前目标数据源枚举后做 trim + 不区分大小写
// 对比，避免历史空格或数据库大小写规则造成“看似不同、实际重复”的误导。
var duplicateRows = db.table('db_table_t').where()
    .eq('datasource_id', propList.dbId)
    .eq('is_del', 0)
    .select()
var duplicate = null
var reuseTargetTable = false
var duplicateCheckTime = new Date()
for (candidate in (duplicateRows == null ? [] : duplicateRows)) {
    if (text(first(candidate, ['tableName', 'table_name'])).trim().toLowerCase() != propList.tableName.toLowerCase()) {
        continue
    }
    var candidateId = text(candidate.tid)
    var candidateSourceId = text(candidate.sourceTableId)
    if (candidateSourceId == sourceTableId) {
        duplicate = candidate
        reuseTargetTable = true
        break
    }
    var columnCount = db.table('db_table_column_t').where()
        .eq('table_id', candidateId)
        .eq('is_del', 0)
        .count()
    var sourceTaskCount = db.table('data_access_agg_task_t').where()
        .eq('source_table_id', candidateId)
        .eq('is_del', 0)
        .count()
    var targetTaskCount = db.table('data_access_agg_task_t').where()
        .eq('target_table_id', candidateId)
        .eq('is_del', 0)
        .count()
    var propCount = db.table('da_prop_t').where()
        .eq('parent_id', candidateId)
        .eq('is_del', 0)
        .count()
    var stalePlaceholder = (blank(candidateSourceId) || candidateSourceId == candidateId)
        && columnCount == 0
        && sourceTaskCount == 0
        && targetTaskCount == 0
        && propCount == 0
    if (stalePlaceholder) {
        db.table('db_table_t').where().eq('tid', candidateId)
            .update({ is_del: 1, updated_time: duplicateCheckTime })
    } else {
        exit 400, '目标数据表「' + propList.tableName + '」已存在于目标数据源「'
            + text(first(targetDatasource, ['dbName', 'db_name'])) + '」，请修改表名后重试'
    }
}

// 表字段名称去重，避免 DDL 创建失败后才暴露错误。
var names = {}
for (index, item in tableItems) {
    if (blank(item.columnName)) {
        exit 400, '第' + (index + 1) + '个字段名称不能为空'
    }
    var key = text(item.columnName).toLowerCase()
    if (names[key] != null) {
        exit 400, '第' + names[key] + '个和第' + (index + 1) + '个字段名称重复'
    }
    names[key] = index + 1
}

// 复用现有 DDL 生成和建表能力，保持各数据库类型的适配口径统一。
// 如果上一次请求已经完成物理建表和 MySQL 目标表落库、但在 ES 同步处失败，
// 本次重试直接复用同一目标表，避免重复建表/重复插主表。
var originalBody = body
var physicalTableReused = reuseTargetTable
if (!reuseTargetTable) {
    var ddl = submittedDdl
    if (blank(ddl)) {
        var ddlRequestBody = {
            // Preserve the existing generated-DDL path for clients that do not
            // explicitly submit an edited statement.
            canvas: true,
            propList: propList,
            tableItems: tableItems
        }
        body = ddlRequestBody
        ddl = getCreateTableDDL()
    }
    var createTableRequestBody = {
        // Keep the execution strategy aligned with DDL generation: ordinary
        // targets use JDBC, while server-managed MRS Hive uses hwhive on the
        // deployment host and never reads a browser-side JDBC URL.
        canvas: true,
        ddl: ddl,
        dbId: propList.dbId,
        tableName: propList.tableName,
        skipIfExists: true
    }
    body = createTableRequestBody
    var createResult = createTable()
    body = originalBody
    // A physical table that is not registered for this source must not be adopted silently.
    // Otherwise the next task creation can point at an incomplete target and open an empty canvas.
    if (createResult != null && createResult.created == false) {
        exit 409, '目标物理表已存在，请先删除目标表或改用其他目标表名后再创建任务'
    }
    physicalTableReused = createResult != null && createResult.created == false
}

var now = new Date()
var targetTableId = reuseTargetTable ? text(duplicate.tid) : IdUtil.getSnowflakeNextIdStr()
var tableNameCn = propList.tableNameCn || propList.tableName
var lowerName = text(propList.tableName).toLowerCase()
var businessType = lowerName.endsWith('log') ? '日志表'
    : lowerName.endsWith('dict') ? '字典表'
    : lowerName.endsWith('temp') || lowerName.endsWith('tmp') ? '临时表'
    : (first(sourceTable, ['businessType', 'business_type']) || '业务表')

// 写目标表主数据，并用 source_table_id 明确来源表。
var targetTableValues = {
    datasource_id: propList.dbId,
    table_name: propList.tableName,
    table_name_cn: tableNameCn,
    table_comment: tableNameCn,
    table_type: propList.tableType || '数据表',
    business_type: businessType,
    record_count: 0,
    field_count: tableItems.size(),
    storage_size: '0 KB',
    total_size_bytes: 0,
    total_size_formatted: '0 KB',
    related_directory: '自动生成目录',
    source_table_id: sourceTableId,
    source_table_name: first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
    org_id: sourceTable.orgId || targetDatasource.orgId,
    tenant_id: tenantId,
    is_del: 0,
    asset_status: 2,
    flow_status: 2,
    updated_time: now
}
if (reuseTargetTable) {
    db.table('db_table_t').where().eq('tid', targetTableId).update(targetTableValues)
    db.table('db_table_column_t').where().eq('table_id', targetTableId).update({ is_del: 1, updated_time: now })
} else {
    targetTableValues.tid = targetTableId
    targetTableValues.reg_time = now
    targetTableValues.created_time = now
    db.table('db_table_t').primary('tid').insert(targetTableValues)
}

var columns = []
var sourceColumns = db.table('db_table_column_t').where()
    .eq('table_id', sourceTableId)
    .eq('is_del', 0)
    .select()
var sourceColumnMap = {}
for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
    sourceColumnMap[text(sourceColumn.columnName).toLowerCase()] = sourceColumn
}
for (index, item in tableItems) {
    var sourceColumn = sourceColumnMap[text(item.columnName).toLowerCase()]
    if (sourceColumn == null && !blank(item.dictionarySourceField)) {
        sourceColumn = sourceColumnMap[text(item.dictionarySourceField).toLowerCase()]
    }
    columns.add({
        tid: IdUtil.getSnowflakeNextIdStr(),
        table_id: targetTableId,
        column_name: item.columnName,
        column_comment: item.columnComment,
        data_type: item.dataType,
        column_type: item.columnType,
        length: item.length,
        precision_length: item.precisionLength,
        scale: item.scale,
        nullable: item.nullable,
        default_value: item.defaultValue,
        primary_key: item.primaryKey,
        auto_increment: item.autoIncrement,
        is_unique: item.isUnique,
        indexed: item.indexed,
        ordinal_position: index,
        charset: item.charset,
        collation: item.collation,
        extra: item.extra,
        source_table_column_id: sourceColumn == null ? null : sourceColumn.tid,
        tenant_id: tenantId,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}
// Java 通用持久化层依据当前数据库的标识符规则引用保留字列，兼容 MySQL、达梦等数据库。
try {
    metadataAsset.batchInsertTableColumns(columns)
} catch (e) {
    // 当前运行包存在 MySQL 驱动批处理兼容问题时，回退为 Magic 命名表逐行插入。
    for (column in columns) {
        db.table('db_table_column_t').primary('tid').insert(column)
    }
}

// Persist a first-class source-to-target binding for every dictionary
// translation/mandatory-standard output.  <source>_cn remains the default
// field created by the UI, but the workflow later reads this binding rather
// than deriving semantics from the target field name.
var targetGovernanceConfig = {
    version: 3,
    registrationSavedPhase: 'materialized',
    sourceTableId: sourceTableId,
    sourceTableName: first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
    fieldTransformBindings: buildFieldTransformBindings(sourceTable, sourceColumns, targetTableId, tableItems, columns)
}
db.table('db_table_t').where().eq('tid', targetTableId).eq('is_del', 0)
    .update({ field_governance_config: jsons.toJSONStr(targetGovernanceConfig), updated_time: now })

// 目标表同步到 ES，供列表和详情快速读取。
var targetDoc = {
    tid: targetTableId,
    assetType: 'table',
    datasourceId: propList.dbId,
    dbId: propList.dbId,
    dbName: targetDatasource.dbName,
    appId: targetDatasource.appId,
    orgId: targetDatasource.orgId,
    tableName: propList.tableName,
    tableNameCn: tableNameCn,
    tableComment: tableNameCn,
    tableType: propList.tableType || '数据表',
    businessType: businessType,
    sourceTableId: sourceTableId,
    sourceTableName: first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en']),
    recordCount: 0,
    fieldCount: tableItems.size(),
    storageSize: '0 KB',
    totalSizeBytes: 0,
    totalSizeFormatted: '0 KB',
    assetStatus: 2,
    flowStatus: 2,
    regTime: date_format(now),
    createdTime: date_format(now),
    updatedTime: date_format(now),
    regTime_date: date_format(now),
    createdTime_date: date_format(now),
    updatedTime_date: date_format(now)
}
try {
    esCommonService.saveOrUpdate('dataassets', targetTableId, jsons.toJSONStr(targetDoc), false)
} catch (e) {
    // elements_dataassets 是混合投影索引，历史 mapping 里 datasourceId 等字符串字段可能是 text。
    // saveOrUpdate 的动态 mapping 校验会把普通字符串推断为 keyword 并误判冲突；
    // 这里目标表文档字段均为 ES 可接受的普通标量，回退到直接按 id 写入，避免物化链路中断。
    try {
        esCommonService.save('dataassets', targetTableId, targetDoc)
    } catch (ignored) {
        // ES 临时不可用时不阻断物理表和 MySQL 元数据提交。
    }
}

// 如果来源表已有自动目录则复用，否则新建。目录代表目标表，但保留来源表属性形成完整链路。
if (blank(catalogId)) {
    var sourceCatalogProps = db.table('da_prop_t').where()
        .eq('prop_name', 'sourceTableId')
        .eq('prop_value', sourceTableId)
        .eq('is_del', 0)
        .select()
    for (sourceCatalogProp in (sourceCatalogProps == null ? [] : sourceCatalogProps)) {
        var sourceCatalogAsset = db.table('da_catalog_t').where()
            .eq('tid', sourceCatalogProp.parentId)
            .eq('is_del', 0)
            .selectOne()
        if (sourceCatalogAsset != null && blank(catalogId)) {
            catalogId = text(sourceCatalogProp.parentId)
        }
    }
}
if (blank(catalogId)) {
    catalogId = IdUtil.getSnowflakeNextIdStr()
    db.table('da_catalog_t').primary('tid').insert({
        tid: catalogId,
        is_del: 0,
        reg_time: now,
        asset_status: 2,
        flow_status: 2,
        created_time: now,
        updated_time: now
    })
} else {
    db.table('da_catalog_t').where()
        .eq('tid', catalogId)
        .update({ updated_time: now, asset_status: 2, flow_status: 2, is_del: 0 })
}

var catalogName = tableNameCn
var sourceTableName = first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
upsertCatalogProp(catalogId, 'catalogName', catalogName, now)
upsertCatalogProp(catalogId, 'catalogNameEn', propList.tableName, now)
upsertCatalogProp(catalogId, 'assetDesc', catalogName, now)
upsertCatalogProp(catalogId, 'dataSourceType', 'ods', now)
upsertCatalogProp(catalogId, 'businessType', businessType, now)
upsertCatalogProp(catalogId, 'sourceDbId', sourceTable.datasourceId, now)
upsertCatalogProp(catalogId, 'sourceDbName', sourceDatasource == null ? '' : sourceDatasource.dbName, now)
upsertCatalogProp(catalogId, 'sourceTableId', sourceTableId, now)
upsertCatalogProp(catalogId, 'sourceTableName', sourceTableName, now)
upsertCatalogProp(catalogId, 'targetDbId', propList.dbId, now)
upsertCatalogProp(catalogId, 'targetDbType', targetDatasource.dbType || 'mysql', now)
upsertCatalogProp(catalogId, 'targetTableId', targetTableId, now)
upsertCatalogProp(catalogId, 'targetTableName', propList.tableName, now)
upsertCatalogProp(catalogId, 'dbId', propList.dbId, now)
upsertCatalogProp(catalogId, 'orgId', sourceTable.orgId || targetDatasource.orgId, now)
upsertCatalogProp(catalogId, 'manageUnit', sourceTable.orgId || targetDatasource.orgId, now)

var catalogDoc = {
    tid: catalogId,
    assetType: 'catalog',
    catalogName: catalogName,
    catalogNameEn: propList.tableName,
    assetDesc: catalogName,
    dataSourceType: 'ods',
    businessType: businessType,
    sourceDbId: sourceTable.datasourceId,
    sourceDbName: sourceDatasource == null ? '' : sourceDatasource.dbName,
    sourceTableId: sourceTableId,
    sourceTableName: sourceTableName,
    targetDbId: propList.dbId,
    targetDbType: targetDatasource.dbType || 'mysql',
    targetTableId: targetTableId,
    targetTableName: propList.tableName,
    dbId: propList.dbId,
    orgId: sourceTable.orgId || targetDatasource.orgId,
    assetStatus: 2,
    flowStatus: 2,
    regTime: date_format(now),
    createdTime: date_format(now),
    updatedTime: date_format(now)
}
try {
    esCommonService.saveOrUpdate('dataassets', catalogId, jsons.toJSONStr(catalogDoc), false)
} catch (e) {
    try {
        esCommonService.save('dataassets', catalogId, catalogDoc)
    } catch (ignored) {
        // ES 临时不可用时不阻断物理表和 MySQL 元数据提交。
    }
}

// 反写来源表与目标表的目录关联，后续列表和任务创建都能直接定位。
db.table('db_table_t').where().eq('tid', sourceTableId).eq('is_del', 0)
    .update({ source_catalog_id: catalogId, catalog_name: catalogName, updated_time: now })
db.table('db_table_t').where().eq('tid', targetTableId).eq('is_del', 0)
    .update({ source_catalog_id: catalogId, catalog_name: catalogName, updated_time: now })

metadataAsset.refreshRegistrationStatus(propList.dbId, false)
return {
    tid: targetTableId,
    targetTableId: targetTableId,
    catalogId: catalogId,
    sourceTableId: sourceTableId,
    physicalTableReused: physicalTableReused
}
