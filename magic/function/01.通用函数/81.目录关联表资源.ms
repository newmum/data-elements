{
  "properties": {},
  "id": "3ff834baaa2e4c028ac0159f903d4eea",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "81.目录关联表资源",
  "path": "/metadataCatalogResources",
  "description": "目录关联资源 GET 与 HEAD 共用目标表关联和权限。",
  "returnType": "Object",
  "parameters": [],
  "options": []
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import '@/common/metadataCatalogRequest' as readRequest
import '@/common/metadataCatalogResponse' as result
import '@/common/metadataCatalogScope' as filterScope

// 目录表资源的 GET 和 HEAD 共用目标表关联及授权；以目录主档中的目标表为准。
var p = readRequest('catalogId', {pageNo: 1, pageSize: 20})
if (p.code != 0) {
    return result(p.code, p.msg)
}
var page = p.numbers.pageNo
var size = p.numbers.pageSize
var id = p.identity
if (id.isBlank() || page < 1 || page > 100000 || size < 1 || size > 100) {
    return result(500, '目录和分页参数无效')
}
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
var col = (a, n, type) => tableRead.column(a, n, type)
var active = a => tableRead.equal(col(a, 'is_del', 'NUMBER'), 0)
var parent = tableRead.from('da_catalog_t', 'c', tenantId)
parent.where(tableRead.all([active('c'), tableRead.equal(col('c', 'tid', 'TEXT'), id), filterScope('c', scope)]))
var name = col('t', 'table_name', 'TEXT')
var tableCn = tableRead.coalesce([tableRead.blankAsNull(col('t', 'table_name_cn', 'TEXT')), name])
var sorts = {tableName: name, tableNameCn: tableCn, fieldCount: col('t', 'field_count', 'NUMBER'),
    recordCount: col('t', 'record_count', 'NUMBER'), createdTime: col('t', 'created_time', 'TIME'),
    updatedTime: col('t', 'updated_time', 'TIME')}
try {
    var allowed = {tableName: 'tableName', tableNameCn: 'tableNameCn', fieldCount: 'fieldCount',
        recordCount: 'recordCount', createdTime: 'createdTime', updatedTime: 'updatedTime'}
    var order = tableSort.field(p, allowed, 'tableName')
    var direction = tableSort.direction({sortDir: p.sortDir == null || p.sortDir.isBlank() ? 'asc' : p.sortDir})
    var parents = parent.rows({target_table_id: col('c', 'target_table_id', 'TEXT')}, 2)
    if (parents.size() != 1) {
        return result(500, '目录不存在或无访问权限')
    }
    var target = parents[0].target_table_id
    if (target == null || target.isBlank()) {
        return result(0, {list: [], total: 0})
    }

    // 校验目标表、数据源和可见范围；应用名称缺失时仍保留资源行。
    var tables = tableRead.from('db_table_t', 't', tenantId)
    tables.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id', [active('d')])
    tables.leftJoinFrom('d', 'sym_application_t', 'a', 'tid', 'app_id', [active('a')])
    var tableId = col('t', 'tid', 'TEXT')
    var sourceId = col('d', 'tid', 'TEXT')
    tables.where(tableRead.all([active('t'), tableRead.equal(tableId, target),
        tableRead.not(tableRead.isNull(sourceId)), filterScope('d', scope)]))
    var rows = tables.rows({
        tableId: tableId, tableName: name, tableNameCn: tableCn, dbId: sourceId,
        dbName: col('d', 'db_name', 'TEXT'), dbType: col('d', 'db_type', 'TEXT'),
        appId: col('d', 'app_id', 'TEXT'), appName: col('a', 'app_name', 'TEXT'),
        fieldCount: sorts.fieldCount, recordCount: sorts.recordCount, createdTime: sorts.createdTime,
        updatedTime: sorts.updatedTime, relaType: tableRead.when(tableRead.all([]), '0', null)
    }, [sorts[order], tableId], [direction, 'asc'], 2)
    if (rows.size() != 1) {
        return result(500, '关联资源不存在或无访问权限，请刷新目录')
    }
    return result(0, {list: page == 1 ? rows : [], total: 1})
} catch (e) {
    return result(500, e.getMessage())
}
