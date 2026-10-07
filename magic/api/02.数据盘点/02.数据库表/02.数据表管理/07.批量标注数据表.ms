{
  "properties": {},
  "id": "db_table_batch_annotate_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "07.批量标注数据表",
  "createTime": null,
  "updateTime": 1787153759223,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/batchAnnotate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"dbId\": \"4b5d9992d89744b9b514f8905dc27998\",\n  \"annotated\": 1,\n  \"dryRun\": true,\n  \"refreshDatasource\": false,\n  \"tables\": [\n    {\n      \"tid\": \"076de72a58024af2aad48c5261df440e\",\n      \"propList\": {\n        \"tableName\": \"police_case_record\",\n        \"tableNameCn\": \"公安案件登记业务表\",\n        \"tableComment\": \"公安案件登记业务表\",\n        \"tableType\": \"数据表\",\n        \"businessType\": \"业务表\",\n        \"businessTypeReason\": \"人工批量标注业务类型\",\n        \"fieldCount\": 10,\n        \"recordCount\": 0\n      }\n    }\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "批量标注数据表（POST /dst/database/table/batchAnnotate）。批量写入 db_table_t，并使用一次 bulk 请求同步当前分片的 dataassets ES 投影；同一数据源的已有表只预读一次，避免逐表 N+1 查询。refreshDatasource=false 可由分片客户端延迟到全部分片完成后统一刷新数据源统计；dryRun=true 时只校验和返回待写入结果；使用请求参数 tables、rows、list、datasourceId、dbId、annotated、dryRun、refreshDatasource。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.批量标注数据表
// 路由：POST /dst/database/table/batchAnnotate
// 业务说明：批量标注数据表（POST /dst/database/table/batchAnnotate）。批量写入 db_table_t，并使用一次 bulk 请求同步当前分片的 dataassets ES 投影；同一数据源的已有表只预读一次，避免逐表 N+1 查询。refreshDatasource=false 可由分片客户端延迟到全部分片完成后统一刷新数据源统计；dryRun=true 时只校验和返回待写入结果；使用请求参数 tables、rows、list、datasourceId、dbId、annotated、dryRun、refreshDatasource。
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import log
import metadataAsset
import tenantRuntime
import '@post:/dst/maintenance/refresh' as refreshAssetIndex

/*
 * 数据表管理 07：第二步批量标注数据表。
 *
 * 该接口服务 register-dbTable 的多选/当前筛选分片标注场景。每个数据源只预读一次
 * db_table_t，分片内逐表幂等写主表，再按 dataassets 既有 mapping 批量同步搜索投影。
 * 数据表仍以 db_table_t 为唯一主表，不写 da_asset_t/da_prop_t/审批关系。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var newId = () => IdUtil.getSnowflakeNextIdStr()
var nowTime = () => new Date()
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
var normalizeKey = (value) => blank(value) ? '' : textOf(value).trim().toLowerCase()
var requiresStandardName = (businessType) => {
    var type = blank(businessType) ? '业务表' : textOf(businessType).trim()
    return type == '字典表' || type == '业务表' || type == '日志表'
}
var syncGovernanceBusinessType = (existing, businessType) => {
    var raw = rowValue(existing, ['fieldGovernanceConfig', 'field_governance_config'])
    if (blank(raw) || blank(businessType)) {
        return raw
    }
    try {
        var config = jsons.toJSONObject(textOf(raw))
        config.businessType = businessType
        return jsons.toJSONStr(config)
    } catch (e) {
        log.warn('sync field_governance_config businessType failed, tableId: {}, error: {}', rowValue(existing, ['tid']), e.message)
        return raw
    }
}

// 解析请求体：tables/rows/list 兼容前端不同命名，annotated 默认按批量标注处理。
var tableItems = body == null ? null : (body.tables == null ? (body.rows == null ? body.list : body.rows) : body.tables)
if (tableItems == null || tableItems.size() == 0) {
    exit 400, 'tables 不能为空'
}
var requestDatasourceId = body == null ? null : (body.datasourceId == null ? body.dbId : body.datasourceId)
var requestAnnotated = body == null || body.annotated == null ? 1 : body.annotated
var annotatedFlag = textOf(requestAnnotated) == '0' ? 0 : 1
var dryRun = body != null && body.dryRun == true
var refreshDatasource = body == null || body.refreshDatasource != false
var tenantId = tenantRuntime.id()

// 先完成整批参数校验，避免第 N 条业务参数错误时前 N-1 条已经落库。
var preparedItems = []
for (item in tableItems) {
    var props = rowValue(item, ['propList', 'props'])
    if (props == null) {
        props = item
    }
    var datasourceId = rowValue(props, ['datasourceId', 'dbId'])
    if (blank(datasourceId)) {
        datasourceId = requestDatasourceId
    }
    if (blank(datasourceId)) {
        exit 400, 'datasourceId 不能为空'
    }
    var tableName = rowValue(props, ['tableName', 'tableNameEn', 'sourceTableName'])
    if (blank(tableName)) {
        exit 400, 'tableName 不能为空'
    }
    var tableComment = rowValue(props, ['tableComment', 'tableNameCn', 'catalogName'])
    var businessType = rowValue(props, ['businessType', 'business_type'])
    if (annotatedFlag == 1 && requiresStandardName(businessType)
        && (blank(tableComment) || normalizeKey(tableComment) == normalizeKey(tableName))) {
        exit 400, tableName + ' 请规范填写数据资源名称，不能留空或直接使用英文表名'
    }
    preparedItems.add({
        item: item,
        props: props,
        datasourceId: datasourceId,
        tableName: tableName,
        tableComment: tableComment
    })
}

// 同一分片通常只有一个数据源；每个数据源只查一次已有表，替代逐表最多两次查询。
var existingCache = {}
var existingQueryCount = 0
var existingMapsOf = (datasourceId) => {
    var datasourceKey = textOf(datasourceId)
    var cached = existingCache[datasourceKey]
    if (cached != null) {
        return cached
    }
    var byId = {}
    var byName = {}
    var existingRows = db.table('db_table_t').where()
        .eq('datasource_id', datasourceId).select()
    existingQueryCount = existingQueryCount + 1
    for (existingRow in existingRows) {
        var existingId = rowValue(existingRow, ['tid'])
        if (!blank(existingId)) {
            byId[textOf(existingId)] = existingRow
        }
        if (textOf(rowValue(existingRow, ['isDel', 'is_del'])) == '0') {
            var existingName = rowValue(existingRow, ['tableName', 'table_name'])
            if (!blank(existingName)) {
                byName[normalizeKey(existingName)] = existingRow
            }
        }
    }
    cached = { byId: byId, byName: byName }
    existingCache[datasourceKey] = cached
    return cached
}
var saveColumns = (tableId, columns, tenantId) => {
    if (columns == null || columns.size() == 0) {
        return 0
    }
    var oldRows = db.table('db_table_column_t').where().eq('table_id', tableId).eq('is_del', 0).select()
    var oldMap = {}
    for (old in oldRows) {
        oldMap[normalizeKey(rowValue(old, ['columnName', 'column_name']))] = old
    }
    var ordinal = 0
    var newRows = []
    for (column in columns) {
        ordinal = ordinal + 1
        var columnName = rowValue(column, ['columnName', 'name'])
        if (blank(columnName)) {
            continue
        }
        var old = oldMap[normalizeKey(columnName)]
        var columnId = old == null ? newId() : rowValue(old, ['tid'])
        var values = {
            tid: columnId,
            table_id: tableId,
            tenant_id: tenantId,
            column_name: columnName,
            column_comment: old == null ? rowValue(column, ['columnComment']) : rowValue(old, ['columnComment', 'column_comment']),
            data_type: rowValue(column, ['dataType']),
            column_type: rowValue(column, ['columnType', 'dataType']),
            length: rowValue(column, ['length', 'columnLength']),
            precision_length: rowValue(column, ['precision']),
            scale: rowValue(column, ['scale']),
            nullable: rowValue(column, ['nullable']) == false ? 0 : 1,
            default_value: rowValue(column, ['defaultValue']),
            primary_key: rowValue(column, ['primaryKey']) == true ? 1 : 0,
            auto_increment: rowValue(column, ['autoIncrement']) == true ? 1 : 0,
            is_unique: rowValue(column, ['unique']) == true ? 1 : 0,
            indexed: rowValue(column, ['indexed']) == true ? 1 : 0,
            ordinal_position: rowValue(column, ['ordinalPosition']) == null ? ordinal : rowValue(column, ['ordinalPosition']),
            charset: rowValue(column, ['charset']),
            collation: rowValue(column, ['collation']),
            extra: rowValue(column, ['extra']),
            is_del: 0,
            updated_time: now
        }
        if (old == null) {
            values.created_time = now
            newRows.add(values)
        } else {
            db.table('db_table_column_t').primary('tid').update(values, true)
        }
    }
    if (newRows.size() > 0) {
        metadataAsset.batchInsertTableColumns(newRows)
    }
    return columns.size()
}

var resultRows = []
var esIds = []
var esDocs = []
var affected = 0
var now = nowTime()

for (prepared in preparedItems) {
    var item = prepared.item
    var props = prepared.props
    var datasourceId = prepared.datasourceId
    var tableName = prepared.tableName
    var tableComment = prepared.tableComment
    var tableId = rowValue(item, ['tid', 'tableId', 'id'])
    if (blank(tableId)) {
        tableId = rowValue(props, ['tid', 'tableId', 'id'])
    }

    var existingMaps = existingMapsOf(datasourceId)
    var existing = blank(tableId) ? null : existingMaps.byId[textOf(tableId)]
    if (existing == null) {
        existing = existingMaps.byName[normalizeKey(tableName)]
    }
    if (existing != null) {
        tableId = rowValue(existing, ['tid'])
    }
    if (blank(tableId)) {
        tableId = newId()
    }

    var valueOrExisting = (propKeys, existingKeys) => {
        var value = rowValue(props, propKeys)
        if (value == null && existing != null) {
            value = rowValue(existing, existingKeys)
        }
        return value
    }

    // 组装 db_table_t 主表字段：保持与单表 saveOrUpdate 字段一致。
    var values = {
        tid: tableId,
        datasource_id: datasourceId,
        table_name: tableName,
        table_name_en: tableName,
        table_name_cn: valueOrExisting(['tableNameCn', 'tableComment', 'catalogName'], ['tableNameCn', 'table_name_cn']),
        table_comment: tableComment,
        table_type: valueOrExisting(['tableType'], ['tableType', 'table_type']),
        business_type: valueOrExisting(['businessType'], ['businessType', 'business_type']),
        business_type_reason: valueOrExisting(['businessTypeReason'], ['businessTypeReason', 'business_type_reason']),
        annotated: annotatedFlag,
        record_count: valueOrExisting(['recordCount'], ['recordCount', 'record_count']),
        field_count: valueOrExisting(['fieldCount'], ['fieldCount', 'field_count']),
        storage_size: valueOrExisting(['storageSize'], ['storageSize', 'storage_size']),
        total_size_formatted: valueOrExisting(['totalSizeFormatted', 'storageSize'], ['totalSizeFormatted', 'total_size_formatted']),
        total_size_bytes: valueOrExisting(['totalSizeBytes', 'totalSizeBytes_num'], ['totalSizeBytes', 'total_size_bytes']),
        org_id: valueOrExisting(['orgId'], ['orgId', 'org_id']),
        org_path: valueOrExisting(['orgPath'], ['orgPath', 'org_path']),
        related_directory: valueOrExisting(['relatedDirectory'], ['relatedDirectory', 'related_directory']),
        data_source_type: valueOrExisting(['dataSourceType'], ['dataSourceType', 'data_source_type']),
        source_table_id: tableId,
        source_table_name: tableName,
        manage_unit: rowValue(props, ['manageUnit', 'orgId']),
        asset_desc: rowValue(props, ['assetDesc', 'tableComment']),
        field_governance_config: syncGovernanceBusinessType(existing, valueOrExisting(['businessType'], ['businessType', 'business_type'])),
        tenant_id: tenantId,
        asset_status: 0,
        flow_status: 0,
        flow_order_id: null,
        is_del: 0,
        updated_time: now
    }

    if (!dryRun) {
        if (existing == null) {
            values.created_time = now
            values.reg_time = now
            db.table('db_table_t').primary('tid').insert(values)
        } else if (annotatedFlag == 0) {
            // “暂不处理”仍属于用户已确认的分类决定：保留在未标注列表，
            // 但必须持久化 business_type 和原因，返回上一步时不能再次被物理表名自动分类覆盖。
            // 不重写字段快照，避免暂不处理动作覆盖后续步骤登记的字段配置。
            db.table('db_table_t').primary('tid').update({
                tid: tableId,
                business_type: values.business_type,
                business_type_reason: values.business_type_reason,
                annotated: 0,
                asset_status: 0,
                flow_status: 0,
                updated_time: now
            }, true)
        } else {
            db.table('db_table_t').primary('tid').update(values, true)
        }
        affected = affected + 1
        existingMaps.byId[textOf(tableId)] = values
        existingMaps.byName[normalizeKey(tableName)] = values
        if (annotatedFlag == 1) {
            saveColumns(tableId, rowValue(props, ['columns']), values.tenant_id)
        }

        // 先收集轻量搜索文档，整批主表写完后统一调用一次 ES bulk。
        var doc = {
            tid: tableId,
            id: tableId,
            assetType: 'table',
            dbId: datasourceId,
            datasourceId: datasourceId,
            tableName: tableName,
            tableNameEn: tableName,
            tableNameCn: values.table_name_cn,
            tableComment: tableComment,
            tableType: values.table_type,
            businessType: values.business_type,
            businessTypeReason: values.business_type_reason,
            annotated: annotatedFlag,
            recordCount: values.record_count,
            fieldCount: values.field_count,
            storageSize: values.storage_size,
            orgId: values.org_id,
            orgPath: values.org_path,
            dataSourceType: values.data_source_type,
            assetStatus: 0,
            flowStatus: 0,
            tenantId: tenantId,
            updatedTime: textOf(now)
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
        esIds.add(textOf(tableId))
        esDocs.add(doc)
    }

    resultRows.add({
        tid: tableId,
        id: tableId,
        datasourceId: datasourceId,
        dbId: datasourceId,
        tableName: tableName,
        tableNameEn: tableName,
        tableNameCn: values.table_name_cn,
        tableComment: tableComment,
        tableType: values.table_type,
        businessType: values.business_type,
        businessTypeReason: values.business_type_reason,
        annotated: annotatedFlag,
        fieldCount: values.field_count,
        recordCount: values.record_count
    })
}

var esSynced = dryRun || esDocs.size() == 0
var esError = null
if (!dryRun && esDocs.size() > 0) {
    try {
        // 使用显式 ID 的 bulk index 作为 upsert：历史表可能尚未进入 ES，partial update
        // 会因 _doc 不存在而失败。主表已先成功落库，投影同步必须能创建缺失文档。
        // dataassets 同时承载 app/db/table/catalog 等多类文档，不能用单个表文档反推整个索引 mapping。
        esCommonService.bulkSaveWithIds('dataassets', esIds, esDocs)
        esSynced = true
    } catch (e) {
        esError = e.message
        log.warn('批量标注同步数据表 ES 失败, count: {}, error: {}', esDocs.size(), e.message)
    }
}

if (!dryRun && refreshDatasource) {
    var refreshedDatasourceIds = {}
    for (row in resultRows) {
        var rowDatasourceId = textOf(row.datasourceId)
        if (!blank(rowDatasourceId) && refreshedDatasourceIds[rowDatasourceId] == null) {
            refreshedDatasourceIds[rowDatasourceId] = true
            try {
                body = { tid: rowDatasourceId }
                refreshAssetIndex()
            } catch (e) {
                log.warn('sync datasource ES stats failed, tid: {}, error: {}', rowDatasourceId, e.message)
            }
        }
    }
}

return {
    affected: affected,
    total: tableItems.size(),
    dryRun: dryRun,
    existingQueryCount: existingQueryCount,
    esSynced: esSynced,
    esError: esError,
    refreshDatasource: refreshDatasource,
    rows: resultRows
}
