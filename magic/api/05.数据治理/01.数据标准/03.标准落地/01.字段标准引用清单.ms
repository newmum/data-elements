{
  "properties": {},
  "id": "dwm_standard_landing_list_01",
  "groupId": "dwm_standard_landing_group_01",
  "name": "01.字段标准引用清单",
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{}",
  "description": "只读当前租户已采集字段及 da_metadata_t 标准引用，统计有效已发布标准覆盖率；涉及 db_table_t、db_datasource_t、db_table_column_t、da_metadata_t 等业务表。",
  "script": null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * POST /dwm/standard/landing/list。分页读取当前租户可见字段和标准引用。
 * 支持表、标准、关键词、绑定状态与白名单排序，返回字段映射、实体、数据元和分页信息。
 * 覆盖率按角色可见的全部有效字段计算，页面筛选只影响分页结果。
 */
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
if (scope == null || !['ALL', 'OWNER', 'ORG'].contains(scope.scope)) {
    exit 403, '无权读取数据源'
}
if (scope.scope == 'OWNER' && (scope.userId == null || scope.userId == '')) {
    exit 403, '数据范围身份缺失'
}

var p = body || {}
var page = Math.max(1, (p.pageNum || 1)::int)
var size = Math.min(100, Math.max(1, (p.pageSize || 20)::int))
var keyword = (p.keyword || '').trim()
var fieldKeyword = (p.fieldKeyword || '').trim()
var state = p.scope || 'ALL'
if (keyword.length() > 100 || fieldKeyword.length() > 100 || !['ALL', 'BOUND', 'UNBOUND'].contains(state)) {
    exit 400, '标准关联筛选参数不正确'
}

var column = (alias, name, type) => tableRead.column(alias, name, type)
var active = alias => tableRead.equal(column(alias, 'is_del', 'NUMBER'), 0)
var present = value => tableRead.not(tableRead.isNull(value))
var entityId = column('c', 'table_id', 'TEXT')
var targetId = column('c', 'data_standard_id', 'TEXT')
var tableId = column('t', 'tid', 'TEXT')
var sourceId = column('d', 'tid', 'TEXT')
var standardId = column('m', 'tid', 'TEXT')
var publishStatus = column('m', 'publish_status', 'NUMBER')
var bound = present(tableRead.blankAsNull(tableRead.trim(targetId)))

// 字段、表和数据源均须有效并属于同一租户。标准引用保留缺失或未发布状态。
var query = tableRead.from('db_table_column_t', 'c', tenantId)
query.leftJoin('db_table_t', 't', 'tid', 'table_id', [active('t')])
query.leftJoin('db_datasource_t', 'd', 'tenant_id', 'tenant_id', [
    tableRead.equal(sourceId, column('t', 'datasource_id', 'TEXT')), active('d')
])
query.leftJoin('da_metadata_t', 'm', 'tid', 'data_standard_id', [active('m')])
query.where(tableRead.all([active('c'), present(tableId), present(sourceId)]))
if (scope.scope == 'OWNER') {
    query.where(tableRead.equal(column('d', 'created_by', 'TEXT'), scope.userId))
}
if (scope.scope == 'ORG') {
    query.where(tableRead.in(column('d', 'org_id', 'TEXT'), scope.organizationIds || []))
}

// 在页面筛选前汇总可见范围，失效或未发布的引用只计入已绑定字段。
var totals = query.grouped({
    total_fields: tableRead.count(),
    bound_fields: tableRead.countIf(bound),
    mapped_fields: tableRead.countIf(tableRead.all([present(standardId), tableRead.equal(publishStatus, 1)]))
}, [])[0]
var totalFields = totals.total_fields::long
var mappedFields = totals.mapped_fields::long
var percentage = BigDecimal.ZERO.setScale(1)
if (totalFields > 0) {
    percentage = BigDecimal.valueOf(mappedFields).multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(totalFields), 1, RoundingMode.HALF_UP)
}
var coverage = {
    totalFields: totalFields,
    boundFields: totals.bound_fields,
    mappedFields: mappedFields,
    coverage: percentage
}

