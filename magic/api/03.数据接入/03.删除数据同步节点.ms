{
  "properties": {},
  "id": "ods_nifi_node_delete_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "03.删除数据同步节点",
  "path": "/nifi-node/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"tid\": \"2087600000000000002\"\n}",
  "responseBody": "{\n  \"success\": true\n}",
  "description": "删除数据同步节点（POST /ods/nifi-node/delete）。仅逻辑删除当前租户的非默认节点；使用请求参数 tid；涉及 nifi_node_t 等业务表。",
  "script": null
}
================================
// 接口：03.删除数据同步节点
// 路由：POST /ods/nifi-node/delete
// 业务说明：删除数据同步节点（POST /ods/nifi-node/delete）。仅逻辑删除当前租户的非默认节点；使用请求参数 tid；涉及 nifi_node_t 等业务表。
import tenantRuntime

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var tid = text(body?.tid).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tid == '') {
    exit 400, '节点标识不能为空'
}
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再维护节点'
}
// 以下从 nifi_node_t 业务表读取或维护当前操作涉及的记录。
var node = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
if (node == null) {
    exit 404, '数据同步节点不存在或已删除'
}
var isDefault = node.isDefault == null ? node.is_default : node.isDefault
if (text(isDefault) == '1' || isDefault == true) {
    exit 400, '默认节点不能删除，请先设置其他节点为默认节点'
}
db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).update({ is_del: 1, updated_time: new Date() })
return { success: true }
