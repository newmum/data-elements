{
  "properties": {},
  "id": "datapd_catalog_items_list_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "07.数据目录数据项列表",
  "path": "/catalog-items/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086383506325794816\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "数据目录数据项列表（POST /dst/catalog/catalog-items/list）。读取 da_catalog_item_t，供目录详情、登记和文件目录兼容调用；使用请求参数 catalogId、tid、id。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.json.JSONUtil as jsons
import tenantRuntime
import dataScope
import tableRead
import tableSort
import cn.hutool.core.util.NumberUtil
import java.util.List
import java.util.Map

/**
 * 数据目录数据项列表。POST /dst/catalog/catalog-items/list
 * 接收 catalogId，兼容 tid 和 id。传入 pageNo 或 page 时返回 list 和 total，否则返回有界数组。
 * 按当前租户和来源数据范围核验目录后读取数据项。旧目录没有实际数据项时可读取历史治理快照。
 * 接口只读，适用于目录详情、登记、挂接及文件目录调用。
 */

var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
var rowValue = (row, keys) => {
    for (key in keys) {
        if (row != null && row[key] != null) {
            return row[key]
        }
    }
    return null
}

var catalogId = body == null ? null : body.catalogId
if (blank(catalogId)) {
    catalogId = blank(body?.tid) ? body?.id : body.tid
}
if (blank(catalogId)) {
    exit 400, '目录标识不能为空'
}
var tenantId = text(tenantRuntime.id())
if (blank(tenantId)) {
    exit 401, '无法识别当前租户'
}
var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'OWNER', 'ORG'].contains(scopeMode) || blank(scopeUserId)) {
    exit 403, '无法确定当前数据范围'
}

var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var active = alias => tableRead.equal(tableRead.coalesce([numberColumn(alias, 'is_del'), 0]), 0)
var scopeCondition = alias => scopeMode == 'OWNER'
    ? tableRead.equal(column(alias, 'created_by'), scopeUserId)
    : tableRead.in(column(alias, 'org_id'), scopeOrgIds)

// 绑定来源的目录按有效来源判断可见性。只有没有绑定来源的历史目录才按目录本身判断。
var parentQuery = tableRead.from('da_catalog_t', 'c', tenantId)
var parentId = column('c', 'tid')
parentQuery.where(tableRead.equal(parentId, text(catalogId)))
parentQuery.where(active('c'))
if (scopeMode != 'ALL') {
    parentQuery.leftJoin('db_datasource_t', 'd', 'tid', 'db_id', [
        tableRead.equal(numberColumn('d', 'is_del'), 0)
    ])
    var allowedSource = tableRead.all([
        tableRead.not(tableRead.isNull(column('d', 'tid'))), scopeCondition('d')
    ])
    var unboundCatalog = tableRead.all([
        tableRead.isNull(tableRead.blankAsNull(column('c', 'db_id'))), scopeCondition('c')
    ])
    parentQuery.where(tableRead.any([allowedSource, unboundCatalog]))
}
var parents = parentQuery.rows({config: tableRead.column('c', 'field_governance_config', 'LOB')}, parentId, 'asc', 1)
if (parents.size() == 0) {
    exit 404, '目录不存在或无权读取'
}

