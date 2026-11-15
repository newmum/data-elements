{
  "properties": {},
  "id": "a1000000000000000000000000000014",
  "script": null,
  "groupId": "f5cea4fc64d44c7ebd024067f72f923d",
  "name": "03.字段选项",
  "createTime": 1785628800000,
  "updateTime": 1791111501368,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/columns",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"tableId\":\"dq_demo_population_meta\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "POST /dwm/qoption/columns；只读；查询指定登记数据表字段、主键和索引属性；规则表单选项接口。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataScope
import tableRead
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.List
import '@/common/metadataCatalogScope' as filterScope
import '@/common/metadataColumnsScope' as columns

// 先逐 ID 核对当前租户和角色可见的登记表，再按批读取字段。任一表不可见时整批拒绝。
var tenantId = tenantRuntime.id()
var batch = body?.tableIds != null
var submitted = batch ? body.tableIds : [body?.tableId]
if (!(submitted instanceof List) || submitted.isEmpty() || submitted.size() > 50) {
    exit 400, '数据表标识必须是 1 至 50 项的数组'
}
var distinct = new LinkedHashSet()
for (rawId in submitted) {
    var id = ('' + rawId).trim()
    if (id == '' || id.length() > 32) { exit 400, '数据表标识不合法' }
    distinct.add(id)
}
var ids = new ArrayList(distinct)
var scope = dataScope.dataSource()
var col = (alias, name, type) => tableRead.column(alias, name, type)
var id = col('t', 'tid', 'TEXT')
var owner = tableRead.from('db_table_t', 't', tenantId)
owner.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id',
    [tableRead.equal(col('d', 'is_del', 'NUMBER'), 0)])
owner.where(tableRead.all([
    tableRead.equal(col('t', 'is_del', 'NUMBER'), 0),
    tableRead.not(tableRead.isNull(col('d', 'tid', 'TEXT'))),
    filterScope('d', scope), tableRead.in(id, ids)
]))
var visible = new LinkedHashSet(owner.rows({tid: id}, ids.size()).map(row => row.tid))
if (!visible.containsAll(ids)) { exit 403, '部分数据表不存在或不在当前租户和角色范围内' }

// 字段读取再次应用相同数据范围。保留无字段表的空列表和原有单表响应。
var fields = columns(tenantId, scope)
var projection = fields.projection
projection.remove('length')
projection.put('is_unique', col('c', 'is_unique', 'NUMBER'))
projection.put('indexed', col('c', 'indexed', 'NUMBER'))
fields.query.where(tableRead.in(projection.table_id, ids))
var grouped = {}
for (id in ids) { grouped.put(id, []) }
fields.query.forEachOrderedRow(projection, [projection.table_id, projection.ordinal_position, projection.column_name],
    ['asc', 'asc', 'asc'], true, row => {
        var tableId = row.remove('table_id')
        if (row.primary_key == null) { row.remove('primary_key') }
        grouped.get(tableId).add(row)
    })
return batch ? grouped : grouped.get(ids[0])
