{
  "properties": {},
  "id": "ods_nifi_node_save_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "02.保存数据同步节点",
  "path": "/nifi-node/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"nodeName\": \"数据同步主节点\",\n  \"nodeCode\": \"primary-nifi\",\n  \"baseUrl\": \"https://nifi.example.com:8443\"\n}",
  "responseBody": "{\n  \"tid\": \"2087600000000000001\"\n}",
  "description": "保存数据同步节点（POST /ods/nifi-node/saveOrUpdate）。仅维护当前租户节点；每个所属网络保留一个默认节点，默认节点可以停用；使用请求参数 tid、nodeName、nodeCode、baseUrl、networkCode、authUsername、authPassword、insecureTls；涉及 sym_dict_t、nifi_node_t 等业务表。",
  "script": null
}
================================
// 接口：02.保存数据同步节点
// 路由：POST /ods/nifi-node/saveOrUpdate
// 业务说明：保存数据同步节点（POST /ods/nifi-node/saveOrUpdate）。仅维护当前租户节点；每个所属网络保留一个默认节点，默认节点可以停用；使用请求参数 tid、nodeName、nodeCode、baseUrl、networkCode、authUsername、authPassword、insecureTls；涉及 sym_dict_t、nifi_node_t 等业务表。
import cn.hutool.core.util.IdUtil
import tenantRuntime

var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var tid = text(body?.tid).trim()
var nodeName = text(body?.nodeName).trim()
var nodeCode = text(body?.nodeCode).trim()
var baseUrl = text(body?.baseUrl).trim().replaceAll('/+$', '')
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (blank(nodeName) || blank(nodeCode) || blank(baseUrl)) {
    exit 400, '节点名称、节点编码和 NiFi 地址不能为空'
}
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再维护节点'
}
var networkCode = text(body?.networkCode).trim()
var authUsername = text(body?.authUsername).trim()
var authPassword = text(body?.authPassword).trim()
var insecureTls = body?.insecureTls == false || text(body?.insecureTls) == '0' ? 0 : 1
var jdbcDriverDirectory = text(body?.jdbcDriverDirectory).trim()
if (jdbcDriverDirectory != '' && (!jdbcDriverDirectory.startsWith('/') || jdbcDriverDirectory.contains('\n') || jdbcDriverDirectory.contains('\r') || jdbcDriverDirectory.contains('='))) {
    exit 400, 'JDBC 驱动目录必须填写 NiFi 节点上的 Linux 绝对目录，例如 /opt/nifi/nifi-current/lib/jdbc'
}
if (networkCode == '') {
    exit 400, '请选择所属网络'
}
// 以下从 sym_dict_t 业务表读取或维护当前操作涉及的记录。
var networkRoot = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('dict_code', 'SSWL').eq('is_del', 0).selectOne()
if (networkRoot == null || db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', networkRoot.tid).eq('dict_code', networkCode).eq('is_del', 0).selectOne() == null) {
    exit 400, '所属网络不存在或已停用'
}
if (!baseUrl.matches('^https?://.+')) {
    exit 400, 'NiFi 地址必须以 http:// 或 https:// 开头'
}
var now = new Date()
var enabled = body?.enabled == false || text(body?.enabled) == '0' ? 0 : 1
var isDefault = body?.isDefault == true || text(body?.isDefault) == '1' ? 1 : 0
var duplicate = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('node_code', nodeCode).eq('is_del', 0).selectOne()
if (duplicate != null && text(duplicate.tid) != tid) {
    exit 400, '节点编码已存在，请使用其他编码'
}
var networkDefault = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('network_code', networkCode).eq('is_del', 0).eq('is_default', 1).selectOne()
if (blank(tid) && networkDefault == null) {
    isDefault = 1
}
if (isDefault == 1) {
    db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('network_code', networkCode).eq('is_del', 0).update({ is_default: 0, updated_time: now })
}
if (blank(tid)) {
    if (isDefault == 1 && (blank(authUsername) || blank(authPassword))) {
        exit 400, '默认节点必须配置 NiFi 用户名和密码'
    }
    tid = IdUtil.getSnowflakeNextIdStr()
    db.table('nifi_node_t').primary('tid').insert({
        tid: tid, tenant_id: tenantId, node_name: nodeName, node_code: nodeCode, base_url: baseUrl,
        network_code: networkCode, root_process_group_id: text(body?.rootProcessGroupId).trim(), auth_username: authUsername, auth_password: authPassword, insecure_tls: insecureTls, jdbc_driver_locations: jdbcDriverDirectory, remark: text(body?.remark).trim(),
        enabled: enabled, is_default: isDefault, created_time: now, updated_time: now, is_del: 0
    })
} else {
    var existing = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
    if (existing == null) {
        exit 404, '数据同步节点不存在或已删除'
    }
    var persistedDefault = existing.isDefault == null ? existing.is_default : existing.isDefault
    var persistedNetworkCode = text(existing.networkCode == null ? existing.network_code : existing.networkCode).trim()
    var existingPassword = text(existing.authPassword == null ? existing.auth_password : existing.authPassword)
    if ((persistedDefault == 1 || persistedDefault == true) && persistedNetworkCode != '' && persistedNetworkCode != networkCode) {
        exit 400, '默认节点不能直接变更所属网络，请先在原网络设置新的默认节点'
    }
    if ((persistedDefault == 1 || persistedDefault == true) && isDefault == 0) {
        exit 400, '每个所属网络必须保留一个默认节点，请先将其他节点设为默认'
    }
    if (isDefault == 1 && (blank(authUsername) || (blank(authPassword) && blank(existingPassword)))) {
        exit 400, '默认节点必须配置 NiFi 用户名和密码'
    }
    db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).update({
        node_name: nodeName, node_code: nodeCode, base_url: baseUrl,
        network_code: networkCode, root_process_group_id: text(body?.rootProcessGroupId).trim(), auth_username: authUsername, auth_password: blank(authPassword) ? existingPassword : authPassword, insecure_tls: insecureTls, jdbc_driver_locations: jdbcDriverDirectory, remark: text(body?.remark).trim(),
        enabled: enabled, is_default: isDefault, updated_time: now
    })
}
return { tid: tid }
