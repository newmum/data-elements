{
  "id": "collect_fn_changes_v1",
  "groupId": "collection_fn_group_v1",
  "name": "13.分页读取采集差异",
  "path": "/changes",
  "parameters": [],
  "description": "分页读取最近采集任务的缺失登记表。"
}
================================
import '@/collect/deleted' as lastChange
import '@/collect/historyargs' as historyArgs
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import tableRead
import tenantRuntime

// /collect/changes 供同址 GET 和 HEAD 使用。历史任务文档在数据库展开、过滤和分页。
return respond(() => {
    var args = historyArgs(20, false)
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
    var textColumn = name => tableRead.column('c', name, 'TEXT')
    var numColumn = name => tableRead.column('c', name, 'NUMBER')
    var fields = {
        tid: textColumn('tid'), table_name: textColumn('table_name'), table_name_cn: textColumn('table_name_cn'),
        table_comment: textColumn('table_comment'), table_type: textColumn('table_type'),
        field_count: numColumn('field_count'), record_count: numColumn('record_count')
    }
    var sorts = {tableName: fields.table_name, name: fields.table_name,
        tableNameCn: tableRead.coalesce([fields.table_name_cn, fields.table_name]),
        fieldCount: fields.field_count, recordCount: fields.record_count}
    var key = args.sortField == '' ? 'tableName' : args.sortField
    var direction = args.sortDir == '' ? 'asc' : args.sortDir
    if (!sorts.containsKey(key)) {
        throw new IllegalArgumentException('不支持的排序字段：' + key)
    }
    if (!['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序方向只能是 asc 或 desc')
    }

    var declared = [
        tableRead.jsonStringField('tid', 'tid', 64), tableRead.jsonStringField('table_name', 'tableName', 255),
        tableRead.jsonStringField('table_name_cn', 'tableNameCn', 1000),
        tableRead.jsonStringField('table_comment', 'tableComment', 1000),
        tableRead.jsonStringField('table_type', 'tableType', 30),
        tableRead.jsonLongField('field_count', 'fieldCount'), tableRead.jsonLongField('record_count', 'recordCount')
    ]
    var changes = tableRead.fromJsonArray(lastChange(tenantId, args.source, false), 'c', tenantId, declared)
    if (args.keyword != '') {
        var match = '%' + args.keyword + '%'
        changes.where(tableRead.any([tableRead.like(tableRead.lower(fields.table_name), match),
            tableRead.like(tableRead.lower(fields.table_name_cn), match)]))
    }
    var page = changes.page(fields, sorts[key], direction, 'tid', args.pageNo, args.pageSize)
    var first = values => {
        for (value in values) {
            if (value != null && (value + '').trim() != '') {
                return (value + '').trim()
            }
        }
        return ''
    }
    var number = value => value == null ? 0L : (value + '')::long(0)
    var rows = page.rows.map(row => ({
        tid: first([row.tid]), tableName: first([row.table_name]),
        tableNameCn: first([row.table_name_cn, row.table_comment, row.table_name]),
        tableType: first([row.table_type]), fieldCount: number(row.field_count),
        recordCount: number(row.record_count), createdAt: null, updatedAt: null
    }))
    return {pageNo: args.pageNo, pageSize: args.pageSize, total: page.total, rows: rows, list: rows}
})
