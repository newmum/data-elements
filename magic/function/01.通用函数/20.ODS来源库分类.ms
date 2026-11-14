{
  "properties": {},
  "id": "d2138de2de28456aa57d9ade2305eed7",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "20.ODS来源库分类",
  "path": "/ods-source-classifier",
  "description": "根据当前租户资源中心 ODS 层和直属分域的有效绑定，构造按来源库标识分组的查询。",
  "parameters": [],
  "options": []
}
================================
import tenantRuntime
import tableRead

/**
 * 构造当前租户的 ODS 来源库分类查询。无需调用参数。
 * 返回 {query, projection, groups}，分组键 source_id 是绑定的数据源标识。
 * 调用方可直接按组读取，或将结果关联到候选数据源查询。
 * 仅构造查询，执行和后续数据范围校验由调用方负责。
 */
var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录'
}

var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
var sourceId = column('ods_bind', 'datasource_id')
var targetType = column('ods_bind', 'target_type')
var layerId = column('ods_layer', 'tid')
var rootId = column('ods_root', 'tid')
var query = tableRead.from('dwm_center_layer_source_t', 'ods_bind', tenantId)
query.leftJoin('sym_dict_t', 'ods_target', 'tid', 'target_id', [
    active('ods_target')
])

// 层绑定直接关联 ODS。分域绑定须关联 ODS 的直属子节点。
query.leftJoin('sym_dict_t', 'ods_layer', 'tenant_id', 'tenant_id', [
    active('ods_layer'),
    tableRead.equal(tableRead.lower(column('ods_layer', 'dict_code')), 'ods'),
    tableRead.any([
        tableRead.all([
            tableRead.equal(targetType, 'layer'),
            tableRead.equal(column('ods_target', 'tid'), layerId)
        ]),
        tableRead.all([
            tableRead.equal(targetType, 'domain'),
            tableRead.equal(column('ods_target', 'parent_id'), layerId)
        ])
    ])
])
query.leftJoin('sym_dict_t', 'ods_root', 'tenant_id', 'tenant_id', [
    active('ods_root'),
    tableRead.equal(rootId, column('ods_layer', 'parent_id')),
    tableRead.equal(column('ods_root', 'parent_id'), '0'),
    tableRead.equal(column('ods_root', 'dict_code'), 'dataSourceType')
])
// 根节点匹配同时证明 ODS 层及绑定目标均在当前租户有效字典树内。
query.where(tableRead.all([
    active('ods_bind'),
    tableRead.not(tableRead.isNull(sourceId)),
    tableRead.not(tableRead.isNull(rootId))
]))
return { query: query, projection: { source_id: sourceId }, groups: [sourceId] }
