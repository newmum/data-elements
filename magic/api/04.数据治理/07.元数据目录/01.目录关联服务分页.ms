{
  "properties": {},
  "id": "7542b242144845b287c82950a1a4b87d",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "01.目录关联服务分页",
  "path": "/services",
  "method": "GET",
  "description": "校验目录与角色范围后，按服务标识分页读取关联服务。",
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
  "paths": []
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import '@/common/metadataCatalogRequest' as readRequest
import '@/common/metadataCatalogResponse' as result
import '@/common/metadataCatalogScope' as filterScope

// GET /dwm/catalog/services。校验目录与角色范围后，按服务标识分页读取关联服务。

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
var parentId = col('c', 'tid', 'TEXT')
var parent = tableRead.from('da_catalog_t', 'c', tenantId)
parent.where(tableRead.all([active('c'), tableRead.equal(parentId, id), filterScope('c', scope)]))
if (parent.rows({tid: parentId}, 2).size() != 1) {
    return result(500, '目录不存在或无访问权限')
}

try {
    var field = tableSort.field(p, {assetName: 'assetName', count: 'count', updateTime: 'updateTime'}, 'assetName')
    var direction = tableSort.direction({sortDir: p.sortDir == null || p.sortDir.isBlank() ? 'asc' : p.sortDir})
    var parameter = tableRead.from('api_param_t', 'p', tenantId)
    var ownerId = col('o', 'tid', 'TEXT')
    var serviceId = col('p', 'api_id', 'TEXT')
    parameter.leftJoin('api_info_t', 'o', 'tid', 'api_id', [active('o')])
    parameter.where(tableRead.all([active('p'), tableRead.not(tableRead.isNull(ownerId)),
        tableRead.equal(col('o', 'catalog_id', 'TEXT'), id)]))

    // 参数计数按当前目录一次聚合，只参与分页查询，计数 SQL 不重复聚合。
    var services = tableRead.from('api_info_t', 'a', tenantId)
    services.where(tableRead.all([active('a'), tableRead.equal(col('a', 'catalog_id', 'TEXT'), id), filterScope('a', scope)]))
    services.leftJoinGroupedProjection(parameter, 'pcount', 'api_id', 'tid',
        {api_id: serviceId, parameter_count: tableRead.count()}, [serviceId])
    var quantity = tableRead.coalesce([col('pcount', 'parameter_count', 'NUMBER'), 0])
    var name = col('a', 'service_name', 'TEXT')
    var updated = col('a', 'updated_time', 'TIME')
    var columns = {assetName: name, count: quantity, updateTime: updated}
    var data = services.page({id: col('a', 'tid', 'TEXT'), assetName: name, count: quantity, updateTime: updated},
        columns[field], direction, 'tid', page, size)
    return result(0, {list: data.rows, total: data.total})
} catch (e) {
    return result(500, e.getMessage())
}
