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
  "description": "数据接入物化建表（POST /ods/createTapleApply）。单目标沿用 propList/tableItems。多目标传 tid、targets（最多20），全部预检后按ODS配置顺序执行，按原请求顺序返回逐项结果。dryRun=true只读验证请求完整性和资源权限，不验证目标SQL语法。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import metadataAsset
import request
import tableRead
import log
import cn.hutool.core.util.IdUtil
import org.redisson.api.RedissonClient as redissonClient
import org.springframework.jdbc.core.JdbcTemplate as platformJdbc
import jsons
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver as connectionProperties
import com.linewell.dataelement.shared.error.ErrorDetailSanitizer
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import '@post:/dst/database/metadata/getCreateTableDDL' as getCreateTableDDL
import '@post:/dst/database/metadata/createTable' as createTable

// POST /ods/createTapleApply：复用现有物理建表与登记逻辑；批量入口先完整验证，再顺序执行。
var text = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || text(value).trim() == ''
// Magic 闭包定义时须已有租户变量，避免治理读取/目录属性写入捕获空上下文。
var tenantId = tenantRuntime.id()
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
// Hive 物理表名沿用元数据接口的大小写规则，其他数据库保留登记名。
var normalizePhysicalTargetName = (name, targetType) => ['hive', 'mrshive'].contains(targetType) ? name.toLowerCase() : name
var requireMetadataUpdate = affected => {
    if (affected != 1) { throw new IllegalStateException('登记元数据已变化，请重新检查目标后再提交') }
}
var upsertCatalogProp = (catalogId, existingProps, name, value, now) => {
    var old = existingProps[name]
    if (old == null) {
        db.table('da_prop_t').primary('tid').insert({
            tid: IdUtil.getSnowflakeNextIdStr(),
            tenant_id: tenantId,
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
        var affected = db.table('da_prop_t').where()
            .eq('tenant_id', tenantId)
            .eq('tid', old.tid)
            .eq('is_del', 0)
        .update({ prop_value: value, updated_time: now })
        requireMetadataUpdate(affected)
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
var metadataTableById = {}
var metadataColumnsByTableId = {}
// code_table_id 兼容列同时承载标准字典ID和数据库表ID，必须先按登记关联类型判别。
var physicalDictionaryRelation = (relation) => {
    if (relation == null) { return true }
    if (['false','0','no'].contains(text(relation.enabled).trim().toLowerCase())) { return false }
    var sourceType = text(relation.sourceType).trim().toLowerCase()
    var mode = text(relation.mode).trim().toLowerCase()
    var forced = text(relation.forceStandardEnabled).trim().toLowerCase()
    return !(['standard','enum'].contains(sourceType) || ['standard','enum'].contains(mode)
        || ['true','1','yes'].contains(forced))
}
var dictionaryRelationsByField = (table) => {
    var result = {}
    var config = governanceConfigOf(table)
    for (field in (config.fields == null ? [] : config.fields)) {
        var name = text(first(field, ['columnName','column_name','fieldName','field_name'])).trim().toLowerCase()
        if (!blank(name)) { result[name] = first(field, ['dictionaryRelation','dictionary_relation','dictionaryConfig','dictionary_config']) }
    }
    return result
}
var registeredDictionaryRelations = (sourceTable) => {
    var result = []
    var sourceTableId = text(first(sourceTable, ['tid', 'id', 'tableId', 'table_id'])).trim()
    if (blank(sourceTableId)) { return result }
    var sourceColumns = metadataColumnsByTableId[sourceTableId]
    var fieldRelations = dictionaryRelationsByField(sourceTable)
    for (sourceColumn in (sourceColumns == null ? [] : sourceColumns)) {
        var sourceName = text(first(sourceColumn, ['columnName', 'column_name'])).trim()
        if (!physicalDictionaryRelation(fieldRelations[sourceName.toLowerCase()])) { continue }
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
var appendDictionaryTranslationColumns = (items, relations) => {
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
var buildFieldTransformBindings = (sourceTable, sourceByName, relations, targetTableId, tableItems, columns) => {
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
// 租户只来自当前认证请求的 TenantContext，不能从 principal、请求体或历史资产推断。
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再提交物化建表'
}
if (blank(inputId)) {
    exit 400, 'tid不能为空'
}
var batch = body?.targets != null
var dryRun = body?.dryRun == true
var submittedTargets = batch ? body.targets : [body]
if (!(submittedTargets instanceof List) || submittedTargets.size() == 0 || submittedTargets.size() > 20) {
    exit 400, '一次最多提交20个物化目标'
}
var plans = []
var targetDbIds = []
var totalFields = 0
var totalDdlSize = 0
var totalPartitions = 0
for (target in submittedTargets) {
    var props = target?.propList
    var fields = target?.tableItems
    var partitionPlan = target?.partitionPlan
    if (!(props instanceof Map)) { exit 400, 'propList不能为空' }
    var dbId = text(props.dbId).trim()
    var name = text(props.tableName).trim()
    var ddl = text(target?.ddl).trim()
    if (blank(dbId) || (batch && dbId.length() > 64)) { exit 400, 'propList.dbId不合法' }
    if (blank(name) || (batch && name.length() > 256)) { exit 400, 'propList.tableName不合法' }
    if (!(fields instanceof List) || fields.size() == 0) { exit 400, 'tableItems不能为空' }
    if (batch && (fields.size() > 2000 || blank(ddl) || ddl.length() > 1000000
            || target?.validateReuseOnly == true || target?.dryRun == true
            || (target?.pendingTargetCleanup == true && !dryRun)
            || (target?.pendingTargetCleanup == true && target?.reuseExistingTable == true))) {
        exit 400, '物化目标字段、建表语句或清理选项不合法'
    }
    if (batch) {
        for (field in fields) {
            if (!(field instanceof Map)) { exit 400, '物化目标字段定义格式不合法' }
        }
        if (partitionPlan != null && (!(partitionPlan instanceof Map) || (partitionPlan.partitions != null
                && (!(partitionPlan.partitions instanceof List) || partitionPlan.partitions.size() > 2000)))) {
            exit 400, '物化目标分区计划格式不合法'
        }
        if (partitionPlan?.partitions != null) { totalPartitions += partitionPlan.partitions.size() }
    }
    if (targetDbIds.contains(dbId)) { exit 400, '同一目标库一次只能提交一张物化表' }
    targetDbIds.add(dbId)
    totalFields += fields.size()
    totalDdlSize += ddl.length()
    var properties = {}
    properties.putAll(props)
    properties.dbId = dbId
    properties.tableName = name
    plans.add({inputIndex: plans.size(), propList: properties, tableItems: fields, ddl: ddl,
        partitionPlan: partitionPlan, confirmReuse: target?.reuseExistingTable == true,
        recreateMissing: target?.recreateMissingTable == true, validateReuseOnly: target?.validateReuseOnly == true,
        pendingCleanup: batch && dryRun && target?.pendingTargetCleanup == true})
}
if (batch && (totalFields > 10000 || totalPartitions > 10000 || totalDdlSize > 4000000)) {
    exit 400, '物化批次最多10000个字段、10000个分区和400万个SQL字符，请缩小提交范围'
}

var runMaterialization = () => {
    // 兼容旧入口：如果 tid 是目录，则读取目录的来源表；否则 tid 就是来源表。
    var catalogId = ''
    var sourceTableId = ''
    if (!batch) {
        try {
            var inputCatalog = esCommonService.getDocument('dataassets', inputId)
            if (inputCatalog != null && text(inputCatalog.assetType) == 'catalog') {
                catalogId = inputId
                sourceTableId = text(inputCatalog.sourceTableId)
            }
        } catch (e) {
            // 单目标兼容旧目录投影，批量以当前租户的登记关联为准。
        }
    }
    if (blank(sourceTableId)) {
        var sourceProp = db.table('da_prop_t').where()
            .eq('tenant_id', tenantId)
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
        .eq('tenant_id', tenantId)
        .eq('tid', sourceTableId)
        .eq('is_del', 0)
        .selectOne()
    if (sourceTable == null) {
        exit 404, '来源表不存在'
    }
    if (text(first(sourceTable, ['tableType', 'table_type'])).toUpperCase() == 'FILE') {
        exit 422, '文件登记项用于非结构化文件上传，不能物化为结构化数据库表'
    }

    var allDbIds = [text(sourceTable.datasourceId)]
    for (id in targetDbIds) { if (!allDbIds.contains(id)) { allDbIds.add(id) } }
    var datasources = db.table('db_datasource_t').where().eq('tenant_id', tenantId).in('tid', allDbIds).eq('is_del', 0).select()
    var datasourceById = {}
    for (row in datasources) { datasourceById[text(row.tid)] = row }
    var sourceDatasource = datasourceById[text(sourceTable.datasourceId)]
    if (sourceDatasource == null) { exit 404, '来源数据源不存在或不属于当前租户' }
    for (plan in plans) {
        plan.datasource = datasourceById[plan.propList.dbId]
        if (plan.datasource == null) { exit 404, '目标数据源不存在或不属于当前租户' }
    }
    if (batch && ((first(sourceTable, ['assetStatus', 'asset_status']) || 0)::int <= 0
            || (first(sourceDatasource, ['assetStatus', 'asset_status']) || 0)::int <= 0)) {
        exit 409, '来源数据源和来源表均须完成登记后再物化建表'
    }
    if (!blank(catalogId)) {
        var selectedCatalog = db.table('da_catalog_t').where().eq('tenant_id', tenantId).eq('tid', catalogId).eq('is_del', 0).selectOne()
        if (selectedCatalog == null) { exit 403, '来源目录不存在或不属于当前租户' }
        // 兼容入口已读取的目录也纳入治理快照，避免缺少关系属性时把已有计数当作新目录0。
        governanceCatalogById[text(catalogId)] = selectedCatalog
    }
    // 来源与关联字典一次批量预取；字段遍历及治理 helper 只读内存映射。
    var sourceColumns = db.table('db_table_column_t').where().eq('tenant_id', tenantId)
        .eq('table_id', sourceTableId).eq('is_del', 0).select()
    metadataTableById[sourceTableId] = sourceTable
    metadataColumnsByTableId[sourceTableId] = sourceColumns
    // 先加载来源表/目录的治理快照，再解析兼容列中的字典引用类型。
    loadGovernanceForTableIds([sourceTableId])
    var sourceFieldRelations = dictionaryRelationsByField(sourceTable)
    var dictionaryIds = []
    var dictionaryFieldsById = {}
    for (column in sourceColumns) {
        var fieldName = text(first(column, ['columnName','column_name'])).trim()
        if (!physicalDictionaryRelation(sourceFieldRelations[fieldName.toLowerCase()])) { continue }
        var dictionaryId = text(first(column, ['codeTableId','code_table_id','dictId','dict_id'])).trim()
        if (!blank(dictionaryId)) {
            if (!dictionaryIds.contains(dictionaryId)) { dictionaryIds.add(dictionaryId) }
            if (dictionaryFieldsById[dictionaryId] == null) { dictionaryFieldsById[dictionaryId] = [] }
            dictionaryFieldsById[dictionaryId].add(fieldName)
        }
    }
    if (dictionaryIds.size() > 199) { exit 400, '单次最多处理199个关联字典表，请缩小字段范围' }
    if (dictionaryIds.size() > 0) {
        var dictionaryRows = db.table('db_table_t').where().eq('tenant_id', tenantId).in('tid', dictionaryIds).eq('is_del', 0).select()
        var dictionaryDbIds = []
        for (dictionary in dictionaryRows) {
            metadataTableById[text(dictionary.tid)] = dictionary
            if (!dictionaryDbIds.contains(text(dictionary.datasourceId))) { dictionaryDbIds.add(text(dictionary.datasourceId)) }
        }
        var dictionaryDbRows = dictionaryDbIds.size() == 0 ? [] : db.table('db_datasource_t').where()
            .eq('tenant_id', tenantId).in('tid', dictionaryDbIds).eq('is_del', 0).select()
        var visibleDictionaryDbs = (dictionaryDbRows)
        var permittedDictionaryDbIds = []
        for (dictionaryDb in visibleDictionaryDbs) { permittedDictionaryDbIds.add(text(dictionaryDb.tid)) }
        for (dictionaryId in dictionaryIds) {
            var dictionary = metadataTableById[dictionaryId]
            var fieldNames = dictionaryFieldsById[dictionaryId].join('、')
            if (dictionary == null) { exit 403, '字段“' + fieldNames + '”关联的数据库字典表不存在、已删除或不属于当前租户，请在登记第4步核对字典表关联' }
            if (!permittedDictionaryDbIds.contains(text(dictionary.datasourceId))) { exit 403, '字段“' + fieldNames + '”关联的字典数据源不存在或已删除' }
        }
        var dictionaryColumns = db.table('db_table_column_t').where().eq('tenant_id', tenantId).in('table_id', dictionaryIds).eq('is_del', 0).select()
        for (column in dictionaryColumns) {
            var ownerId = text(column.tableId)
            if (metadataColumnsByTableId[ownerId] == null) { metadataColumnsByTableId[ownerId] = [] }
            metadataColumnsByTableId[ownerId].add(column)
        }
    }
    loadGovernanceForTableIds(dictionaryIds)
    var relations = dictionaryRelations(sourceTable)
    var sourceColumnMap = {}
    for (column in sourceColumns) { sourceColumnMap[text(column.columnName).toLowerCase()] = column }

    // ODS 目标按配置分域的顺序执行。先批量读取绑定，再验证每个目标的准入条件。
    var dataSourceTypeRoot = db.table('sym_dict_t').where().eq('tenant_id', tenantId)
        .eq('dict_code', 'dataSourceType').eq('parent_id', '0').eq('is_del', 0).selectOne()
    var odsNode = dataSourceTypeRoot == null ? null : db.table('sym_dict_t').where().eq('tenant_id', tenantId)
        .eq('parent_id', dataSourceTypeRoot.tid).eq('dict_code', 'ods').eq('is_del', 0).selectOne()
    var odsRankByDb = {}
    if (odsNode != null) {
        var domains = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', odsNode.tid)
            .eq('is_del', 0).orderBy('sort_no', 'asc').orderBy('tid', 'asc').select()
        var domainIds = domains.map(row => text(row.tid))
        if (domainIds.size() > 0) {
            var bindings = db.table('dwm_center_layer_source_t').where().eq('tenant_id', tenantId)
                .eq('target_type', 'domain').in('target_id', domainIds).in('datasource_id', targetDbIds)
                .eq('is_del', 0).select()
            for (binding in bindings) {
                var id = text(first(binding, ['datasourceId', 'datasource_id']))
                var rank = domainIds.indexOf(text(first(binding, ['targetId', 'target_id'])))
                if (odsRankByDb[id] == null || rank < odsRankByDb[id]) { odsRankByDb[id] = rank }
            }
        }
    }
    var needsReferenceCheck = false
    for (plan in plans) {
        var target = plan.datasource
        var targetType = text(first(target, ['dbType', 'db_type'])).trim().toLowerCase()
        var normalizedType = targetType.replace('_', '').replace('-', '')
        plan.hive = ['hive', 'mrshive', 'huaweihive', 'huaweimrshive'].contains(normalizedType)
        if (target.showConnect == 0) { exit 400, '目标数据源未提供连接信息' }
        if (batch && blank(targetType)) { exit 400, '目标数据源未配置数据库类型' }
        if (!['1', '2'].contains(text(first(target, ['assetStatus', 'asset_status'])))
                && odsRankByDb[plan.propList.dbId] == null) {
            exit 400, '目标数据源未完成登记且未配置为贴源层 ODS 分域的数据源，不能用于物化建表'
        }
        if (plan.hive && plan.pendingCleanup) { exit 400, 'Hive 目标只能明确复用，不能通过物化自动删除' }
        plan.propList.tableName = normalizePhysicalTargetName(plan.propList.tableName, targetType)
        if (plan.propList.dbId == text(sourceTable.datasourceId)
                && plan.propList.tableName.toLowerCase() == text(first(sourceTable, ['tableName', 'table_name'])).trim().toLowerCase()) {
            exit 409, '不能将来源表自身复用为物化目标表'
        }
        plan.tableItems = ensureOdsSystemColumns(appendDictionaryTranslationColumns(plan.tableItems, relations), target.dbType)
        var names = {}
        for (index, item in plan.tableItems) {
            if (blank(item.columnName)) { exit 400, '第' + (index + 1) + '个字段名称不能为空' }
            var name = text(item.columnName).toLowerCase()
            if (names[name] != null) { exit 400, '第' + names[name] + '个和第' + (index + 1) + '个字段名称重复' }
            names[name] = index + 1
        }
        if (plan.confirmReuse || plan.pendingCleanup) { needsReferenceCheck = true }
    }

    // 以目标库与表名配对查询，兼容历史首尾带空格的登记；下方再精确核对归一化名称。
    var duplicateRows = []
    if (batch) {
        var field = (name, type) => tableRead.column('t', name, type)
        var idColumn = field('tid', 'TEXT')
        var datasourceColumn = field('datasource_id', 'TEXT')
        var nameColumn = field('table_name', 'TEXT')
        var padded = tableRead.any([tableRead.like(nameColumn, ' %'), tableRead.like(nameColumn, '% ')])
        var targetNames = plans.map(plan => {
            var name = plan.propList.tableName.toLowerCase()
            return tableRead.all([
                tableRead.equal(datasourceColumn, plan.propList.dbId),
                tableRead.any([tableRead.equal(tableRead.lower(nameColumn), name),
                    tableRead.all([padded, tableRead.contains(nameColumn, name)])])
            ])
        })
        var candidates = tableRead.from('db_table_t', 't', tenantId)
        candidates.where(tableRead.equal(field('is_del', 'NUMBER'), 0))
        candidates.where(tableRead.any(targetNames))
        duplicateRows = candidates.rows({tid:idColumn, datasourceId:datasourceColumn, tableName:nameColumn,
            sourceTableId:field('source_table_id','TEXT'), recordCount:field('record_count','NUMBER'),
            storageSize:field('storage_size','TEXT'), totalSizeBytes:field('total_size_bytes','NUMBER'),
            totalSizeFormatted:field('total_size_formatted','TEXT')}, idColumn, 'asc', 401)
        if (duplicateRows.size() > 400) {
            exit 409, '目标表候选元数据过多，请先核对登记记录'
        }
    } else {
        duplicateRows = db.table('db_table_t').where().eq('tenant_id', tenantId)
            .eq('datasource_id', targetDbIds[0]).eq('is_del', 0).select()
    }
    var planByDb = {}
    for (plan in plans) { plan.matchingRows = []; planByDb[plan.propList.dbId] = plan }
    var candidateIds = []
    for (candidate in duplicateRows) {
        var plan = planByDb[text(first(candidate, ['datasourceId', 'datasource_id']))]
        if (plan != null && text(first(candidate, ['tableName', 'table_name'])).trim().toLowerCase()
                == plan.propList.tableName.toLowerCase()) {
            plan.matchingRows.add(candidate)
            candidateIds.add(text(candidate.tid))
        }
    }
    var referencedIds = []
    var protectedReferenceIds = []
    var candidateColumns = []
    if (candidateIds.size() > 0) {
        var columnQuery = db.table('db_table_column_t').where().eq('tenant_id', tenantId)
            .in('table_id', candidateIds).eq('is_del', 0)
        if (batch) {
            var columnPage = columnQuery.page(10001, 0)
            if (columnPage.total > 10000) { exit 409, '目标表候选字段过多，请缩小物化批次' }
            candidateColumns = columnPage.list || []
        } else { candidateColumns = columnQuery.select() }
        var sourceTasks = db.table('data_access_agg_task_t').column('source_table_id').where().eq('tenant_id', tenantId)
            .in('source_table_id', candidateIds).eq('is_del', 0).groupBy('source_table_id').select()
        var targetTasks = db.table('data_access_agg_task_t').column('target_table_id').where().eq('tenant_id', tenantId)
            .in('target_table_id', candidateIds).eq('is_del', 0).groupBy('target_table_id').select()
        var candidateProps = db.table('da_prop_t').column('parent_id').where().eq('tenant_id', tenantId)
            .in('parent_id', candidateIds).eq('is_del', 0).groupBy('parent_id').select()
        for (row in sourceTasks) { protectedReferenceIds.add(text(first(row, ['sourceTableId', 'source_table_id']))) }
        for (row in targetTasks) { protectedReferenceIds.add(text(first(row, ['targetTableId', 'target_table_id']))) }
        for (row in candidateProps) { protectedReferenceIds.add(text(first(row, ['parentId', 'parent_id']))) }
        referencedIds.addAll(protectedReferenceIds)
        for (row in candidateColumns) { referencedIds.add(text(first(row, ['tableId', 'table_id']))) }
        if (batch || needsReferenceCheck) {
            var children = db.table('db_table_t').column('source_table_id').where().eq('tenant_id', tenantId)
                .in('source_table_id', candidateIds).eq('is_del', 0).groupBy('source_table_id').select()
            var catalogReferences = []
            if (batch) {
                var link = tableRead.from('da_prop_t', 'p', tenantId)
                var valueColumn = tableRead.column('p', 'prop_value', 'LOB')
                var valueText = tableRead.boundedText(valueColumn)
                link.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
                link.where(tableRead.in(tableRead.column('p', 'prop_name', 'TEXT'), ['sourceTableId', 'targetTableId']))
                link.where(tableRead.in(valueColumn, candidateIds))
                catalogReferences = link.grouped({propValue:valueText}, [valueText])
            } else {
                catalogReferences = db.table('da_prop_t').column('prop_value').where().eq('tenant_id', tenantId)
                    .in('prop_value', candidateIds).eq('is_del', 0).select()
            }
            for (row in children) { protectedReferenceIds.add(text(first(row, ['sourceTableId', 'source_table_id']))) }
            for (row in catalogReferences) { protectedReferenceIds.add(text(first(row, ['propValue', 'prop_value']))) }
            if (batch) { referencedIds.addAll(protectedReferenceIds) }
        }
    }

    var sourceCatalogIds = governanceCatalogIdsBySource[sourceTableId] || []
    var catalogPropsById = {}
    if (!blank(catalogId) && !sourceCatalogIds.contains(catalogId)) { sourceCatalogIds.add(catalogId) }
    if (!blank(catalogId) && governancePropertiesByParent[catalogId] == null) {
        governancePropertiesByParent[catalogId] = db.table('da_prop_t').where().eq('tenant_id', tenantId)
            .eq('parent_id', catalogId).eq('is_del', 0).select()
    }
    for (id in sourceCatalogIds) {
        if (id != catalogId && governanceCatalogById[id] == null) { continue }
        var properties = {}
        for (property in (governancePropertiesByParent[id] || [])) {
            var key = text(first(property, ['propName', 'prop_name']))
            if (properties[key] == null) { properties[key] = property }
        }
        catalogPropsById[id] = properties
    }
    var reservedCatalogIds = []
    for (plan in plans) {
        plan.duplicate = null
        plan.stalePlaceholderIds = []
        if (plan.matchingRows.size() > 100) { exit 409, '目标表同名登记异常，请先核对登记记录' }
        for (candidate in plan.matchingRows) {
            var id = text(candidate.tid)
            if (id == sourceTableId) { exit 409, '不能将来源表自身复用为物化目标表' }
            var ownerId = text(first(candidate, ['sourceTableId', 'source_table_id']))
            var unlinked = blank(ownerId) || ownerId == id
            if (plan.pendingCleanup && (ownerId == sourceTableId || (unlinked && !protectedReferenceIds.contains(id)))) {
                continue
            }
            if (ownerId == sourceTableId || (plan.confirmReuse && unlinked && !protectedReferenceIds.contains(id))) {
                if (batch && plan.duplicate != null) { exit 409, '目标表有多条同名登记，请先核对元数据' }
                plan.duplicate = candidate
                if (!batch) { break }
                continue
            }
            if (unlinked && !referencedIds.contains(id)) {
                plan.stalePlaceholderIds.add(id)
            } else {
                exit 400, '目标数据表「' + plan.propList.tableName + '」已存在于目标数据源「'
                    + text(first(plan.datasource, ['dbName', 'db_name'])) + '」，请修改表名后重试'
            }
        }
        // 单目标保留旧目录入口；批量按目标匹配各自目录，避免后续项覆盖前一项。
        plan.catalogId = !batch ? catalogId : ''
        for (id in sourceCatalogIds) {
            if (!blank(plan.catalogId) || catalogPropsById[id] == null || reservedCatalogIds.contains(id)) { continue }
            var properties = catalogPropsById[id]
            var registeredDb = text(first(properties.targetDbId, ['propValue', 'prop_value']))
            var registeredTable = text(first(properties.targetTableId, ['propValue', 'prop_value']))
            var registeredName = text(first(properties.targetTableName, ['propValue', 'prop_value'])).trim()
            var duplicateId = text(plan.duplicate?.tid)
            if (!batch || (registeredDb == plan.propList.dbId && (registeredName.toLowerCase() == plan.propList.tableName.toLowerCase()
                    || (!blank(duplicateId) && registeredTable == duplicateId)))) {
                plan.catalogId = id
                reservedCatalogIds.add(id)
            }
        }
    }

    // 使用当前平台库驱动的标识符引号，避免金仓和达梦将字段名解析为保留字。
    var identifierQuote = ''
    if (!dryRun && (batch || (plans[0].confirmReuse && !plans[0].validateReuseOnly))) {
        var platformConnection = platformJdbc.getDataSource().getConnection()
        try {
            identifierQuote = text(platformConnection.getMetaData().getIdentifierQuoteString()).trim()
            if (blank(identifierQuote)) { exit 503, '当前平台数据库驱动未提供标识符引用能力' }
        } finally { platformConnection.close() }
    }
    var columnWriteValues = column => {
        if (blank(identifierQuote)) { return column }
        var closeQuote = identifierQuote == '[' ? ']' : identifierQuote
        var values = {}
        for (key in column.keySet()) {
            // 租户和审计字段保留命名表拦截器使用的键，字段定义按驱动引用。
            var mapped = ['tenant_id', 'created_time', 'updated_time'].contains(key)
                ? key : identifierQuote + key + closeQuote
            var value = column[key]
            values[mapped] = value instanceof Boolean ? (value ? 1 : 0) : value
        }
        return values
    }

    var callPhysical = payload => {
        var original = body
        body = payload
        try { return createTable() } finally { body = original }
    }
    var physicalPayload = (plan, validate) => {
        return { canvas: true, ddl: plan.ddl, dbId: plan.propList.dbId, tableName: plan.propList.tableName,
            skipIfExists: true, validateOnly: validate, reuseExistingOnly: plan.confirmReuse,
            tableItems: plan.tableItems, partitionPlan: plan.partitionPlan }
    }

    // 目标登记成功后才更新搜索投影，搜索不可用时保留已提交的权威数据。
    var saveProjection = doc => {
        if (doc.assetType == 'catalog') {
            // 目录只能走单调投影；失败后不能使用整篇覆盖的通用兜底。
            try { esCommonService.mergeCatalogProjection('dataassets', doc.tid, jsons.toJSONStr(doc), doc.viewCount) }
            catch (error) { log.warn('目录计数投影待补齐: tid={}, error={}', doc.tid, error.message) }
            return
        }
        try {
            esCommonService.saveOrUpdate('dataassets', doc.tid, jsons.toJSONStr(doc), false)
        } catch (e) {
            try {
                esCommonService.save('dataassets', doc.tid, doc)
            } catch (ignored) {
                // 后续可从已提交的租户元数据补齐投影。
            }
        }
    }

    var persistTarget = (plan, tableItems, physicalTableReused) => {
        var propList = plan.propList
        var targetDatasource = plan.datasource
        var submittedPartitionPlan = plan.partitionPlan
        var duplicate = plan.duplicate
        var reuseTargetTable = duplicate != null
        var confirmReuseExisting = plan.confirmReuse
        var catalogId = plan.catalogId
        var stalePlaceholderIds = plan.stalePlaceholderIds
        var now = new Date()
        // Hive 重名或物理建表失败之前不得清理任何占位登记。
        if (stalePlaceholderIds.size() > 0) {
            db.table('db_table_t').where().eq('tenant_id', tenantId).in('tid', stalePlaceholderIds)
                .eq('is_del', 0).update({ is_del: 1, updated_time: now })
        }

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
        if (physicalTableReused && duplicate != null) {
            // 复用保留已有统计，不能把有数据的目标表重新标成空表。
            for (key in ['record_count','storage_size','total_size_bytes','total_size_formatted']) { targetTableValues.remove(key) }
        }
        if (reuseTargetTable) {
            var affected = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', targetTableId)
                .eq('is_del', 0).update(targetTableValues)
            requireMetadataUpdate(affected)
            if (!confirmReuseExisting) { db.table('db_table_column_t').where().eq('tenant_id', tenantId).eq('table_id', targetTableId).update({ is_del: 1, updated_time: now }) }
        } else {
            targetTableValues.tid = targetTableId
            targetTableValues.reg_time = now
            targetTableValues.created_time = now
            db.table('db_table_t').primary('tid').insert(targetTableValues)
        }

        var columns = []
        var existingColumnsByName = {}
        if (reuseTargetTable && confirmReuseExisting) {
            for (column in candidateColumns) {
                if (text(first(column, ['tableId','table_id'])) == targetTableId) { existingColumnsByName[text(column.columnName).toLowerCase()] = column }
            }
        }
        // sourceColumns 已在授权后一次读取，字段保存不再次查询来源。
        for (index, item in tableItems) {
            var sourceColumn = sourceColumnMap[text(item.columnName).toLowerCase()]
            if (sourceColumn == null && !blank(item.dictionarySourceField)) {
                sourceColumn = sourceColumnMap[text(item.dictionarySourceField).toLowerCase()]
            }
            columns.add({
                tid: existingColumnsByName[text(item.columnName).toLowerCase()]?.tid || IdUtil.getSnowflakeNextIdStr(),
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
                created_time: existingColumnsByName[text(item.columnName).toLowerCase()]?.createdTime || now,
                updated_time: now
            })
        }
        var columnsToInsert = []
        var actualColumnNames = []
        for (column in columns) { actualColumnNames.add(text(column.column_name).toLowerCase()) }
        var staleColumnIds = []
        for (column in existingColumnsByName.values()) {
            if (!actualColumnNames.contains(text(column.columnName).toLowerCase())) { staleColumnIds.add(text(column.tid)) }
        }
        if (staleColumnIds.size() > 0) {
            db.table('db_table_column_t').where().eq('tenant_id', tenantId).in('tid', staleColumnIds).update({is_del:1,updated_time:now})
        }
        for (column in columns) {
            if (existingColumnsByName[text(column.column_name).toLowerCase()] != null) {
                // 保留原字段 ID，已有画布/治理绑定不会因补登记而失效。
                var affected = db.table('db_table_column_t').where().eq('tenant_id', tenantId).eq('tid', column.tid)
                    .eq('table_id', targetTableId).eq('is_del', 0).update(columnWriteValues(column))
                requireMetadataUpdate(affected)
            } else { columnsToInsert.add(column) }
        }
        if (columnsToInsert.size() > 0) {
            if (batch) {
                // 命名表批量写入使用当前 Magic 事务；单目标兼容旧驱动的字段保存路径。
                db.table('db_table_column_t').batchInsert(columnsToInsert.map(columnWriteValues))
            } else {
                try {
                    metadataAsset.batchInsertTableColumns(columnsToInsert)
                } catch (e) {
                    for (column in columnsToInsert) { db.table('db_table_column_t').primary('tid').insert(column) }
                }
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
            partitionPlan: submittedPartitionPlan,
            fieldTransformBindings: buildFieldTransformBindings(sourceTable, sourceColumnMap, relations, targetTableId, tableItems, columns)
        }
        db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', targetTableId).eq('is_del', 0)
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
            recordCount: physicalTableReused && duplicate != null ? first(duplicate,['recordCount','record_count']) : 0,
            fieldCount: tableItems.size(),
            storageSize: physicalTableReused && duplicate != null ? first(duplicate,['storageSize','storage_size']) : '0 KB',
            totalSizeBytes: physicalTableReused && duplicate != null ? first(duplicate,['totalSizeBytes','total_size_bytes']) : 0,
            totalSizeFormatted: physicalTableReused && duplicate != null ? first(duplicate,['totalSizeFormatted','total_size_formatted']) : '0 KB',
            assetStatus: 2,
            flowStatus: 2,
            regTime: date_format(now),
            createdTime: date_format(now),
            updatedTime: date_format(now),
            regTime_date: date_format(now),
            createdTime_date: date_format(now),
            updatedTime_date: date_format(now)
        }
        // 如果来源表已有自动目录则复用，否则新建。目录代表目标表，但保留来源表属性形成完整链路。
        if (blank(catalogId)) {
            catalogId = IdUtil.getSnowflakeNextIdStr()
            db.table('da_catalog_t').primary('tid').insert({
                tid: catalogId,
                tenant_id: tenantId,
                is_del: 0,
                reg_time: now,
                asset_status: 2,
                flow_status: 2,
                created_time: now,
                updated_time: now
            })
        } else {
            var affected = db.table('da_catalog_t').where()
                .eq('tenant_id', tenantId)
                .eq('tid', catalogId)
                .eq('is_del', 0)
                .update({ updated_time: now, asset_status: 2, flow_status: 2, is_del: 0 })
            requireMetadataUpdate(affected)
        }

        var existingProps = catalogPropsById[catalogId] || {}
        var catalogName = tableNameCn
        var sourceTableName = first(sourceTable, ['tableName', 'table_name', 'tableNameEn', 'table_name_en'])
        upsertCatalogProp(catalogId, existingProps, 'catalogName', catalogName, now)
        upsertCatalogProp(catalogId, existingProps, 'catalogNameEn', propList.tableName, now)
        upsertCatalogProp(catalogId, existingProps, 'assetDesc', catalogName, now)
        upsertCatalogProp(catalogId, existingProps, 'dataSourceType', 'ods', now)
        upsertCatalogProp(catalogId, existingProps, 'businessType', businessType, now)
        upsertCatalogProp(catalogId, existingProps, 'sourceDbId', sourceTable.datasourceId, now)
        upsertCatalogProp(catalogId, existingProps, 'sourceDbName', sourceDatasource == null ? '' : sourceDatasource.dbName, now)
        upsertCatalogProp(catalogId, existingProps, 'sourceTableId', sourceTableId, now)
        upsertCatalogProp(catalogId, existingProps, 'sourceTableName', sourceTableName, now)
        upsertCatalogProp(catalogId, existingProps, 'targetDbId', propList.dbId, now)
        upsertCatalogProp(catalogId, existingProps, 'targetDbType', targetDatasource.dbType || 'mysql', now)
        upsertCatalogProp(catalogId, existingProps, 'targetTableId', targetTableId, now)
        upsertCatalogProp(catalogId, existingProps, 'targetTableName', propList.tableName, now)
        upsertCatalogProp(catalogId, existingProps, 'dbId', propList.dbId, now)
        upsertCatalogProp(catalogId, existingProps, 'orgId', sourceTable.orgId || targetDatasource.orgId, now)
        upsertCatalogProp(catalogId, existingProps, 'manageUnit', sourceTable.orgId || targetDatasource.orgId, now)

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
        // 复用治理目录的批量预取结果；新建目录使用数据库默认计数0。
        var previousCatalog = governanceCatalogById[catalogId]
        catalogDoc.viewCount = previousCatalog == null ? 0L : first(previousCatalog, ['marketViewCount', 'market_view_count'])::long(0)
        // 反写来源表与目标表的目录关联，后续列表和任务创建都能直接定位。
        var sourceUpdated = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', sourceTableId).eq('is_del', 0)
            .update({ source_catalog_id: catalogId, catalog_name: catalogName, updated_time: now })
        var targetUpdated = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', targetTableId).eq('is_del', 0)
            .update({ source_catalog_id: catalogId, catalog_name: catalogName, updated_time: now })
        requireMetadataUpdate(sourceUpdated)
        requireMetadataUpdate(targetUpdated)

        return {targetDoc:targetDoc, catalogDoc:catalogDoc, result:{
            tid: targetTableId,
            targetTableId: targetTableId,
            catalogId: catalogId,
            sourceTableId: sourceTableId,
            physicalTableReused: physicalTableReused
        }}
    }

    var applyTarget = plan => {
        var propList = plan.propList
        var tableItems = plan.tableItems
        var submittedPartitionPlan = plan.partitionPlan
        var duplicate = plan.duplicate
        var reuseTargetTable = duplicate != null
        var confirmReuseExisting = plan.confirmReuse
        var physicalTableReused = reuseTargetTable
        if (batch || !reuseTargetTable || plan.hive || confirmReuseExisting || plan.recreateMissing || dryRun) {
            if (blank(plan.ddl)) {
                // 旧单目标调用可以省略SQL，继续使用现有方言生成器。
                var saved = body
                body = {canvas:true, propList:propList, tableItems:tableItems, partitionPlan:submittedPartitionPlan}
                try { plan.ddl = getCreateTableDDL() } finally { body = saved }
            }
            var physical = plan.preparedReuse != null ? plan.preparedReuse : callPhysical(physicalPayload(plan, dryRun))
            if (physical?.status == 'failed') {
                return {errorCode: physical.code || 500, message: physical.message, sideEffectsPossible: true}
            }
            if (dryRun) {
                if (physical?.valid != true) { return {errorCode:400, message:'物化建表预检未完成'} }
                return {valid:true, dryRun:true, sideEffectsApplied:false, dictionaryValidationPassed:true,
                    physicalTableChecked:false, sourceTableId:sourceTableId, tableItems:tableItems}
            }
            if (physical != null && physical.created == false && !confirmReuseExisting) {
                var message = plan.hive
                    ? 'Hive 目标物理表已存在，请修改目标表名，或联系运维手动删除原表后重新物化；系统不会自动删除 Hive 表'
                    : '目标物理表已存在，请先删除目标表或改用其他目标表名后再创建任务'
                return {errorCode:409, message:message, sideEffectsPossible:false}
            }
            if (confirmReuseExisting) {
                if (physical?.physicalTableReused != true || physical?.tableItems == null || physical.tableItems.size() == 0) {
                    return {errorCode:409, message:'已有目标表复用校验未完成，未保存目标登记'}
                }
                tableItems = physical.tableItems
            } else if (batch && physical?.created != true) {
                return {errorCode:500, message:'目标数据库未确认物理建表结果', sideEffectsPossible:true}
            }
            physicalTableReused = physical != null && physical.created == false
        }
        if (plan.validateReuseOnly && confirmReuseExisting) {
            return {valid:true, dryRun:true, sideEffectsApplied:false, physicalTableReused:physicalTableReused,
                targetTableId:duplicate == null ? '' : text(duplicate.tid), sourceTableId:sourceTableId, tableItems:tableItems}
        }
        // 物理建表完成后，在同一个平台库事务中保存目标表、字段和目录关联。
        var saved = batch ? db.transaction(() => persistTarget(plan, tableItems, physicalTableReused))
            : persistTarget(plan, tableItems, physicalTableReused)
        saveProjection(saved.targetDoc)
        saveProjection(saved.catalogDoc)
        return saved.result
    }

    var executeTargets = () => {
        // 全批先校验已授权目标及请求完整性。正式复用还需在任何 DDL 前验证目标真实结构。
        for (plan in plans) {
            if (!blank(plan.ddl)) {
                var checked = callPhysical(physicalPayload(plan, true))
                if (batch && checked?.valid != true) { exit 400, '物化目标的建表请求预检未完成' }
                if (checked?.partitionPlan != null) { plan.partitionPlan = checked.partitionPlan }
            }
            if (batch && !dryRun && plan.confirmReuse) {
                plan.preparedReuse = callPhysical(physicalPayload(plan, false))
                if (plan.preparedReuse?.physicalTableReused != true || plan.preparedReuse?.tableItems == null
                        || plan.preparedReuse.tableItems.size() == 0) {
                    exit 409, '已有目标表复用校验未完成'
                }
            }
        }
        if (!batch) {
            var result = applyTarget(plans[0])
            if (result.errorCode != null) { exit result.errorCode, result.message }
            if (result.sideEffectsApplied != false) { metadataAsset.refreshRegistrationStatus(plans[0].propList.dbId, false) }
            return result
        }
        if (dryRun) {
            var validated = plans.map(plan => {return {dbId:plan.propList.dbId, tableName:plan.propList.tableName,
                status:'succeeded', valid:true, syntaxChecked:false, sideEffectsApplied:false}})
            return {batch:true, complete:true, sideEffectsApplied:false, sourceTableId:sourceTableId, results:validated}
        }

        var executionOrder = []
        var orderedOds = plans.filter(plan => odsRankByDb[plan.propList.dbId] != null).sort(
            (left, right) => odsRankByDb[left.propList.dbId] - odsRankByDb[right.propList.dbId])
        var odsIndex = 0
        for (plan in plans) {
            if (odsRankByDb[plan.propList.dbId] == null) { executionOrder.add(plan) }
            else { executionOrder.add(orderedOds[odsIndex]); odsIndex++ }
        }
        var resultByIndex = {}
        var touchedDbIds = []
        var stopped = false
        for (plan in executionOrder) {
            var output = {dbId:plan.propList.dbId, tableName:plan.propList.tableName, status:'skipped'}
            resultByIndex[plan.inputIndex] = output
            if (stopped) { continue }
            try {
                var result = applyTarget(plan)
                if (result.errorCode != null) {
                    output.status = 'failed'
                    output.code = result.errorCode
                    output.message = result.message
                    output.sideEffectsPossible = result.sideEffectsPossible == true
                    stopped = true
                } else {
                    output.putAll(result)
                    output.status = 'succeeded'
                    touchedDbIds.add(plan.propList.dbId)
                }
            } catch (error) {
                output.status = 'failed'
                output.code = 500
                output.message = ErrorDetailSanitizer.errorText(error.getMessage()) || '目标物化失败，请重新预检后重试'
                output.sideEffectsPossible = true
                stopped = true
            }
        }
        var registrationStatusRefreshed = true
        if (touchedDbIds.size() > 0) {
            try { metadataAsset.refreshRegistrationStatuses(touchedDbIds) }
            catch (error) { registrationStatusRefreshed = false }
        }
        return {batch:true, complete:!stopped, results:plans.map(plan => resultByIndex[plan.inputIndex]),
            sourceTableId:sourceTableId, registrationStatusRefreshed:registrationStatusRefreshed}
    }

    if (!batch) { return executeTargets() }

    // Servlet 属性只在当前内部调用链可写；直接请求的请求体不能替换目标连接。
    var http = request.get()
    var attribute = 'ods.materialization.targets'
    var previousContext = http.getAttribute(attribute)
    var authorizedConnections = connectionProperties.resolveBatch(tenantId, targetDbIds, 'MENU:2081000000000000002')
    try {
        http.setAttribute(attribute, {tenantId:tenantId, connections:authorizedConnections, prepared:{}})
        return executeTargets()
    } finally {
        http.setAttribute(attribute, previousContext)
    }
}

// 相同租户的物化提交共用一个看门狗锁，覆盖复用目标的查重和登记写入；只读预检可并行。
if (dryRun || (!batch && plans[0].validateReuseOnly && plans[0].confirmReuse)) { return runMaterialization() }
var lock = null
var acquired = false
try {
    lock = redissonClient.getLock('data-elements:ods:materialization:' + tenantId)
    acquired = lock.tryLock()
} catch (error) {
    exit 503, '物化提交暂时无法获取操作锁，请稍后重试'
}
if (!acquired) { exit 409, '当前租户已有物化申请正在提交，请稍后重试' }
try { return runMaterialization() }
finally {
    try { lock.unlock() }
    catch (error) { log.warn('物化提交操作锁释放失败') }
}
