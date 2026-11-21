{
  "id": "nifi_runtime_node_read_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "78.读取NiFi运行节点配置",
  "path": "/nifiruntime",
  "parameters": [
    {"name": "nodeId", "dataType": "Object"},
    {"name": "allNodes", "dataType": "Object"},
    {"name": "consume", "dataType": "Object", "required": true}
  ],
  "description": "供运行采样、流程编译和节点探查共用。可信行业上下文读取指定节点或最多100个启用节点，由服务端回调消费凭据。"
}
================================
import tableRead
import tenantRuntime

// 默认、指定节点及共享采样共用凭据读取。配置只交给服务端回调。
if (consume == null) { return null }
var tenantId = tenantRuntime.id()
var node = tableRead.from('nifi_node_t', 'n', tenantId)
var column = (name, type) => tableRead.column('n', name, type)
var nullableNumber = (name, fallback) => tableRead.coalesce([column(name, 'NUMBER'), fallback])
var updatedTime = column('updated_time', 'TIME')
var fields = {
    tid:column('tid','TEXT'), node_code:column('node_code','TEXT'), base_url:column('base_url','TEXT'),
    node_name:column('node_name','TEXT'), network_code:column('network_code','TEXT'),
    root_process_group_id:column('root_process_group_id','TEXT'), auth_username:column('auth_username','TEXT'),
    auth_password:column('auth_password','TEXT'), insecure_tls:column('insecure_tls','NUMBER'),
    jdbc_driver_locations:column('jdbc_driver_locations','TEXT'), updated_time:updatedTime, enabled:column('enabled','NUMBER')
}
node.where(tableRead.equal(nullableNumber('is_del', 0), 0))
var rows = null
if (allNodes == true) {
    node.where(tableRead.equal(nullableNumber('enabled', 1), 1))
    rows = node.rows(fields, [nullableNumber('is_default', 0), updatedTime, fields.tid], ['desc', 'desc', 'asc'], 101, true)
    if (rows.size() > 100) { throw new IllegalStateException('启用的采样节点超过100个，请调整节点配置') }
    return consume(rows)
}
if (nodeId == null) {
    node.where(tableRead.equal(nullableNumber('enabled', 1), 1))
    rows = node.rows(fields, [nullableNumber('is_default', 0), updatedTime], ['desc', 'desc'], 1, true)
} else {
    node.where(tableRead.equal(fields.tid, nodeId))
    rows = node.rows(fields, 1)
}
return rows.isEmpty() ? null : consume(rows[0])
