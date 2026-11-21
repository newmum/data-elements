{
  "id": "collect_snapshot_v1",
  "groupId": "collection_api_group_v1",
  "name": "06.查询待标注表快照",
  "path": "/snapshot",
  "method": "GET",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "读取所选来源的登记审查快照，在任务和租户范围内分页。"
}
================================
import '@/collect/deleted' as lastChange
import '@/collect/historyargs' as historyArgs
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import tableRead
import tenantRuntime

// GET /dst/collect/snapshot。读取所选来源的登记审查快照，在任务和租户范围内分页。

return respond(() => {
    var args = historyArgs(100, true)
    if (args.error != null) {
        return args.error
    }
    if (!args.source.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    var tenantId = tenantRuntime.id()
    if (visibleSources(tenantId, [args.source]).isEmpty()) {
        throw new IllegalArgumentException('数据源不存在或无访问权限')
    }

    var fields = {}
    for (column in ['tid', 'datasource_id', 'table_name', 'table_name_en', 'table_name_cn', 'table_type',
            'business_type', 'storage_size', 'total_size_formatted']) {
        fields[column] = tableRead.column('t', column, 'TEXT')
    }
    for (column in ['table_comment', 'business_type_reason']) {
        fields[column] = tableRead.column('t', column, 'LOB')
    }
    for (column in ['record_count', 'field_count', 'total_size_bytes']) {
        fields[column] = tableRead.column('t', column, 'NUMBER')
    }
    var created = tableRead.column('t', 'created_time', 'TIME')
    var updated = tableRead.column('t', 'updated_time', 'TIME')
    var title = tableRead.coalesce([tableRead.blankAsNull(fields.table_name_cn), fields.table_name])
    var sorts = {name: fields.table_name, tableName: fields.table_name, tableNameCn: title,
        fieldCount: fields.field_count, recordCount: fields.record_count,
        createdAt: created, createdTime: created, updatedAt: updated, updatedTime: updated}
    var key = args.sortField == '' ? 'tableName' : args.sortField
    var direction = args.sortDir == '' ? 'asc' : args.sortDir
    if (!sorts.containsKey(key)) {
        throw new IllegalArgumentException('不支持的排序字段：' + key)
    }
    if (!['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序方向只能是 asc 或 desc')
    }

    var active = tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0)
    var owned = tableRead.equal(fields.datasource_id, args.source)
    var annotated = tableRead.column('t', 'annotated', 'NUMBER')
    var snapshot = tableRead.from('db_table_t', 't', tenantId)
    snapshot.where(active)
    snapshot.where(owned)
    snapshot.where(tableRead.any([tableRead.equal(annotated, 0), tableRead.isNull(annotated)]))

    // 用历史差异作为一次绑定的排除关系，避免把大量表名变为 SQL 参数或逐表补查。
    var nameField = tableRead.jsonStringField('table_name', 'tableName', 255)
    var missing = tableRead.fromJsonArray(lastChange(tenantId, args.source, true), 'm', tenantId, [nameField])
    var missingName = tableRead.column('m', 'table_name', 'TEXT')
    var comparableName = term => tableRead.fixedWidthText(tableRead.lower(tableRead.trim(term)), 255)
    snapshot.whereNoMatch(missing, tableRead.equal(comparableName(missingName), comparableName(fields.table_name)))
    if (args.keyword != '') {
        var match = '%' + args.keyword + '%'
        var notes = tableRead.lower(tableRead.coalesce([fields.table_comment, fields.table_name_cn, '']))
        snapshot.where(tableRead.any([tableRead.like(tableRead.lower(fields.table_name), match), tableRead.like(notes, match)]))
    }
    if (args.businessType != '' && args.businessType != '全部') {
        snapshot.where(tableRead.equal(fields.business_type, args.businessType))
    }
    var start = args.offset == null ? ((args.pageNo - 1)::long(0)) * args.pageSize : Math.max(0, args.offset)
    var page = snapshot.pageAtOffset(fields, sorts[key], direction, 'tid', start, args.pageSize)

    // 物理总数中的已标注表沿用来源总数，独立于当页关键词和业务类型。
    var registered = tableRead.from('db_table_t', 't', tenantId)
    registered.where(active)
    registered.where(owned)
    registered.where(tableRead.equal(annotated, 1))
    var marked = registered.grouped({total: tableRead.count(fields.tid)}, [])[0].total
    var first = values => {
        for (value in values) {
            if (value != null && (value + '').trim() != '') {
                return (value + '').trim()
            }
        }
        return ''
    }
    var number = value => value == null ? 0L : (value + '')::long(0)
    var rows = page.rows.map(row => {
        var tableName = first([row.table_name])
        var comment = first([row.table_comment, row.table_name_cn, row.table_name])
        return {
            tid: row.tid, tableId: row.tid, dbId: row.datasource_id, datasourceId: row.datasource_id,
            tableName: tableName, tableNameEn: first([row.table_name_en, row.table_name]), sourceTableName: tableName,
            physicalTableComment: comment, savedResourceName: comment,
            tableNameCn: first([row.table_name_cn, row.table_comment, row.table_name]), tableComment: comment,
            tableType: first([row.table_type]), businessType: first([row.business_type]),
            businessTypeReason: first([row.business_type_reason]), recordCount: number(row.record_count),
            fieldCount: number(row.field_count), storageSize: first([row.storage_size, row.total_size_formatted]),
            totalSizeFormatted: first([row.total_size_formatted, row.storage_size]), totalSizeBytes: number(row.total_size_bytes),
            annotated: false, registered: false
        }
    })
    return {pageNo: args.pageNo, pageSize: args.pageSize, offset: Math.min(start, page.total),
        total: page.total, unmarkedTotal: page.total, physicalTotal: page.total + marked, rows: rows, list: rows}
})