var paging = body.pageNo != null || body.page != null
var requestedPage = body.pageNo == null ? body.page : body.pageNo
var requestedSize = body.pageSize == null ? body.size : body.pageSize
var page = requestedPage == null ? 1 : NumberUtil.parseInt(text(requestedPage))
var size = requestedSize == null ? 20 : NumberUtil.parseInt(text(requestedSize))
if (page < 1 || page > 100000 || size < 1 || size > 100) {
    exit 400, '分页参数无效，每页最多100项'
}
var sortFields = {
    colName: 'colName', colEn: 'colEn', colLength: 'colLength', sortNo: 'sortNo',
    createdTime: 'createdTime', updatedTime: 'updatedTime'
}
var sortKey = tableSort.field(body, sortFields, 'sortNo')
var dir = tableSort.direction(body)
var columns = {
    tid: 'tid', catalogId: 'catalog_id', tenantId: 'tenant_id', colName: 'col_name', colEn: 'col_en',
    colType: 'col_type', colComment: 'col_comment', colLength: 'col_length', isPk: 'is_pk',
    isMasking: 'is_masking', isFk: 'is_fk', isNullable: 'is_nullable', dateFormat: 'date_format',
    markLvl: 'mark_lvl', colUnit: 'col_unit', colPrecision: 'col_precision', maskingId: 'masking_id',
    sortNo: 'sort_no', shareType: 'share_type', shareCondition: 'share_condition', isDict: 'is_dict',
    dictName: 'dict_name', colMaxLength: 'col_max_length', defaultValue: 'default_value',
    dataStandardId: 'data_standard_id', qualityRule: 'quality_rule', enableCodeTable: 'enable_code_table',
    codeTableId: 'code_table_id', sourceTableColumnId: 'source_table_column_id',
    targetTableColumnId: 'target_table_column_id'
}
var timeColumns = {createdTime: 'created_time', updatedTime: 'updated_time'}
var order = columns[sortKey] == null ? timeColumns[sortKey] : columns[sortKey]
var rows = []
var total = 0
if (paging) {
    // 数据库先按白名单字段、空值置后和唯一键完成排序，再取当前页。
    var query = tableRead.from('da_catalog_item_t', 'i', tenantId)
    query.where(tableRead.equal(column('i', 'catalog_id'), text(catalogId)))
    query.where(active('i'))
    var projection = {}
    for (key in columns.keySet()) {
        projection[key] = column('i', columns[key])
    }
    var result = query.page(projection, column('i', order), dir, 'tid', page, size)
    rows = result.rows
    total = result.total
} else {
    // 历史数组调用只读取最多 1001 条。保留数据库的排序口径，在有界结果里将空排序值放到末尾。
    var selected = []
    for (name in columns.values()) {
        selected.add(name)
    }
    if (!selected.contains(order)) {
        selected.add(order)
    }
    var legacy = db.lower().table('da_catalog_item_t').columns(selected).where()
        .eq('tenant_id', tenantId).eq('catalog_id', text(catalogId))
        .and(activeItem => activeItem.eq('is_del', 0).or().isNull('is_del'))
        .orderBy(order, dir).orderBy('tid', 'asc').page(1001, 0)
    var legacyRows = legacy.list == null ? [] : legacy.list
    total = legacy.total == null ? legacyRows.size() : legacy.total
    if (total > 1000 || legacyRows.size() > 1000) {
        exit 400, '数据项超过1000项，请使用分页查询'
    }
    var nulls = []
    for (row in legacyRows) {
        if (row[order] == null) {
            nulls.add(row)
        } else {
            rows.add(row)
        }
    }
    rows.addAll(nulls)
}

var items = []
for (row in rows) {
    var item = {}
    for (key in columns.keySet()) {
        item[key] = paging ? row[key] : row[columns[key]]
    }
    items.add(item)
}

// 仅在目录没有任何实际数据项时使用旧治理快照。翻到实际数据项末页以外时仍返回空页。
if (total == 0 && !blank(parents[0].config)) {
    var fields = null
    try {
        fields = jsons.parseObj(parents[0].config).get('fields')
    } catch (e) {
        log.warn('无法解析目录治理快照，目录标识 {}', catalogId)
    }
    if (fields instanceof List) {
        if (fields.size() > 1000) {
            exit 400, '历史配置超过1000项，请分批整理'
        }
        var sortNo = 0
        for (field in fields) {
            if (!(field instanceof Map)) {
                continue
            }
            sortNo = sortNo + 1
            items.add({
                catalogId: catalogId,
                tid: catalogId + ':' + sortNo,
                colName: rowValue(field, ['columnComment', 'colName', 'catalogItemName']),
                colEn: rowValue(field, ['columnName', 'colEn', 'catalogItemEn']),
                colType: rowValue(field, ['columnType', 'colType', 'dataType']),
                colLength: rowValue(field, ['length', 'columnLength']),
                colComment: rowValue(field, ['columnComment', 'colName']),
                isPk: rowValue(field, ['primaryKey', 'isPk']) ? '1' : '0',
                isNullable: rowValue(field, ['nullable', 'isNullable']) == false ? '0' : '1',
                sourceTableColumnId: rowValue(field, ['tid', 'sourceTableColumnId']),
                sortNo: sortNo,
                enableCodeTable: 0
            })
        }
        items = tableSort.rows(items, body, sortFields, 'sortNo', 'tid')
        total = items.size()
        if (paging) {
            var start = Math.min((page - 1) * size, total)
            items = items.subList(start, Math.min(start + size, total))
        }
    }
}
return paging ? {list: items, total: total} : items
