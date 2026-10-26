{
  "properties": {},
  "id": "datapd_db_table_govern_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "05.保存数据表治理配置",
  "createTime": 1784454007655,
  "updateTime": 1787155350373,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/governance/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"00e4a8e8cc254c708925a853200bf5b9\",\n    \"propList\": {\n    },\n    \"columns\": [\n    ],\n    \"tid\": \"00e4a8e8cc254c708925a853200bf5b9\",\n    \"fieldGovernanceConfig\": \"测试\",\n    \"catalogItems\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 05：保存登记步骤 3/4 的表治理配置；使用请求参数 tableId、tid、propList、fieldGovernanceConfig、dryRun、columns、catalogItems、fields；涉及 db_table_column_t、db_table_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.保存数据表治理配置
// 路由：POST /dst/database/table/governance/save
// 业务说明：数据表管理 05：保存登记步骤 3/4 的表治理配置；使用请求参数 tableId、tid、propList、fieldGovernanceConfig、dryRun、columns、catalogItems、fields；涉及 db_table_column_t、db_table_t 等业务表。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import cn.hutool.json.JSONUtil
import '@/common/registeredTableCatalogProjection' as registeredTableCatalogProjection
import java.util.Map
import java.util.List
import log
import cn.hutool.core.util.IdUtil
import metadataAsset
import tenantRuntime
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService
import tableSchemaSync
import '@/common/datasourceConnectionConfig' as datasourceConnectionConfig
import cn.dev33.satoken.stp.StpUtil
var tenantId = tenantRuntime.id()

/*
 * 数据表管理 05：保存登记步骤 3/4 的表治理配置。
 *
 * 表级配置写 db_table_t 物理字段，字段治理配置写 db_table_column_t。
 * 不写 da_prop_t，不创建 asset_type=catalog 的资产、目录关系或审批关系。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var nowTime = () => new Date()
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var valueText = (value) => {
    if (value == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    // 返回当前业务分支的标准结果。
    return value instanceof Map || value instanceof List ? jsons.toJSONStr(value) : textOf(value)
}
var itemValue = (item, keys) => {
    for (key in keys) {
        if (item != null && item[key] != null) {
            // 返回当前业务分支的标准结果。
            return item[key]
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var newId = () => IdUtil.getSnowflakeNextIdStr()
var toInt = (value, fallback) => blank(value) ? fallback : value::int
// 第四步保存的字段配置是登记后的字段主数据。探查尚未创建字段读模型时也必须在这里落库，
// 让物化建表直接读取登记字段，而不是依赖后续 refreshTableInfo 补数据。
var saveColumns = (tableId, items) => {
    if (items == null || items.size() == 0) {
        return 0
    }
    var existingRows = db.table('db_table_column_t').where()
        .eq('tenant_id', tenantId).eq('table_id', tableId).eq('is_del', 0).select()
    var existingByName = {}
    for (existing in (existingRows == null ? [] : existingRows)) {
        var existingName = textOf(rowValue(existing, ['columnName', 'column_name']))
        if (!blank(existingName)) {
            existingByName[existingName.trim().toLowerCase()] = existing
        }
    }
    var newRows = []
    var ordinal = 0
    for (item in items) {
        ordinal = ordinal + 1
        var columnName = itemValue(item, ['columnName', 'colEn', 'fieldName'])
        if (blank(columnName)) {
            continue
        }
        var key = textOf(columnName).trim().toLowerCase()
        var existing = existingByName[key]
        var columnId = existing == null ? newId() : rowValue(existing, ['tid'])
        var values = {
            tid: columnId,
            table_id: tableId,
            column_name: columnName,
            column_comment: itemValue(item, ['columnComment', 'colName']) || columnName,
            data_type: itemValue(item, ['dataType', 'columnType']),
            column_type: itemValue(item, ['columnType', 'dataType']),
            length: toInt(itemValue(item, ['length', 'columnLength']), 0),
            precision_length: toInt(itemValue(item, ['precisionLength', 'precision', 'colPrecision']), 0),
            scale: toInt(itemValue(item, ['scale']), 0),
            nullable: toInt(itemValue(item, ['nullable']), 1),
            default_value: itemValue(item, ['defaultValue']),
            primary_key: toInt(itemValue(item, ['primaryKey']), 0),
            auto_increment: toInt(itemValue(item, ['autoIncrement']), 0),
            is_unique: toInt(itemValue(item, ['isUnique', 'unique']), 0),
            indexed: toInt(itemValue(item, ['indexed']), 0),
            ordinal_position: toInt(itemValue(item, ['ordinalPosition']), ordinal),
            charset: itemValue(item, ['charset']),
            collation: itemValue(item, ['collation']),
            extra: itemValue(item, ['extra']),
            source_table_column_id: itemValue(item, ['sourceTableColumnId']) || columnId,
            is_del: 0,
            updated_time: nowTime()
        }
        if (existing == null) {
            values.created_time = nowTime()
            newRows.add(values)
        } else {
            db.table('db_table_column_t').primary('tid').update(values, true)
        }
    }
    if (newRows.size() > 0) {
        metadataAsset.batchInsertTableColumns(newRows)
    }
    return newRows.size()
}
// 一次读取本表已保存字段；逐项按 ID/物理名称解析，不逐字段查询，也不接受其它表或租户的 ID。
var registeredColumnItems = (tableId, items) => {
    var rows = db.table('db_table_column_t').where()
        .eq('tenant_id', tenantId).eq('table_id', tableId).eq('is_del', 0).select()
    var byId = {}
    var byName = {}
    for (row in rows) {
        byId[textOf(rowValue(row, ['tid']))] = row
        var name = textOf(rowValue(row, ['columnName', 'column_name']))
        if (!blank(name)) { byName[name.trim().toLowerCase()] = row }
    }
    var result = []
    for (item in items == null ? [] : items) {
        var id = textOf(itemValue(item, ['sourceTableColumnId', 'tableColumnId', 'tid', 'id']))
        var column = blank(id) ? null : byId[id]
        var name = textOf(itemValue(item, ['columnName', 'colEn']))
        if (column == null && !blank(name)) { column = byName[name.trim().toLowerCase()] }
        if (column != null) { result.add({ item: item, column: column }) }
    }
    return result
}
// 详情页只允许编辑当前表已存在的字段，校验全部 ID 后才开始写入。
var validateDetailColumns = (items, registeredItems) => {
    if (items == null || items.size() == 0 || items.size() > 2000) { exit 400, '字段数量须为 1 至 2000' }
    if (registeredItems.size() != items.size()) { exit 403, '包含不属于当前表的字段' }
    var seen = {}
    for (registered in registeredItems) {
        var item = registered.item
        var column = registered.column
        var id = textOf(itemValue(item, ['tid', 'tableColumnId', 'id', 'sourceTableColumnId']))
        var columnId = textOf(rowValue(column, ['tid']))
        var name = textOf(itemValue(item, ['columnName', 'colEn']))
        var columnName = textOf(rowValue(column, ['columnName', 'column_name']))
        if ((!blank(id) && id != columnId) || (!blank(name) && name.trim().toLowerCase() != columnName.trim().toLowerCase()) || seen[columnId] == true) {
            exit 403, '字段 ID、名称或归属不匹配'
        }
        seen[columnId] = true
    }
    return registeredItems
}
// 解析请求体并为可选参数设置默认值。
var tableId = body == null ? null : (body.tableId == null ? body.tid : body.tableId)
if (blank(tableId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tableId 不能为空'
}
// 从 MySQL 业务表读取当前有效数据。
var table = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', tableId).eq('is_del', 0).selectOne()
if (table == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '数据表不存在或已删除'
}
var sourceId = rowValue(table, ['datasourceId', 'datasource_id'])
var source = db.table('db_datasource_t').where().eq('tenant_id', tenantId).eq('tid', sourceId).eq('is_del', 0).selectOne()
if (source == null) {
    exit 403, '数据表关联的数据源不存在或已删除'
}
// 结构同步只接受当前租户已保存的字段快照；客户端不能提交连接、字段定义或执行 SQL。
if (!blank(body.schemaAction)) {
    if (body.schemaAction != 'compare' && body.schemaAction != 'apply') { exit 400, '无效的结构同步操作' }
    var snapshotPage = db.table('db_table_column_t').where()
        .eq('tenant_id', tenantId).eq('table_id', tableId).eq('is_del', 0)
        .orderBy('ordinal_position', 'asc').orderBy('tid').page(2001, 0)
    var snapshot = snapshotPage.list
    if (snapshot == null || snapshot.size() == 0 || snapshot.size() > 2000) { exit 400, '字段快照须包含 1 至 2000 个字段' }
    // 历史字段行的主键标记可能为空；与详情页保持一致，以已保存治理快照中的可编辑属性为准。
    var savedConfig = rowValue(table, ['fieldGovernanceConfig', 'field_governance_config'])
    savedConfig = savedConfig instanceof Map ? savedConfig : (blank(savedConfig) ? {} : JSONUtil.parseObj(textOf(savedConfig)))
    var savedByName = {}
    for (item in savedConfig.fields == null ? [] : savedConfig.fields) {
        var name = textOf(itemValue(item, ['columnName', 'colEn']))
        if (!blank(name)) { savedByName[name.trim().toLowerCase()] = item }
    }
    for (field in snapshot) {
        var savedField = savedByName[textOf(rowValue(field, ['columnName', 'column_name'])).trim().toLowerCase()]
        if (savedField != null) {
            if (savedField.containsKey('primaryKey')) { field.primaryKey = savedField.primaryKey }
            if (savedField.containsKey('columnComment')) { field.columnComment = savedField.columnComment }
        }
    }
    var connection = datasourceConnectionConfig('read', sourceId, null, false)
    var physicalName = textOf(rowValue(table, ['tableName', 'table_name']))
    var binding = tenantId + ':' + StpUtil.getLoginIdAsString() + ':' + tableId + ':' + jsons.toJSONStr(snapshot)
    if (body.schemaAction == 'compare' || body?.dryRun == true) {
        return tableSchemaSync.preview(connection, physicalName, snapshot, binding)
    }
    return tableSchemaSync.apply(connection, physicalName, snapshot, binding,
        textOf(body.fingerprint), textOf(body.confirmation), body.acknowledged == true)
}
var props = body.propList == null ? {} : body.propList
var governance = body.fieldGovernanceConfig == null ? props.fieldGovernanceConfig : body.fieldGovernanceConfig
// 文件定位由登记采集保存；治理编辑保留它，不能由客户端更换文件或带入连接凭据。
governance = StructuredSourceProbeService.preserveFtpFileSnapshot(
    rowValue(table, ['fieldGovernanceConfig', 'field_governance_config']), governance)
// 附件限制由第一步模板登记维护，后续治理保存不能丢失或静默修改。
var previousGovernance = rowValue(table, ['fieldGovernanceConfig', 'field_governance_config'])
previousGovernance = previousGovernance instanceof Map ? previousGovernance : (blank(previousGovernance) ? {} : JSONUtil.parseObj(textOf(previousGovernance)))
if (previousGovernance.containsKey('attachmentFields') || (!blank(governance) && textOf(governance).contains('attachmentFields'))) {
    governance = governance instanceof Map ? governance : (blank(governance) ? {} : JSONUtil.parseObj(textOf(governance)))
    governance.remove('attachmentFields')
    if (previousGovernance.containsKey('attachmentFields')) { governance.attachmentFields = previousGovernance.attachmentFields }
}
var dictionaryCategories = props.dictionaryCategories
var dictionaryProfiles = props.dictionaryProfiles
var dictionaryProfile = props.dictionaryProfile
if (body.editMode == 'fields') {
    var registeredItems = validateDetailColumns(body.columns, registeredColumnItems(tableId, body.columns))
    governance = governance instanceof Map ? governance : (blank(governance) ? {} : JSONUtil.parseObj(textOf(governance)))
    // 登记完成标记属于原流程，详情编辑不能把未登记表变成已登记表。
    for (key in ['registrationSavedPhase', 'registrationSavedAt', 'tableName', 'tableComment', 'businessType']) {
        governance.remove(key)
        if (previousGovernance.containsKey(key)) { governance[key] = previousGovernance[key] }
    }
    var result = {
        tid: tableId, tableId: tableId,
        businessType: rowValue(table, ['businessType', 'business_type']),
        businessTypeReason: rowValue(table, ['businessTypeReason', 'business_type_reason']),
        annotated: rowValue(table, ['annotated']),
        fieldGovernanceConfig: governance,
        saved: body?.dryRun != true, dryRun: body?.dryRun == true
    }
    if (body?.dryRun == true) { return result }
    db.transaction(() => {
        for (registered in registeredItems) {
            var item = registered.item
            // 物理类型、可空性和连接信息保持已采集快照；只更新登记页面可编辑的属性。
            db.table('db_table_column_t').primary('tid').update({
                tid: rowValue(registered.column, ['tid']),
                column_comment: itemValue(item, ['columnComment', 'colName']),
                primary_key: item.primaryKey == true || textOf(item.primaryKey) == '1' ? 1 : 0,
                data_standard_id: item.dataStandardId,
                enable_code_table: item.enableCodeTable,
                code_table_id: item.codeTableId,
                updated_time: nowTime()
            }, true)
        }
        db.table('db_table_t').primary('tid').update({
            tid: tableId,
            timestamp_field: governance.timestampField,
            dictionary_structure_type: governance.dictionaryStructureType,
            dictionary_categories: valueText(governance.dictionaryCategories),
            dictionary_profiles: valueText(governance.dictionaryProfiles),
            dictionary_profile: valueText(governance.dictionaryProfile),
            field_governance_config: valueText(governance),
            updated_time: nowTime()
        }, true)
    })
    return result
}
// “暂不处理”是已记录但尚未标注的特殊状态。历史数据中的“不确定”一并按该语义处理，
// 不能在保存治理配置时又被强制写为已标注，否则返回第二步会进入右侧已标注列表。
var businessType = blank(props.businessType)
    ? textOf(rowValue(table, ['businessType', 'business_type']))
    : textOf(props.businessType).trim()
var pendingBusinessType = businessType == '暂不处理' || businessType == '不确定'
var annotated = pendingBusinessType ? 0 : 1
if (body?.dryRun == true) {
    return { tid: tableId, tableId: tableId, assetStatus: 2, flowStatus: 2, annotated: annotated, dryRun: true }
}
// 将校验后的业务数据写入 MySQL 主表。
db.table('db_table_t').primary('tid').update({
    tid: tableId,
    table_name_cn: props.tableNameCn,
    table_name_en: props.tableName,
    table_comment: props.tableComment == null ? props.tableNameCn : props.tableComment,
    business_type: businessType,
    business_type_reason: props.businessTypeReason,
    timestamp_field: props.timestampField,
    dictionary_structure_type: props.dictionaryStructureType,
    dictionary_table_flag: props.dictionaryTableFlag,
    field_governance_config: valueText(governance),
    dictionary_categories: valueText(dictionaryCategories),
    dictionary_profiles: valueText(dictionaryProfiles),
    dictionary_profile: valueText(dictionaryProfile),
    catalog_name: props.catalogName == null ? props.tableNameCn : props.catalogName,
    catalog_name_en: props.catalogNameEn == null ? props.tableName : props.catalogNameEn,
    source_table_id: tableId,
    source_table_name: props.tableName,
    manage_unit: props.manageUnit == null ? props.orgId : props.manageUnit,
    asset_desc: props.assetDesc,
    annotated: annotated,
    asset_status: 2,
    flow_status: 2,
    flow_order_id: null,
    updated_time: nowTime(),
    is_del: 0
}, true)
var items = body.columns
if (items == null) {
    items = body.catalogItems
}
if (items == null) {
    items = body.fields
}
if (items == null && governance instanceof Map) {
    items = governance.get('fields')
}
saveColumns(tableId, items)
var registeredItems = registeredColumnItems(tableId, items)
for (registeredItem in registeredItems) {
    var item = registeredItem.item
    var column = registeredItem.column
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('db_table_column_t').primary('tid').update({
        tid: rowValue(column, ['tid']),
        catalog_item_name: itemValue(item, ['catalogItemName', 'colName']),
        catalog_item_en: itemValue(item, ['catalogItemEn', 'colEn']),
        catalog_item_type: itemValue(item, ['catalogItemType', 'colType']),
        is_masking: itemValue(item, ['isMasking']),
        is_fk: itemValue(item, ['isFk']),
        date_format: itemValue(item, ['dateFormat']),
        mark_lvl: itemValue(item, ['markLvl']),
        col_unit: itemValue(item, ['colUnit']),
        col_precision: itemValue(item, ['colPrecision']),
        masking_id: itemValue(item, ['maskingId']),
        share_type: itemValue(item, ['shareType']),
        share_condition: itemValue(item, ['shareCondition']),
        is_dict: itemValue(item, ['isDict']),
        dict_name: itemValue(item, ['dictName']),
        col_max_length: itemValue(item, ['colMaxLength']),
        data_standard_id: itemValue(item, ['dataStandardId']),
        quality_rule: itemValue(item, ['qualityRule']),
        enable_code_table: itemValue(item, ['enableCodeTable']),
        code_table_id: itemValue(item, ['codeTableId']),
        source_table_column_id: rowValue(column, ['tid']),
        updated_time: nowTime()
    }, true)
}

// 目录是“已标注”登记表的派生视图。暂不处理表只保存在物理表登记状态中，
// 不建立目录投影，确保第二步回读时始终进入左侧未标注列表。
var catalogProjection = pendingBusinessType
    ? { catalogId: null, indexed: false }
    : registeredTableCatalogProjection('ensure', tableId, true, null)

var doc = {
    tid: tableId,
    id: tableId,
    assetType: 'table',
    dbId: rowValue(table, ['datasourceId', 'datasource_id']),
    datasourceId: rowValue(table, ['datasourceId', 'datasource_id']),
    tableName: rowValue(table, ['tableName', 'table_name']),
    tableNameEn: rowValue(table, ['tableName', 'table_name']),
    tableNameCn: props.tableNameCn,
    businessType: businessType,
    businessTypeReason: props.businessTypeReason,
    annotated: annotated,
    timestampField: props.timestampField,
    dictionaryStructureType: props.dictionaryStructureType,
    dictionaryTableFlag: props.dictionaryTableFlag,
    assetStatus: 2,
    flowStatus: 2,
    updatedTime: date_format(nowTime())
}
var excludedIndexProps = {
    fieldGovernanceConfig: true,
    dictionaryProfiles: true,
    dictionaryProfile: true,
    dictionaryCategories: true,
    columns: true
}
for (key in props.keySet()) {
    if (excludedIndexProps[key] == null) {
        doc[key] = props[key]
    }
}
try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(doc), false)
} catch (e) {
    log.warn('同步数据表治理配置到 ES 失败, tid: {}, error: {}', tableId, e.message)
}
metadataAsset.refreshRegistrationStatus(rowValue(table, ['datasourceId', 'datasource_id']), false)
// 返回当前业务分支的标准结果。
return {
    tid: tableId,
    tableId: tableId,
    assetType: 'table',
    catalogId: catalogProjection.catalogId,
    catalogIndexed: catalogProjection.indexed,
    businessType: businessType,
    businessTypeReason: props.businessTypeReason,
    annotated: annotated,
    saved: true
}