var fieldName = column('c', 'column_name', 'TEXT')
var fieldComment = column('c', 'column_comment', 'LOB')
var tableName = column('t', 'table_name', 'TEXT')
var tableNameCn = column('t', 'table_name_cn', 'TEXT')
var tableComment = column('t', 'table_comment', 'LOB')
var metaName = column('m', 'meta_name', 'TEXT')
var metaCode = column('m', 'meta_code', 'TEXT')

// 由数据库判断参数是否为空，保留达梦将空字符串视为 NULL 的行为。
var filterOptionalId = (term, value) => {
    if (value != null) {
        var parameter = tableRead.when(tableRead.all([]), value, null)
        query.where(tableRead.any([tableRead.isNull(parameter), tableRead.equal(term, value)]))
    }
}
filterOptionalId(entityId, p.entityId)
filterOptionalId(targetId, p.targetId)
// 按原字段顺序拼接后查找，允许关键词跨相邻列，保留注释完整内容。
if (fieldKeyword != '') {
    query.where(tableRead.contains(tableRead.concatWs(' ', [fieldName, fieldComment]), fieldKeyword))
}
if (keyword != '') {
    query.where(tableRead.contains(tableRead.concatWs(' ', [
        tableName, tableNameCn, tableComment, fieldName, fieldComment, metaName, metaCode
    ]), keyword))
}
if (state == 'BOUND') {
    query.where(bound)
}
if (state == 'UNBOUND') {
    query.where(tableRead.not(bound))
}

var sortField = tableSort.field(p, {
    field_id: 'field_id', fieldName: 'fieldName', target_id: 'target_id',
    createdTime: 'createdTime', updatedTime: 'updatedTime'
}, 'field_id')
var sortColumns = {
    field_id: tableRead.coalesce([tableRead.blankAsNull(fieldComment), fieldName]),
    fieldName: fieldName,
    target_id: metaName,
    createdTime: column('c', 'created_time', 'TIME'),
    updatedTime: column('c', 'updated_time', 'TIME')
}
var result = query.page({
    field_id: column('c', 'tid', 'TEXT'),
    entity_id: entityId,
    field_name: fieldName,
    display_name: fieldComment,
    data_type: column('c', 'data_type', 'TEXT'),
    length: column('c', 'length', 'NUMBER'),
    target_id: targetId,
    table_name: tableName,
    table_name_cn: tableNameCn,
    table_comment: tableComment,
    source_name: column('d', 'db_name', 'TEXT'),
    standard_id: standardId,
    meta_name: metaName,
    meta_code: metaCode,
    field_type: column('m', 'field_type', 'TEXT'),
    field_length: column('m', 'field_length', 'NUMBER'),
    publish_status: publishStatus
}, sortColumns[sortField], tableSort.direction(p), 'tid', page, size)

// 分页已在数据库完成，按原顺序把本页数据组装为实体、字段映射与数据元。
var entities = []
var items = []
var elements = []
var byId = {}
var standardIds = []
for (row in result.rows) {
    var entity = byId.get(row.entity_id)
    if (entity == null) {
        entity = {
            id: row.entity_id,
            name: row.table_name,
            displayName: row.table_name_cn || row.table_comment || row.table_name,
            sourceName: row.source_name,
            fields: []
        }
        entities.add(entity)
        byId.put(row.entity_id, entity)
    }
    var target = row.target_id == null ? null : (row.target_id + '').trim()
    if (target == '') {
        target = null
    }
    var valid = row.standard_id != null && row.publish_status == 1
    entity.fields.add({
        id: row.field_id, name: row.field_name, displayName: row.display_name || row.field_name,
        type: row.data_type, length: row.length, standardId: target
    })
    items.add({
        id: row.field_id + ':STANDARD', entity_id: row.entity_id, field_id: row.field_id,
        mapping_mode: 'STANDARD', target_id: target,
        status: target == null ? 'UNBOUND' : valid ? 'CONFIRMED' : 'STALE'
    })
    if (row.standard_id != null && !standardIds.contains(row.standard_id)) {
        standardIds.add(row.standard_id)
        elements.add({
            id: row.standard_id, name: row.meta_name, code: row.meta_code,
            fieldType: row.field_type, fieldLength: row.field_length,
            status: row.publish_status == 1 ? 'PUBLISHED' : 'PENDING'
        })
    }
}
return {
    items: items, entities: entities, elements: elements, coverage: coverage,
    total: result.total, pageNum: page, pageSize: size
}
