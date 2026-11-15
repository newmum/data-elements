{
  "properties": {},
  "id": "dwm_center_datasource_options_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "08.资源中心关联数据源选项",
  "createTime": 1785067200000,
  "updateTime": 1785067200000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/datasource-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [\n    {\n      \"label\": \"出入境人员动管系统数据库\",\n      \"value\": \"4b5d9992d89744b9b514f8905dc27998\",\n      \"dbType\": \"mysql\",\n      \"nodeName\": \"业务库\"\n    }\n  ],\n  \"total\": 1\n}",
  "description": "为当前租户的分层和分域返回数据源选项。按更新时间等原有排序取得最新 500 条有效候选，keyword 可按名称、库类型或节点名称做字面搜索，最多返回 100 项及返回数量。仅返回展示字段。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import tableRead

/**
 * 资源中心分层、分域的数据源候选。POST /dwm/center/datasource-options
 * keyword 按名称、库类型和节点名称做不区分大小写的字面检索。
 * 在当前租户最近的 500 条有效候选中搜索，返回最多 100 项及实际返回数量。
 * 只读展示所需字段，绑定动作由节点保存接口完成。
 */
var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') {
    exit 401, '无法识别当前租户'
}
var keyword = body?.keyword == null ? '' : (body.keyword + '').trim().toLowerCase()

// 排除验证数据后按原有数据库顺序取候选窗口，再匹配名称、类型和字典标签。
var validationPrefix = 'validation\\_%'
var candidates = db.camel().table('db_datasource_t')
    .columns('tid', 'db_name', 'db_type', 'show_connect', 'connection_status', 'node_id').where()
    .eq('tenant_id', tenantId).eq('is_del', 0)
    .and(filter => filter.isNull('tid').or().notLike('tid', validationPrefix))
    .and(filter => filter.isNull('db_name').or().notLike('db_name', validationPrefix))
    .orderBy('updated_time', 'desc').orderBy('created_time', 'desc').orderBy('db_name', 'asc')
    .page(500, 0)
var rows = candidates.list == null ? [] : candidates.list

// 只批量读取当前窗口需要的有效节点。源表 node_id 与字典 tid 在历史库可能使用不同排序规则。
var nodeIds = []
var seenNodeIds = {}
for (row in rows) {
    if (row.nodeId != null && seenNodeIds[row.nodeId] == null) {
        seenNodeIds[row.nodeId] = true
        nodeIds.add(row.nodeId)
    }
}
var nodeNameById = {}
if (nodeIds.size() > 0) {
    var nodes = tableRead.from('sym_dict_t', 'c', tenantId)
    var nodeId = tableRead.column('c', 'tid', 'TEXT')
    nodes.leftJoin('sym_dict_t', 'p', 'tid', 'parent_id', [
        tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0)
    ])
    nodes.where(tableRead.equal(tableRead.column('c', 'is_del', 'NUMBER'), 0))
    nodes.where(tableRead.equal(tableRead.column('p', 'dict_code', 'TEXT'), 'nodeType'))
    nodes.where(tableRead.in(nodeId, nodeIds))
    var labels = nodes.rows({id: nodeId, name: tableRead.column('c', 'dict_name', 'TEXT')}, nodeId, 'asc', nodeIds.size())
    for (label in labels) {
        nodeNameById[label.id] = label.name
    }
}
var list = []
for (row in rows) {
    var nodeName = nodeNameById[row.nodeId + '']
    if (keyword != '') {
        var hitText = ((row.dbName == null ? '' : row.dbName) + ' ' +
            (row.dbType == null ? '' : row.dbType) + ' ' +
            (nodeName == null ? '' : nodeName)).toLowerCase()
        if (!hitText.contains(keyword)) {
            continue
        }
    }
    list.add({
        label: row.dbName,
        value: row.tid,
        tid: row.tid,
        dbName: row.dbName,
        dbType: row.dbType,
        nodeId: row.nodeId,
        nodeName: nodeName,
        showConnect: row.showConnect,
        connectionStatus: row.connectionStatus
    })
    if (list.size() >= 100) {
        break
    }
}

return { list: list, total: list.size() }
