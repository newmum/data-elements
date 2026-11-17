{
  "id": "collect_fn_saved_v1",
  "groupId": "collection_fn_group_v1",
  "name": "09.读取已保存采集表",
  "path": "/saved",
  "parameters": [],
  "description": "按大小写无关的表名称去重后搜索、排序并分页。"
}
================================
import '@/collect/pageargs' as pageArgs
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import '@/collect/tablepage' as tablePage
import '@/collect/tables' as columns
import tableRead
import tenantRuntime

// /collect/saved 供已保存表清单的 GET 和 HEAD 使用。排名和筛选均在数据库内完成。
return respond(() => {
    var args = pageArgs()
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
    var source = tableRead.from('db_table_t', 't', tenantId)
    source.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    source.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), args.source))
    var raw = columns('t')
    var annotated = tableRead.coalesce([tableRead.column('t', 'annotated', 'NUMBER'), 0])
    var saved = source.ranked('s', raw, [tableRead.lower(raw.table_name)],
        [annotated, raw.updated_time, raw.tid], ['desc', 'desc', 'desc'], true)
    var projection = columns('s')
    var name = tableRead.lower(projection.table_name)
    var title = tableRead.coalesce([tableRead.blankAsNull(projection.table_name_cn),
        tableRead.blankAsNull(projection.table_comment), projection.table_name])
    var sorts = {tableName: name, name: title, tableNameCn: title, fieldCount: projection.field_count,
        createdAt: projection.created_time, updatedAt: projection.updated_time}
    var key = args.sortField == '' ? 'tableName' : args.sortField
    var direction = args.sortDir == '' ? 'asc' : args.sortDir
    if (!sorts.containsKey(key)) {
        throw new IllegalArgumentException('不支持的排序字段：' + key)
    }
    if (!['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序方向只能是 asc 或 desc')
    }
    if (args.keyword != '') {
        // 只搜索去重后保留的表头，沿用旧接口的 LIKE 通配语义。
        var match = '%' + args.keyword + '%'
        saved.where(tableRead.any([tableRead.like(name, match),
            tableRead.like(tableRead.lower(tableRead.coalesce([projection.table_name_cn, ''])), match),
            tableRead.like(tableRead.lower(tableRead.coalesce([projection.table_comment, ''])), match)]))
    }
    return tablePage(saved.page(projection, sorts[key], direction, 'tid', args.pageNo, args.pageSize), args)
})
