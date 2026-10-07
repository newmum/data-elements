{
  "properties" : { },
  "id" : "datapd_table_catalog_save_01",
  "script" : null,
  "groupId" : "datapd_catalog_group_01",
  "name" : "08.保存数据目录",
  "createTime" : 1783613131792,
  "updateTime" : 1791030855523,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/saveOrUpdate",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "保存目录主表、动态属性、数据项与来源表关联；数据库事务先提交，再同步检索投影。dryRun=true仅验证；使用请求参数 assetType、propList、tid、dryRun、catalogItems；涉及 da_prop_t、da_catalog_t、da_catalog_item_t、db_table_t 等业务表。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import java.util.Map
import java.util.List
import log
import tenantRuntime

// 数据盘点统一保存接口。
// 目标是承接原 /dst/catalog/saveOrUpdate 的核心保存能力，业务逻辑直接落在 Magic API，便于后续调整。
// 原平台登记仍直接生效；数据中心传 reviewRequired=true 时保存为待提交审核。

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var nowTime = () => new Date()
var newId = () => IdUtil.getSnowflakeNextIdStr()

var valueText = (value) => {
    if (value == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    if (value instanceof Map || value instanceof List) {
        // 返回当前业务分支的标准结果。
        return jsons.toJSONStr(value)
    }
    // 返回当前业务分支的标准结果。
    return textOf(value)
}

var propValue = (props, key) => {
    if (props == null || key == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    // 返回当前业务分支的标准结果。
    return props[key]
}

var firstValue = (props, keys) => {
    for (key in keys) {
        var value = propValue(props, key)
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

var getPropType = (key) => {
    if (key == null) {
        // 返回当前业务分支的标准结果。
        return '0'
    }
    if (key.endsWith('_date')) {
        // 返回当前业务分支的标准结果。
        return '4'
    }
    if (key.endsWith('_num')) {
        // 返回当前业务分支的标准结果。
        return '1'
    }
    if (key.endsWith('_file')) {
        // 返回当前业务分支的标准结果。
        return '5'
    }
    if (key.endsWith('_dict')) {
        // 返回当前业务分支的标准结果。
        return '3'
    }
    if (key.endsWith('_range')) {
        // 返回当前业务分支的标准结果。
        return '6'
    }
    if (key.endsWith('_time')) {
        // 返回当前业务分支的标准结果。
        return '7'
    }
    // 返回当前业务分支的标准结果。
    return '0'
}

var getRealPropName = (key) => {
    if (key == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    var suffixes = ['_date', '_num', '_file', '_dict', '_range', '_time']
    for (suffix in suffixes) {
        if (key.endsWith(suffix)) {
            // 返回当前业务分支的标准结果。
            return key.substring(0, key.length() - suffix.length())
        }
    }
    // 返回当前业务分支的标准结果。
    return key
}

var upsertAssetProps = (assetId, tenantId, assetType, props) => {
    var now = nowTime()
    // 从 MySQL 业务表读取当前有效数据。
    var oldRows = db.table('da_prop_t').where().eq('tenant_id', tenantId)
        .eq('parent_id', assetId).eq('is_del', 0).select()
    var oldMap = {}
    for (old in oldRows) {
        oldMap[rowValue(old, ['propName', 'prop_name'])] = old
    }
    if (props == null) {
        return
    }
    for (rawKey in props.keySet()) {
        var propName = getRealPropName(rawKey)
        if (blank(propName)) {
            continue
        }
        var propType = getPropType(rawKey)
        var propText = valueText(props[rawKey])
        var old = oldMap[propName]
        if (old == null) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_prop_t').primary('tid').insert({
                tid: newId(),
                tenant_id: tenantId,
                parent_id: assetId,
                prop_name: propName,
                prop_value: propText,
                prop_type: propType,
                data_type: assetType,
                is_del: 0,
                created_time: now,
                updated_time: now
            })
        } else {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_prop_t').primary('tid').update({
                tid: rowValue(old, ['tid']),
                prop_value: propText,
                prop_type: propType,
                data_type: assetType,
                updated_time: now,
                is_del: 0
            }, true)
        }
    }
}

var buildAssetResultData = (assetId) => {
    // 从 MySQL 业务表读取当前有效数据。
    var asset = db.table('da_catalog_t').where().eq('tenant_id', tenantId)
        .eq('tid', assetId).eq('is_del', 0).selectOne()
    var result = {}
    if (asset != null) {
        result.id = rowValue(asset, ['tid'])
        result.tid = rowValue(asset, ['tid'])
        result.tenantId = rowValue(asset, ['tenantId', 'tenant_id'])
        result.assetType = 'catalog'
        result.assetStatus = rowValue(asset, ['assetStatus', 'asset_status']) == null ? 2 : rowValue(asset, ['assetStatus', 'asset_status'])
        result.flowStatus = rowValue(asset, ['flowStatus', 'flow_status']) == null ? 2 : rowValue(asset, ['flowStatus', 'flow_status'])
        result.regTime = rowValue(asset, ['regTime', 'reg_time'])
        result.createdTime = rowValue(asset, ['createdTime', 'created_time'])
        result.updatedTime = rowValue(asset, ['updatedTime', 'updated_time'])
        result.regTime_date = result.regTime
        result.createdTime_date = result.createdTime
        result.updatedTime_date = result.updatedTime
        result.dataCatalogNum = rowValue(asset, ['dataCatalogNum', 'data_catalog_num'])
        result.tableNum = rowValue(asset, ['tableNum', 'table_num'])
    }
    // 从 MySQL 业务表读取当前有效数据。
    var props = db.table('da_prop_t').where().eq('tenant_id', tenantId)
        .eq('parent_id', assetId).eq('is_del', 0).select()
    for (prop in props) {
        var name = rowValue(prop, ['propName', 'prop_name'])
        if (!blank(name)) {
            result[name] = rowValue(prop, ['propValue', 'prop_value'])
        }
    }
    // 返回当前业务分支的标准结果。
    return result
}

var saveToEs = (assetId, data) => {
    if (blank(assetId) || data == null) {
        return
    }
    try {
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        esCommonService.saveOrUpdate('dataassets', assetId, jsons.toJSONStr(data), false)
    } catch (e) {
        log.warn('sync dataassets es failed, tid: {}, error: {}', assetId, e.message)
    }
}

var itemValue = (item, keys) => {
    if (item == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = item[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

var saveCatalogItems = (catalogId, tenantId, items) => {
    if (blank(catalogId) || items == null) {
        return
    }
    // 从 MySQL 业务表读取当前有效数据。
    var oldRows = db.table('da_catalog_item_t').where().eq('tenant_id', tenantId)
        .eq('catalog_id', catalogId).eq('is_del', 0).select()
    var oldMap = {}
    for (old in oldRows) {
        oldMap[rowValue(old, ['tid'])] = old
    }
    var now = nowTime()
    var sort = 1
    for (item in items) {
        var itemId = itemValue(item, ['tid', 'id'])
        var data = {
            catalog_id: catalogId,
            tenant_id: tenantId,
            col_name: itemValue(item, ['colName', 'col_name']),
            col_en: itemValue(item, ['colEn', 'col_en', 'columnName']),
            col_type: itemValue(item, ['colType', 'col_type', 'dataType']),
            col_comment: itemValue(item, ['colComment', 'col_comment', 'columnComment']),
            col_length: itemValue(item, ['colLength', 'col_length', 'columnSize']),
            is_pk: itemValue(item, ['isPk', 'is_pk', 'primaryKey']),
            is_masking: itemValue(item, ['isMasking', 'is_masking']),
            is_fk: itemValue(item, ['isFk', 'is_fk']),
            is_nullable: itemValue(item, ['isNullable', 'is_nullable', 'nullable']),
            date_format: itemValue(item, ['dateFormat', 'date_format']),
            mark_lvl: itemValue(item, ['markLvl', 'mark_lvl']),
            col_unit: itemValue(item, ['colUnit', 'col_unit']),
            col_precision: itemValue(item, ['colPrecision', 'col_precision']),
            masking_id: itemValue(item, ['maskingId', 'masking_id']),
            sort_no: itemValue(item, ['sortNo', 'sort_no']) == null ? sort : itemValue(item, ['sortNo', 'sort_no']),
            share_type: itemValue(item, ['shareType', 'share_type']),
            share_condition: itemValue(item, ['shareCondition', 'share_condition']),
            is_dict: itemValue(item, ['isDict', 'is_dict']),
            dict_name: itemValue(item, ['dictName', 'dict_name']),
            col_max_length: itemValue(item, ['colMaxLength', 'col_max_length']),
            default_value: itemValue(item, ['defaultValue', 'default_value']),
            data_standard_id: itemValue(item, ['dataStandardId', 'data_standard_id']),
            quality_rule: itemValue(item, ['qualityRule', 'quality_rule']),
            enable_code_table: itemValue(item, ['enableCodeTable', 'enable_code_table']),
            code_table_id: itemValue(item, ['codeTableId', 'code_table_id']),
            source_table_column_id: itemValue(item, ['sourceTableColumnId', 'source_table_column_id']),
            target_table_column_id: itemValue(item, ['targetTableColumnId', 'target_table_column_id']),
            updated_time: now,
            is_del: 0
        }
        if (!blank(itemId) && oldMap[itemId] != null) {
            data.tid = itemId
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_catalog_item_t').primary('tid').update(data, true)
            oldMap[itemId] = null
        } else {
            // 客户端行标识可能来自源字段，目录数据项必须生成独立主键。
            data.tid = newId()
            data.created_time = now
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_catalog_item_t').primary('tid').insert(data)
        }
        sort = sort + 1
    }
    for (oldKey in oldMap.keySet()) {
        var old = oldMap[oldKey]
        if (old != null) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_catalog_item_t').primary('tid').update({
                tid: rowValue(old, ['tid']),
                is_del: 1,
                updated_time: now
            }, true)
        }
    }
}


/*
 * 目录保存：同一事务写 da_catalog_t / da_prop_t / da_catalog_item_t 与 db_table_t 关联。
 * 不读写源库物理表；先校验当前登录租户归属。dryRun=true 仅校验并返回计划。
 */
if (body == null) { exit 400, '请求参数不能为空' }
var tenantId = tenantRuntime.id()
var assetType = blank(body.assetType) ? 'catalog' : textOf(body.assetType)
if (assetType != 'catalog') { exit 400, '本接口只允许保存数据目录' }
var props = body.propList
if (props == null || !(props instanceof Map)) { exit 400, '目录属性不能为空' }
var tid = body.tid
var old = blank(tid) ? null : db.table('da_catalog_t').where()
    .eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (!blank(tid) && old == null) { exit 404, '数据目录不存在或无访问权限' }
var oldProps = blank(tid) ? {} : buildAssetResultData(tid)
var sourceProvided = props.containsKey('sourceTableId')
var sourceTableId = sourceProvided ? props.sourceTableId :
    firstValue(oldProps, ['sourceTableId'])
if (!sourceProvided && blank(sourceTableId)) { sourceTableId = rowValue(old, ['sourceTableId', 'source_table_id']) }
var sourceTable = blank(sourceTableId) ? null : db.table('db_table_t').where()
    .eq('tid', sourceTableId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (!blank(sourceTableId) && sourceTable == null) { exit 404, '关联的数据表不存在或无访问权限' }
var name = props.containsKey('catalogName') ? props.catalogName :
    firstValue(oldProps, ['catalogName'])
if (blank(name)) { name = rowValue(old, ['catalogName', 'catalog_name']) }
if (blank(name)) { exit 400, '数据目录名称不能为空' }
if (body.dryRun == true) { return {dryRun: true, tid: tid, catalogName: name, sourceTableId: sourceTableId} }
var reviewRequired = body.reviewRequired == true
var saved = db.transaction(() => {
    var now = nowTime()
    // 元数据登记保持原有即登记完成的行为；数据中心编目显式进入待提交审核。
    var values = { tenant_id: tenantId, asset_status: reviewRequired ? 0 : 2,
        flow_status: reviewRequired ? 0 : 2, is_del: 0, updated_time: now }
    if (reviewRequired) {
        values.resource_revision_no = old == null ? 1 :
            ((rowValue(old, ['resourceRevisionNo', 'resource_revision_no']) == null ? 1 :
                rowValue(old, ['resourceRevisionNo', 'resource_revision_no']))::int) + 1
    }
    var fields = {
        catalogName: 'catalog_name', catalogNameEn: 'catalog_name_en', assetDesc: 'asset_desc',
        appId: 'app_id', appName: 'app_name', dbId: 'db_id', dbName: 'db_name',
        sourceTableId: 'source_table_id', sourceTableName: 'source_table_name',
        sourceDbId: 'source_db_id', sourceDbName: 'source_db_name',
        targetTableId: 'target_table_id', targetTableName: 'target_table_name',
        targetDbId: 'target_db_id', targetDbType: 'target_db_type', orgId: 'org_id',
        manageUnit: 'manage_unit', businessType: 'business_type', dataSourceType: 'data_source_type',
        timestampField: 'timestamp_field', dictionaryTableFlag: 'dictionary_table_flag',
        fieldGovernanceConfig: 'field_governance_config'
    }
    for (key in fields.keySet()) {
        if (props.containsKey(key)) { values[fields[key]] = valueText(props[key]) }
    }
    values.catalog_name = name
    if (!props.containsKey('catalogNameEn') && old != null) {
        values.catalog_name_en = rowValue(old, ['catalogNameEn', 'catalog_name_en'])
    }
    if (sourceTable != null) {
        values.source_table_id = sourceTableId
        if (blank(values.source_table_name)) { values.source_table_name = rowValue(sourceTable, ['tableName', 'table_name']) }
        if (blank(values.db_id)) { values.db_id = rowValue(sourceTable, ['datasourceId', 'datasource_id']) }
    }
    if (old == null) {
        tid = newId()
        values.tid = tid
        values.created_time = now
        values.reg_time = now
        db.table('da_catalog_t').primary('tid').insert(values)
    } else {
        values.tid = tid
        db.table('da_catalog_t').primary('tid').update(values, true)
    }
    upsertAssetProps(tid, tenantId, 'catalog', props)
    // 基础列与兼容属性保持一致，旧列表与详情都可读取相同的数据。
    for (key in fields.keySet()) {
        if (values.containsKey(fields[key])) {
            var singleProp = {}
            singleProp[key] = values[fields[key]]
            upsertAssetProps(tid, tenantId, 'catalog', singleProp)
        }
    }
    if (body.catalogItems != null) { saveCatalogItems(tid, tenantId, body.catalogItems) }
    // 若重新绑定来源表，先清除原表仍指向本目录的关联。
    db.table('db_table_t').where().eq('tenant_id', tenantId).eq('source_catalog_id', tid)
        .update({source_catalog_id: null, catalog_name: null, catalog_name_en: null, updated_time: now}, true)
    if (sourceTable != null) {
        db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', sourceTableId).eq('is_del', 0)
            .update({source_catalog_id: tid, catalog_name: name, catalog_name_en: values.catalog_name_en, updated_time: now}, true)
    }
    var result = buildAssetResultData(tid)
    result.assetStatus = values.asset_status
    result.flowStatus = values.flow_status
    return result
})
// 索引是查询投影；数据库事务成功后再更新，索引故障不回滚已保存的目录。
saveToEs(saved.tid, saved)
return saved
