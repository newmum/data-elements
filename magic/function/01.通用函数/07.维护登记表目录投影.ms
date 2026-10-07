{
  "properties": {},
  "id": "datapd_registered_table_catalog_projection_01",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "07.维护登记表目录投影",
  "createTime": 1787155322067,
  "updateTime": 1787617853769,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/registeredTableCatalogProjection",
  "description": "action=ensure 时维护指定登记表的目录投影；action=syncCompleted 时补偿当前租户已完成登记的表。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "action",
      "value": null,
      "description": "要执行的业务操作",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tableId",
      "value": null,
      "description": "数据表 ID；syncCompleted 时为空",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "synchronizeIndex",
      "value": null,
      "description": "ensure 时是否同步 ES 索引",
      "required": false,
      "dataType": "Boolean",
      "type": "java.lang.Boolean",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "maxRows",
      "value": null,
      "description": "syncCompleted 最大处理数，最大 10000",
      "required": false,
      "dataType": "Integer",
      "type": "java.lang.Integer",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
================================
// 共享函数：07.维护登记表目录投影
// 函数路径：/common/registeredTableCatalogProjection
// 业务说明：action=ensure 时维护指定登记表的目录投影；action=syncCompleted 时补偿当前租户已完成登记的表。
// 函数输入：action：要执行的业务操作；tableId：数据表 ID；syncCompleted 时为空；synchronizeIndex：ensure 时是否同步 ES 索引；maxRows：syncCompleted 最大处理数，最大 10000。
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import java.util.List
import java.util.Map
import log

/*
 * 已登记数据表 -> 数据目录投影。
 *
 * 这是一份业务函数：目录的准入、字段取值、目录 ID 选择、属性与字段项同步、退役规则
 * 都可在 Magic 编辑器实时调整。db.table 使用当前请求绑定的租户元数据数据源，
 * 因而不依赖登记源（MySQL/达梦/Oracle 等）的 SQL 方言。
 */
var textOf = value => value == null ? '' : value + ''
var blank = value => value == null || textOf(value).trim() == ''
var nowTime = () => new Date()
var newId = () => IdUtil.getSnowflakeNextIdStr()
var rowValue = (row, keys) => {
    if (row == null) {
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            return value
        }
    }
    return null
}
var firstText = values => {
    for (value in values) {
        if (!blank(value)) {
            return textOf(value).trim()
        }
    }
    return ''
}
var boolValue = value => value == true || textOf(value).toLowerCase() == 'true' || textOf(value) == '1'
var catalogEligible = businessType => {
    var normalized = textOf(businessType).trim().toLowerCase()
    return normalized == '业务表' || normalized == '字典表' || normalized == '日志表'
        || normalized == 'business' || normalized == 'dictionary' || normalized == 'log'
}
var catalogNameOf = table => firstText([
    rowValue(table, ['catalogName', 'catalog_name']),
    rowValue(table, ['tableNameCn', 'table_name_cn']),
    rowValue(table, ['tableComment', 'table_comment']),
    rowValue(table, ['tableName', 'table_name'])
])
var catalogNameEnOf = table => firstText([
    rowValue(table, ['catalogNameEn', 'catalog_name_en']),
    rowValue(table, ['tableName', 'table_name'])
])
var assetDescOf = table => firstText([
    rowValue(table, ['assetDesc', 'asset_desc']),
    rowValue(table, ['tableComment', 'table_comment']),
    catalogNameOf(table)
])
var catalogPropRows = (catalogId, propName) => db.table('da_prop_t').where()
    .eq('parent_id', catalogId).eq('data_type', 'catalog').eq('prop_name', propName).eq('is_del', 0).select()
