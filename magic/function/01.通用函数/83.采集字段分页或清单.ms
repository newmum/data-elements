{
  "properties": {},
  "id": "aa4d85f493a64aff8d75eb0ee5693cfe",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "83.采集字段分页或清单",
  "path": "/metadataCatalogColumns",
  "description": "采集字段清单和分页的 GET 与 HEAD 共用两种返回契约。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "paginated",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import '@/common/metadataCatalogRequest' as readRequest
import '@/common/metadataCatalogResponse' as result
import '@/common/metadataColumnsScope' as fields

// 字段清单与分页的 GET 和 HEAD 共用当前表可见范围和数据库排序。
// 完整清单在第 1001 个字段拒绝，分页最多 100 个字段。
var p = readRequest('tableId', paginated ? {page: 1, size: 20} : {})
if (p.code != 0) {
    return result(p.code, p.msg)
}
try {
    var allowed = {column_name: 'column_name', length: 'length', updated_time: 'updated_time',
        ordinal_position: 'ordinal_position'}
    var field = tableSort.field(p, allowed, 'ordinal_position')
    var direction = tableSort.direction({sortDir: p.sortDir == null || p.sortDir.isBlank() ? 'asc' : p.sortDir})
    var base = fields(tenantRuntime.id(), dataScope.dataSource())
    var projections = base.projection
    base.query.where(tableRead.equal(projections.table_id, p.identity))
    var sort = field == 'updated_time' ? tableRead.column('c', 'updated_time', 'TIME') : projections[field]
    if (paginated) {
        var page = Math.max(1, p.numbers.page)
        var size = Math.min(100, Math.max(1, p.numbers.size))
        var data = base.query.page(projections, sort, direction, 'tid', page, size)
        return result(0, {list: data.rows, total: data.total, page: page, size: size})
    }
    projections.put('is_unique', tableRead.column('c', 'is_unique', 'NUMBER'))
    projections.put('indexed', tableRead.column('c', 'indexed', 'NUMBER'))
    var rows = base.query.rows(projections, [sort, projections.tid], [direction, 'asc'], 1001)
    if (rows.size() > 1000) {
        return result(500, '字段超过 1000 项，请使用字段分页查询')
    }
    return result(0, rows)
} catch (e) {
    return result(500, e.getMessage())
}
