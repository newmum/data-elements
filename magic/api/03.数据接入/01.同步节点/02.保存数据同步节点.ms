{
  "properties": {},
  "id": "ods_nifi_node_save_01",
  "groupId": "10bd924399785adab505cc0c03ca90ed",
  "name": "02.保存数据同步节点",
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"nodeName\": \"数据同步主节点\",\n  \"nodeCode\": \"primary-nifi\",\n  \"baseUrl\": \"https://nifi.example.com:8443\"\n}",
  "responseBody": "{\n  \"tid\": \"2087600000000000001\"\n}",
  "description": "保存当前租户的数据抽取或推送节点（POST /ods/node/save），返回 tid。每个所属网络保留一个默认节点。HTTP NiFi 节点的用户名和密码可同时留空，HTTPS 须设置完整凭据。",
  "script": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import java.net.URI

// POST /ods/node/save 保存数据同步节点。
// 接口：02.保存数据抽取节点
// 路由：POST /ods/node/save
// 业务说明：按 tid、节点信息、网络和凭据保存本租户节点并返回 tid。HTTP NiFi 可免认证，HTTPS 须设置完整凭据。

var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var tid = text(body?.tid).trim()
var nodeName = text(body?.nodeName).trim()
var nodeCode = text(body?.nodeCode).trim()
var baseUrl = text(body?.baseUrl).trim().replaceAll('/+$', '')
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()

if (text(body?.nodeType) == 'push') {
    if (blank(tenantId)) { exit 401, '当前登录租户无效' }
    if (blank(nodeName) || blank(nodeCode) || blank(baseUrl) || nodeName.length()>128 || nodeCode.length()>64 || baseUrl.length()>255) {
        exit 400, '请填写有效的节点名称、编码和推送服务地址'
    }
    if (text(body?.remark).length()>500) { exit 400, '节点说明最多500个字符' }
    var uri = null
    try { uri = new URI(baseUrl) } catch (e) { exit 400, '推送服务地址格式不正确' }
    if (!['http','https'].contains(uri.getScheme()) || blank(uri.getHost()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) {
        exit 400, '请填写不含账号、查询参数或锚点的 HTTP(S) 服务根地址'
    }
    var networkCode = text(body?.networkCode).trim()
    var root = db.table('sym_dict_t').where().eq('tenant_id',tenantId).eq('dict_code','SSWL').eq('is_del',0).selectOne()
    if (root == null || db.table('sym_dict_t').where().eq('tenant_id',tenantId).eq('parent_id',root.tid).eq('dict_code',networkCode).eq('is_del',0).selectOne()==null) {
        exit 400, '所属网络不存在或已停用'
    }
    var existing = blank(tid)?null:db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',tid).eq('is_del',0).selectOne()
    if (!blank(tid) && existing==null) { exit 404, '数据推送节点不存在或无权访问' }
    var duplicate = db.table('push_node_t').where().eq('tenant_id',tenantId).eq('node_code',nodeCode).eq('is_del',0).selectOne()
    if (duplicate!=null && text(duplicate.tid)!=tid) { exit 409, '节点编码已存在，请使用其他编码' }
    var enabled = body?.enabled==false || text(body?.enabled)=='0'?0:1
    var isDefault = body?.isDefault==true || text(body?.isDefault)=='1'?1:0
    var networkDefault = db.table('push_node_t').where().eq('tenant_id',tenantId).eq('network_code',networkCode).eq('is_del',0).eq('is_default',1).selectOne()
    if (existing!=null && text(existing.isDefault == null?existing.is_default:existing.isDefault)=='1'
        && (text(existing.networkCode || existing.network_code)!=networkCode || isDefault==0)) {
        exit 409, '请先在原网络指定其他默认节点，再调整当前默认节点'
    }
    if (networkDefault==null) { isDefault=1 }
    var now = new Date()
    var values = {node_name:nodeName,node_code:nodeCode,network_code:networkCode,base_url:baseUrl,enabled:enabled,is_default:isDefault,remark:text(body?.remark).trim(),updated_time:now}
    db.transaction(() => {
        if (isDefault==1) { db.table('push_node_t').where().eq('tenant_id',tenantId).eq('network_code',networkCode).eq('is_del',0).update({is_default:0}) }
        if (existing==null) {
            tid=IdUtil.getSnowflakeNextIdStr()
            values.tid=tid;values.tenant_id=tenantId;values.created_time=now;values.is_del=0
            db.table('push_node_t').primary('tid').insert(values)
        } else { db.table('push_node_t').where().eq('tenant_id',tenantId).eq('tid',tid).eq('is_del',0).update(values) }
    })
    return {tid:tid}
}

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
if (jdbcDriverDirectory.length() > 4096) { exit 400, 'JDBC 驱动位置配置最多4096个字符' }
if (jdbcDriverDirectory != '') {
    // The runtime supports a shared directory or an explicit per-dialect JAR.
    // Assets uploaded through NiFi can reside outside the default driver directory.
    var locations = jdbcDriverDirectory.contains('=') ? jdbcDriverDirectory.split('[;\\r\\n]+') : [jdbcDriverDirectory]
    if (locations.size() > 32) { exit 400, '最多配置32项JDBC驱动位置' }
    for (entry in locations) {
        var location = text(entry).trim()
        if (jdbcDriverDirectory.contains('=')) {
            var separator = location.indexOf('=')
            if (separator < 1 || !location.substring(0,separator).matches('[A-Za-z0-9_*]+')) { exit 400, '驱动映射须使用数据库类型=绝对路径' }
            location = location.substring(separator+1).trim()
        }
        if (!location.startsWith('/') || location.contains('=') || location.contains('\n') || location.contains('\r') || location.contains('\u0000')) {
            exit 400, 'JDBC驱动位置须为NiFi节点上的Linux绝对目录或JAR路径'
        }
    }
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
var creating = blank(tid)
var httpNode = baseUrl.startsWith('http://')
var clearStoredCredentials = false
if (!creating) {
    var existing = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
    if (existing == null) {
        exit 404, '数据抽取节点不存在或已删除'
    }
    var persistedDefault = existing.isDefault == null ? existing.is_default : existing.isDefault
    var persistedNetworkCode = text(existing.networkCode == null ? existing.network_code : existing.networkCode).trim()
    var existingUsername = text(existing.authUsername == null ? existing.auth_username : existing.authUsername).trim()
    var existingPassword = text(existing.authPassword == null ? existing.auth_password : existing.authPassword)
    if ((persistedDefault == 1 || persistedDefault == true) && persistedNetworkCode != '' && persistedNetworkCode != networkCode) {
        exit 400, '默认节点不能直接变更所属网络，请先在原网络设置新的默认节点'
    }
    if ((persistedDefault == 1 || persistedDefault == true) && isDefault == 0) {
        exit 400, '每个所属网络必须保留一个默认节点，请先将其他节点设为默认'
    }
    // 普通编辑时空密码表示保留。HTTP 显式清空用户名且密码留空才切换到免认证。
    var sentUsername = body.containsKey('authUsername')
    var clearCredentials = httpNode && sentUsername && blank(authUsername) && blank(authPassword)
    if (!sentUsername) { authUsername = existingUsername }
    if (blank(authPassword) && !clearCredentials) { authPassword = existingPassword }
    clearStoredCredentials = clearCredentials && (!blank(existingUsername) || !blank(existingPassword))
}
var anonymousHttp = httpNode && blank(authUsername) && blank(authPassword)
if (!anonymousHttp && (blank(authUsername) || blank(authPassword))) {
    exit 400, '请填写完整的 NiFi 用户名和密码，仅 HTTP 节点可同时留空'
}

if (creating) { tid = IdUtil.getSnowflakeNextIdStr() }
var nodeId = tid
var clearAuthentication = clearStoredCredentials
var values = {
    node_name: nodeName, node_code: nodeCode, base_url: baseUrl, network_code: networkCode,
    root_process_group_id: text(body?.rootProcessGroupId).trim(), auth_username: authUsername, auth_password: authPassword,
    insecure_tls: insecureTls, jdbc_driver_locations: jdbcDriverDirectory, remark: text(body?.remark).trim(),
    enabled: enabled, is_default: isDefault, updated_time: now
}
// 校验完成后，在同一事务切换默认节点并保存，失败时保留原有默认节点。
db.transaction(() => {
    if (values.is_default == 1) {
        db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('network_code', networkCode).eq('is_del', 0)
            .update({ is_default: 0, updated_time: now })
    }
    if (creating) {
        values.tid = nodeId
        values.tenant_id = tenantId
        values.created_time = now
        values.is_del = 0
        db.table('nifi_node_t').primary('tid').insert(values)
    } else {
        db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0).update(values)
        if (clearAuthentication) {
            // 常规更新忽略空值。显式切换认证方式时清除两列，兼容空字符串视为 NULL 的数据库。
            db.table('nifi_node_t').withBlank().where().eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0)
                .update({ auth_username: null, auth_password: null })
        }
    }
})
return { tid: tid }