var catalogProp = (catalogId, propName) => {
    var rows = catalogPropRows(catalogId, propName)
    return rows == null || rows.size() == 0 ? null : rows.get(0)
}
var upsertCatalogProp = (catalogId, name, value, now) => {
    var rows = catalogPropRows(catalogId, name)
    if (rows == null || rows.size() == 0) {
        db.table('da_prop_t').primary('tid').insert({
            tid: newId(), parent_id: catalogId, prop_name: name, prop_value: value,
            prop_type: '0', data_type: 'catalog', is_del: 0, created_time: now, updated_time: now
        })
        return
    }
    var primary = rows.get(0)
    db.table('da_prop_t').primary('tid').update({
        tid: rowValue(primary, ['tid']), prop_value: value, prop_type: '0',
        data_type: 'catalog', is_del: 0, updated_time: now
    }, true)
    for (duplicate in rows) {
        if (textOf(rowValue(duplicate, ['tid'])) != textOf(rowValue(primary, ['tid']))) {
            db.table('da_prop_t').primary('tid').update({
                tid: rowValue(duplicate, ['tid']), is_del: 1, updated_time: now
            }, true)
        }
    }
}
var catalogCanRepresentTable = (catalogId, sourceTableId) => {
    if (blank(catalogId)) {
        return false
    }
    var catalog = db.table('da_catalog_t').where().eq('tid', catalogId).eq('is_del', 0).selectOne()
    if (catalog == null) {
        return false
    }
    var physicalSource = rowValue(catalog, ['sourceTableId', 'source_table_id'])
    if (!blank(physicalSource) && textOf(physicalSource) != textOf(sourceTableId)) {
        return false
    }
    var prop = catalogProp(catalogId, 'sourceTableId')
    var propertySource = rowValue(prop, ['propValue', 'prop_value'])
    return blank(propertySource) || textOf(propertySource) == textOf(sourceTableId)
}
var resolveCatalogId = table => {
    var tableIdValue = textOf(rowValue(table, ['tid']))
    var requested = rowValue(table, ['sourceCatalogId', 'source_catalog_id'])
    if (catalogCanRepresentTable(requested, tableIdValue)) {
        return textOf(requested)
    }
    var physical = db.table('da_catalog_t').where()
        .eq('source_table_id', tableIdValue).eq('is_del', 0).select()
    if (physical != null && physical.size() > 0) {
        return textOf(rowValue(physical.get(0), ['tid']))
    }
    var props = db.table('da_prop_t').where()
        .eq('data_type', 'catalog').eq('prop_name', 'sourceTableId')
        .eq('prop_value', tableIdValue).eq('is_del', 0).select()
    if (props != null && props.size() > 0) {
        return textOf(rowValue(props.get(0), ['parentId', 'parent_id']))
    }
    return newId()
}
var catalogValues = (catalogId, table, now) => ({
    tid: catalogId,
    is_del: 0,
    asset_status: 2,
    flow_status: 2,
    catalog_name: catalogNameOf(table),
    catalog_name_en: catalogNameEnOf(table),
    asset_desc: assetDescOf(table),
    app_id: rowValue(table, ['appId', 'app_id']),
    app_name: rowValue(table, ['appName', 'app_name']),
    db_id: rowValue(table, ['datasourceId', 'datasource_id']),
    db_name: rowValue(table, ['dbName', 'db_name']),
    source_table_id: rowValue(table, ['tid']),
    source_table_name: rowValue(table, ['tableName', 'table_name']),
    org_id: rowValue(table, ['orgId', 'org_id']),
    manage_unit: firstText([rowValue(table, ['manageUnit', 'manage_unit']), rowValue(table, ['orgId', 'org_id'])]),
    business_type: rowValue(table, ['businessType', 'business_type']),
    data_source_type: firstText([rowValue(table, ['dataSourceType', 'data_source_type']), 'database']),
    timestamp_field: rowValue(table, ['timestampField', 'timestamp_field']),
    dictionary_table_flag: rowValue(table, ['dictionaryTableFlag', 'dictionary_table_flag']),
    field_governance_config: rowValue(table, ['fieldGovernanceConfig', 'field_governance_config']),
    updated_time: now
})
var upsertCatalog = (catalogId, table, now) => {
    var values = catalogValues(catalogId, table, now)
    var current = db.table('da_catalog_t').where().eq('tid', catalogId).selectOne()
    if (current == null) {
        values.created_time = now
        values.reg_time = now
        db.table('da_catalog_t').primary('tid').insert(values)
    } else {
        db.table('da_catalog_t').primary('tid').update(values, true)
    }
}
var syncCatalogProperties = (catalogId, table, now) => {
    var properties = {
        catalogName: catalogNameOf(table),
        catalogNameEn: catalogNameEnOf(table),
        assetDesc: assetDescOf(table),
        appId: rowValue(table, ['appId', 'app_id']),
        appName: rowValue(table, ['appName', 'app_name']),
        dbId: rowValue(table, ['datasourceId', 'datasource_id']),
        dbName: rowValue(table, ['dbName', 'db_name']),
        sourceTableId: rowValue(table, ['tid']),
        sourceTableName: rowValue(table, ['tableName', 'table_name']),
        orgId: rowValue(table, ['orgId', 'org_id']),
        manageUnit: firstText([rowValue(table, ['manageUnit', 'manage_unit']), rowValue(table, ['orgId', 'org_id'])]),
        businessType: rowValue(table, ['businessType', 'business_type']),
        dataSourceType: firstText([rowValue(table, ['dataSourceType', 'data_source_type']), 'database']),
        timestampField: rowValue(table, ['timestampField', 'timestamp_field']),
        dictionaryTableFlag: rowValue(table, ['dictionaryTableFlag', 'dictionary_table_flag']),
        fieldGovernanceConfig: rowValue(table, ['fieldGovernanceConfig', 'field_governance_config'])
    }
    for (propertyName in properties.keySet()) {
        upsertCatalogProp(catalogId, propertyName + '', properties[propertyName], now)
    }
}
var itemValues = (itemId, catalogId, source, now) => ({
    tid: itemId,
    catalog_id: catalogId,
    is_del: 0,
    col_name: firstText([rowValue(source, ['catalogItemName', 'catalog_item_name']), rowValue(source, ['columnComment', 'column_comment']), rowValue(source, ['columnName', 'column_name'])]),
    col_en: firstText([rowValue(source, ['catalogItemEn', 'catalog_item_en']), rowValue(source, ['columnName', 'column_name'])]),
    col_type: firstText([rowValue(source, ['catalogItemType', 'catalog_item_type']), rowValue(source, ['columnType', 'column_type']), rowValue(source, ['dataType', 'data_type'])]),
    col_comment: rowValue(source, ['columnComment', 'column_comment']),
    col_length: rowValue(source, ['length']),
    is_pk: rowValue(source, ['primaryKey', 'primary_key']),
    is_nullable: rowValue(source, ['nullable']),
    sort_no: rowValue(source, ['ordinalPosition', 'ordinal_position']),
    data_standard_id: rowValue(source, ['dataStandardId', 'data_standard_id']),
    quality_rule: rowValue(source, ['qualityRule', 'quality_rule']),
    enable_code_table: rowValue(source, ['enableCodeTable', 'enable_code_table']),
    code_table_id: rowValue(source, ['codeTableId', 'code_table_id']),
    source_table_column_id: rowValue(source, ['tid']),
    updated_time: now
})
var syncCatalogItems = (catalogId, tableIdValue, now) => {
    var existing = db.table('da_catalog_item_t').where().eq('catalog_id', catalogId).eq('is_del', 0).select()
    var existingByColumn = {}
    for (item in (existing == null ? [] : existing)) {
        var sourceColumnId = rowValue(item, ['sourceTableColumnId', 'source_table_column_id'])
        if (!blank(sourceColumnId)) {
            existingByColumn[textOf(sourceColumnId)] = item
        }
    }
    var retained = {}
    var columns = db.table('db_table_column_t').where().eq('table_id', tableIdValue).eq('is_del', 0)
        .orderBy('ordinal_position').select()
    for (column in (columns == null ? [] : columns)) {
        var columnId = textOf(rowValue(column, ['tid']))
        retained[columnId] = true
        var item = existingByColumn[columnId]
        var values = itemValues(item == null ? newId() : rowValue(item, ['tid']), catalogId, column, now)
        if (item == null) {
            values.created_time = now
            db.table('da_catalog_item_t').primary('tid').insert(values)
        } else {
            db.table('da_catalog_item_t').primary('tid').update(values, true)
        }
    }
    for (columnId in existingByColumn.keySet()) {
        var existingItem = existingByColumn[columnId]
        if (retained[columnId] == null) {
            db.table('da_catalog_item_t').primary('tid').update({
                tid: rowValue(existingItem, ['tid']), is_del: 1, updated_time: now
            }, true)
        }
    }
}
var updateTableLink = (catalogId, table, now) => db.table('db_table_t').primary('tid').update({
    tid: rowValue(table, ['tid']),
    source_catalog_id: catalogId,
    catalog_name: catalogNameOf(table),
    catalog_name_en: catalogNameEnOf(table),
    source_table_id: rowValue(table, ['tid']),
    source_table_name: rowValue(table, ['tableName', 'table_name']),
    updated_time: now
}, true)
var retireCatalogs = (tableIdValue, now) => {
    var ids = {}
    var physical = db.table('da_catalog_t').where().eq('source_table_id', tableIdValue).eq('is_del', 0).select()
    for (catalog in (physical == null ? [] : physical)) {
        ids[textOf(rowValue(catalog, ['tid']))] = true
    }
    var props = db.table('da_prop_t').where().eq('data_type', 'catalog').eq('prop_name', 'sourceTableId')
        .eq('prop_value', tableIdValue).eq('is_del', 0).select()
    for (prop in (props == null ? [] : props)) {
        ids[textOf(rowValue(prop, ['parentId', 'parent_id']))] = true
    }
    var retired = 0
    for (catalogIdValue in ids.keySet()) {
        var catalogId = catalogIdValue + ''
        db.table('da_catalog_t').primary('tid').update({tid: catalogId, is_del: 1, updated_time: now}, true)
        var catalogProps = db.table('da_prop_t').where().eq('parent_id', catalogId).eq('data_type', 'catalog').eq('is_del', 0).select()
        for (prop in (catalogProps == null ? [] : catalogProps)) {
            db.table('da_prop_t').primary('tid').update({tid: rowValue(prop, ['tid']), is_del: 1, updated_time: now}, true)
        }
        var catalogItems = db.table('da_catalog_item_t').where().eq('catalog_id', catalogId).eq('is_del', 0).select()
        for (item in (catalogItems == null ? [] : catalogItems)) {
            db.table('da_catalog_item_t').primary('tid').update({tid: rowValue(item, ['tid']), is_del: 1, updated_time: now}, true)
        }
        retired = retired + 1
    }
    db.table('db_table_t').primary('tid').update({tid: tableIdValue, source_catalog_id: null, updated_time: now}, true)
    return retired
}
var syncIndex = (catalogId, table) => {
    var document = {
        tid: catalogId, id: catalogId, assetType: 'catalog',
        catalogName: catalogNameOf(table), catalogNameEn: catalogNameEnOf(table),
        sourceTableId: rowValue(table, ['tid']), sourceTableName: rowValue(table, ['tableName', 'table_name']),
        dbId: rowValue(table, ['datasourceId', 'datasource_id']), dbName: rowValue(table, ['dbName', 'db_name']),
        appId: rowValue(table, ['appId', 'app_id']), appName: rowValue(table, ['appName', 'app_name']),
        orgId: rowValue(table, ['orgId', 'org_id']),
        dataSourceType: firstText([rowValue(table, ['dataSourceType', 'data_source_type']), 'database']),
        assetStatus: 2, flowStatus: 2
    }
    try {
        esCommonService.saveOrUpdate('dataassets', catalogId, jsons.toJSONStr(document), false)
        return true
    } catch (e) {
        log.warn('数据目录索引同步失败，但关系投影已保留: catalogId={}, message={}', catalogId, e.message)
        return false
    }
}
var ensure = (requestedTableId, withIndex) => {
    if (blank(requestedTableId)) {
        exit 400, 'tableId不能为空'
    }
    var table = db.table('db_table_t').where().eq('tid', textOf(requestedTableId).trim()).eq('is_del', 0)
        .eq('asset_status', 2).selectOne()
    if (table == null) {
        exit 404, '数据表不存在或尚未完成登记'
    }
    var tableIdValue = textOf(rowValue(table, ['tid']))
    var now = nowTime()
    if (!catalogEligible(rowValue(table, ['businessType', 'business_type']))) {
        return {tableId: tableIdValue, retired: retireCatalogs(tableIdValue, now), eligible: false, indexed: false}
    }
    var datasourceId = rowValue(table, ['datasourceId', 'datasource_id'])
    var datasource = blank(datasourceId) ? null : db.table('db_datasource_t').where().eq('tid', datasourceId).eq('is_del', 0).selectOne()
    if (datasource != null) {
        table.dbName = rowValue(datasource, ['dbName', 'db_name'])
        table.appId = rowValue(datasource, ['appId', 'app_id'])
        var applicationId = rowValue(datasource, ['appId', 'app_id'])
        if (!blank(applicationId)) {
            var application = db.table('sym_application_t').where().eq('tid', applicationId).eq('is_del', 0).selectOne()
            if (application != null) {
                table.appName = rowValue(application, ['appName', 'app_name'])
            }
        }
    }
    var catalogId = resolveCatalogId(table)
    upsertCatalog(catalogId, table, now)
    syncCatalogProperties(catalogId, table, now)
    syncCatalogItems(catalogId, tableIdValue, now)
    updateTableLink(catalogId, table, now)
    return {tableId: tableIdValue, catalogId: catalogId, catalogName: catalogNameOf(table), eligible: true, indexed: boolValue(withIndex) ? syncIndex(catalogId, table) : false}
}
var syncCompleted = maximum => {
    var requested = maximum == null ? 10000 : maximum::int
    var limit = requested <= 0 ? 10000 : (requested > 10000 ? 10000 : requested)
    var completed = db.table('db_table_t').where().eq('is_del', 0).eq('asset_status', 2).orderByDesc('updated_time').select()
    var targets = []
    for (table in (completed == null ? [] : completed)) {
        if (catalogEligible(rowValue(table, ['businessType', 'business_type']))) {
            targets.add(textOf(rowValue(table, ['tid'])))
            if (targets.size() >= limit) {
                break
            }
        }
    }
    var failures = []
    var synced = 0
    for (targetId in targets) {
        try {
            db.transaction(() => ensure(targetId, false))
            synced = synced + 1
        } catch (e) {
            failures.add({tableId: targetId, message: e.message == null ? (e + '') : e.message + ''})
        }
    }
    return {total: targets.size(), synced: synced, failed: failures.size(), failures: failures}
}

if (action == 'ensure') {
    return db.transaction(() => ensure(tableId, synchronizeIndex))
}
if (action == 'syncCompleted') {
    return syncCompleted(maxRows)
}
exit 400, 'action仅支持 ensure 或 syncCompleted'
