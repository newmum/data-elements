{
  "id": "collect_order_v1",
  "groupId": "collection_api_group_v1",
  "name": "07.查询登记审查顺序",
  "path": "/order",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "OBJECT_MAP"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "校验当前来源的所选表，返回与持久化排序一致的当前页表名。"
}
================================
import '@/collect/response' as respond
import '@/collect/scope' as visibleSources
import '@/collect/tables' as columns
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import java.lang.Boolean
import java.lang.Integer
import java.util.HashSet
import java.util.List
import java.util.Locale
import request
import tableRead
import tenantRuntime

// POST /dst/collect/order。完整来源在库内分页，精确清单以有界游标逐名校验，避免逐表 SQL。
return respond(() => {
    var jsonError = request.get().getAttribute(MagicBootConfiguration.MAGIC_COLLECTION_ORDER_JSON_ERROR)
    if (jsonError != null) {
        throw jsonError
    }
    var sourceId = body?.dbId == null ? '' : (body.dbId + '').trim()
    if (!sourceId.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    var tenantId = tenantRuntime.id()
    if (visibleSources(tenantId, [sourceId]).isEmpty()) {
        throw new IllegalArgumentException('数据源不存在或无访问权限')
    }
    var page = body.pageNo == null ? 1 : Integer.parseInt((body.pageNo + '').trim())
    var size = body.pageSize == null ? 20 : Integer.parseInt((body.pageSize + '').trim())
    if (page < 1 || page > 100000 || size < 1 || size > 100) {
        throw new IllegalArgumentException('分页参数无效，每页最多100张表')
    }
    var rawField = body.sortField == null ? '' : (body.sortField + '').trim()
    var rawDirection = body.sortDir == null ? '' : (body.sortDir + '').trim().toLowerCase(Locale.ROOT)
    var key = rawField.isBlank() ? 'tableName' : rawField
    var direction = rawDirection.isBlank() ? 'asc' : rawDirection
    var original = columns('t')
    var query = tableRead.from('db_table_t', 't', tenantId)
    query.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
    query.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), sourceId))
    query.where(tableRead.not(tableRead.isNull(tableRead.blankAsNull(tableRead.trim(original.table_name)))))
    var mark = tableRead.coalesce([tableRead.column('t', 'annotated', 'NUMBER'), 0])
    var review = query.ranked('r', original, [tableRead.lower(original.table_name)],
        [mark, original.updated_time, original.tid], ['desc', 'desc', 'desc'], true)

    var entry = columns('r')
    var title = tableRead.coalesce([tableRead.blankAsNull(entry.table_name_cn),
        tableRead.blankAsNull(entry.table_comment), entry.table_name])
    var sorts = {tableName: entry.table_name, name: title,
        fieldCount: entry.field_count, createdTime: entry.created_time, updatedTime: entry.updated_time}
    if (!sorts.containsKey(key)) {
        throw new IllegalArgumentException('不支持的排序字段：' + key)
    }
    if (!['asc', 'desc'].contains(direction)) {
        throw new IllegalArgumentException('排序方向只能是 asc 或 desc')
    }

    var projection = {table_name: entry.table_name}
    var start = ((page - 1)::long(0)) * size
    var order = [sorts[key], entry.tid]
    var directions = [direction, 'asc']
    if (body.allTables instanceof Boolean && body.allTables) {
        if (body.containsKey('names')) {
            throw new IllegalArgumentException('完整范围与筛选范围不能同时指定')
        }
        // 计数不物化分组代表行，只有实际窗口执行排序和去重。
        var total = query.grouped({total: tableRead.countDistinct(tableRead.lower(original.table_name))}, [])[0].total
        var rows = start < total ? review.rowsAtOffset(projection, order, directions, start, size, false) : []
        return {keys: rows.map(row => (row.table_name + '').trim()), total: total}
    }
    if (!(body.names instanceof List)) {
        throw new IllegalArgumentException('请选择有效的数据表审查范围')
    }
    var unmatched = new HashSet()
    for (input in body.names) {
        var name = input == null ? '' : (input + '').trim()
        if (name.isBlank() || name.length() > 256 || !unmatched.add(name)) {
            throw new IllegalArgumentException('数据表名称为空或重复')
        }
    }
    if (unmatched.isEmpty()) {
        return {keys: [], total: 0}
    }
    var end = start + size
    var result = []
    var progress = {matched: 0L}
    review.withTimeoutSeconds(20).forEachOrderedRow(projection, order, directions, false, row => {
        if (unmatched.remove(row.table_name)) {
            var position = progress.matched
            progress.matched = position + 1
            if (position >= start && position < end) {
                result.add(row.table_name)
            }
        }
    })
    if (!unmatched.isEmpty()) {
        throw new IllegalArgumentException('审查范围包含不存在或无权访问的数据表，请刷新清单')
    }
    return {keys: result, total: body.names.size()}
})
