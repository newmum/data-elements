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
import '@/common/registeredTableCatalogProjection' as registeredTableCatalogProjection
import java.util.Map
import java.util.List
import log
import cn.hutool.core.util.IdUtil
import metadataAsset
import tenantRuntime
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
        .eq('table_id', tableId).eq('is_del', 0).select()
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
// 解析请求体并为可选参数设置默认值。
var tableId = body == null ? null : (body.tableId == null ? body.tid : body.tableId)
if (blank(tableId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tableId 不能为空'
}
// 从 MySQL 业务表读取当前有效数据。
var table = db.table('db_table_t').where().eq('tid', tableId).eq('is_del', 0).selectOne()
if (table == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '数据表不存在或已删除'
}
var props = body.propList == null ? {} : body.propList
var governance = body.fieldGovernanceConfig == null ? props.fieldGovernanceConfig : body.fieldGovernanceConfig
var dictionaryCategories = props.dictionaryCategories
var dictionaryProfiles = props.dictionaryProfiles
var dictionaryProfile = props.dictionaryProfile
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
for (item in items == null ? [] : items) {
    var columnId = itemValue(item, ['sourceTableColumnId', 'tableColumnId', 'tid', 'id'])
    var column = null
    if (!blank(columnId)) {
        // 从 MySQL 业务表读取当前有效数据。
        column = db.table('db_table_column_t').where()
            .eq('tid', columnId).eq('table_id', tableId).eq('is_del', 0).selectOne()
    }
    if (column == null && !blank(itemValue(item, ['columnName', 'colEn']))) {
        // 从 MySQL 业务表读取当前有效数据。
        column = db.table('db_table_column_t').where()
            .eq('table_id', tableId)
            .eq('column_name', itemValue(item, ['columnName', 'colEn']))
            .eq('is_del', 0)
            .selectOne()
    }
    if (column == null) {
        continue
    }
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
