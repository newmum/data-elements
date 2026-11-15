{
  "properties": {},
  "id": "e4e9c341f7204025a438da78107217c1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "82.采集表分页",
  "path": "/metadataCatalogTables",
  "description": "采集表分页 GET 与 HEAD 共用过滤、排序及目录关联。",
  "returnType": "Object",
  "parameters": [],
  "options": []
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import java.util.ArrayList
import java.util.LinkedHashSet
import '@/common/metadataCatalogRequest' as readRequest
import '@/common/metadataCatalogResponse' as result
import '@/common/metadataCatalogScope' as filterScope

// 采集表浏览 GET 和 HEAD 共用数据源范围、数据库分页和当前页的目录关联。
var p = readRequest(null, {pageNo: 1, pageSize: 15})
if (p.code != 0) {
    return result(p.code, p.msg)
}
var page = Math.max(1, p.numbers.pageNo)
var size = Math.max(1, Math.min(100, p.numbers.pageSize))
var sources = p.sourceIds
if (sources != null && sources.size() > 1000) {
    return result(500, '单次最多选择 1000 个数据源')
}

try {
    var field = tableSort.field(p, {tableName: 'tableName', fieldCount: 'fieldCount', recordCount: 'recordCount',
        createdTime: 'createdTime', updatedTime: 'updatedTime'}, 'tableName')
    var direction = tableSort.direction({sortDir: p.sortDir == null || p.sortDir.isBlank() ? 'asc' : p.sortDir})
    var tenantId = tenantRuntime.id()
    var scope = dataScope.dataSource()
    var col = (a, n, type) => tableRead.column(a, n, type)
    var active = a => tableRead.equal(col(a, 'is_del', 'NUMBER'), 0)
    var id = col('t', 'tid', 'TEXT')
    var datasource = col('t', 'datasource_id', 'TEXT')
    var tables = tableRead.from('db_table_t', 't', tenantId)
    tables.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
    tables.where(tableRead.all([active('t'), tableRead.not(tableRead.isNull(col('d', 'tid', 'TEXT'))), filterScope('d', scope)]))
    if (p.tableId != null && !p.tableId.isBlank()) {
        tables.where(tableRead.equal(id, p.tableId.trim()))
    }
    if (sources != null) {
        var distinct = new LinkedHashSet()
        for (sourceId in sources) {
            if (sourceId != null && !sourceId.isBlank()) {
                distinct.add(sourceId)
            }
        }
        if (distinct.isEmpty()) {
            return result(0, {rows: [], catalogs: [], total: 0, pageNo: page, pageSize: size})
        }
        tables.where(tableRead.in(datasource, new ArrayList(distinct)))
    }

    var type = col('t', 'table_type', 'TEXT')
    if (['视图', '数据表'].contains(p.kind)) {
        var view = tableRead.any([tableRead.contains(type, 'VIEW'), tableRead.contains(type, '视图')])
        var isView = tableRead.all([tableRead.not(tableRead.isNull(type)), view])
        tables.where(p.kind == '视图' ? isView : tableRead.not(isView))
    }

    var name = col('t', 'table_name', 'TEXT')
    var nameCn = col('t', 'table_name_cn', 'TEXT')
    var comment = col('t', 'table_comment', 'LOB')
    if (p.keyword != null && !p.keyword.isBlank()) {
        var pattern = '%' + p.keyword.trim().toLowerCase() + '%'
        tables.where(tableRead.any([tableRead.like(tableRead.lower(name), pattern),
            tableRead.like(tableRead.lower(nameCn), pattern), tableRead.like(tableRead.lower(comment), pattern)]))
    }
    var fieldCount = col('t', 'field_count', 'NUMBER')
    var recordCount = col('t', 'record_count', 'NUMBER')
    var created = col('t', 'created_time', 'TIME')
    var updated = col('t', 'updated_time', 'TIME')
    var sorts = {tableName: tableRead.coalesce([tableRead.blankAsNull(nameCn), tableRead.blankAsNull(comment), name]),
        fieldCount: fieldCount, recordCount: recordCount, createdTime: created, updatedTime: updated}
    var data = tables.page({tid: id, datasource_id: datasource, table_name: name, table_name_cn: nameCn,
        table_comment: comment, table_type: type, field_count: fieldCount, record_count: recordCount,
        created_time: created, updated_time: updated}, sorts[field], direction, 'tid', page, size)
    var catalogs = []
    if (!data.rows.isEmpty()) {
        var ids = data.rows.map(row => row.tid)
        var linked = tableRead.from('da_catalog_t', 'c', tenantId)
        var table = col('c', 'source_table_id', 'TEXT')
        var catalogId = col('c', 'tid', 'TEXT')
        linked.where(tableRead.all([active('c'), tableRead.in(table, ids)]))
        catalogs = linked.rows({tid: catalogId, catalog_name: col('c', 'catalog_name', 'TEXT'),
            catalog_name_en: col('c', 'catalog_name_en', 'TEXT'), asset_desc: col('c', 'asset_desc', 'LOB'),
            source_table_id: table, db_id: col('c', 'db_id', 'TEXT')},
            [col('c', 'updated_time', 'TIME'), catalogId], ['desc', 'desc'], 10001, true)
        if (catalogs.size() > 10000) {
            return result(500, '目录关联超过 10000 项，请缩小当前页范围')
        }
    }
    return result(0, {rows: data.rows, catalogs: catalogs, total: data.total, pageNo: page, pageSize: size})
} catch (e) {
    return result(500, e.getMessage())
}
