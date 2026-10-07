{
  "properties": {},
  "id": "ods_nifi_node_access_options_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "04.数据接入节点选项",
  "path": "/nifi-node/access-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{}",
  "responseBody": "{\n  \"list\": []\n}",
  "description": "数据接入节点选项及所属网络图标配置（POST /ods/nifi-node/access-options）。默认读取当前租户已启用节点；action=saveNetworkIcon 用于维护 SSWL 网络图标；涉及 sym_dict_t、nifi_node_t 等业务表；执行数据库写入并返回处理结果。",
  "script": null
}
================================
// 接口：04.数据接入节点选项
// 路由：POST /ods/nifi-node/access-options
// 业务说明：数据接入节点选项及所属网络图标配置（POST /ods/nifi-node/access-options）。默认读取当前租户已启用节点；action=saveNetworkIcon 用于维护 SSWL 网络图标；涉及 sym_dict_t、nifi_node_t 等业务表；执行数据库写入并返回处理结果。
import tenantRuntime

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var action = text(body?.action).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再读取数据接入节点'
}

// 以下从 sym_dict_t 业务表读取或维护当前操作涉及的记录。
var networkRoot = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('dict_code', 'SSWL').eq('is_del', 0).selectOne()
var allowedIcons = ['safe', 'menu-earth', 'tree', 'database-network', 'connection', 'organisation']
if (action == 'saveNetworkIcon') {
    if (networkRoot == null) {
        exit 400, '未配置 SSWL 数据字典，无法设置所属网络图标'
    }
    var targetNetworkCode = text(body?.networkCode).trim()
    var targetIcon = text(body?.icon).trim()
    if (targetNetworkCode == '' || targetIcon == '') {
        exit 400, '请选择所属网络和图标'
    }
    if (allowedIcons.indexOf(targetIcon) < 0) {
        exit 400, '不支持的所属网络图标'
    }
    var targetNetwork = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', networkRoot.tid).eq('dict_code', targetNetworkCode).eq('is_del', 0).selectOne()
    if (targetNetwork == null) {
        exit 400, '所属网络不存在或已删除'
    }
    db.table('sym_dict_t').where().eq('tid', targetNetwork.tid).eq('tenant_id', tenantId).eq('is_del', 0).update({ icon: targetIcon })
    return { saved: true, networkCode: targetNetworkCode, icon: targetIcon }
}
var networks = []
var networkNames = {}
if (networkRoot != null) {
    var dictionaryRows = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', networkRoot.tid).eq('is_del', 0).orderBy('sort_no', 'asc').select()
    for (dictionary in (dictionaryRows == null ? [] : dictionaryRows)) {
        var networkCode = text(dictionary.dictCode == null ? dictionary.dict_code : dictionary.dictCode).trim()
        var networkName = text(dictionary.dictName == null ? dictionary.dict_name : dictionary.dictName).trim()
        if (networkCode != '' && networkName != '') {
            networkNames[networkCode] = networkName
            networks.add({ value: networkCode, label: networkName, tagType: text(dictionary.tagType == null ? dictionary.tag_type : dictionary.tagType).trim(), icon: text(dictionary.icon).trim() })
        }
    }
}

var rows = db.table('nifi_node_t').where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .eq('enabled', 1)
    .orderBy('is_default', 'desc')
    .orderBy('updated_time', 'desc')
    .select()
var list = []
for (row in (rows == null ? [] : rows)) {
    var nodeCode = text(row.nodeCode == null ? row.node_code : row.nodeCode).trim()
    var nodeName = text(row.nodeName == null ? row.node_name : row.nodeName).trim()
    var legacyNetwork = text(row.networkType == null ? row.network_type : row.networkType).trim()
    var networkCode = text(row.networkCode == null ? row.network_code : row.networkCode).trim()
    if (networkCode == '') {
        networkCode = legacyNetwork == '公安网' ? 'GAW' : (legacyNetwork == '互联网' ? 'HLW' : (legacyNetwork == '专网' || legacyNetwork == 'ZWW' ? 'SPZW' : legacyNetwork))
    }
    var networkName = text(networkNames[networkCode]).trim()
    if (networkName == '') {
        networkName = legacyNetwork == '' ? networkCode : legacyNetwork
    }
    var baseUrl = text(row.baseUrl == null ? row.base_url : row.baseUrl).trim().replaceAll('/+$', '')
    if (nodeCode == '' || nodeName == '' || baseUrl == '') {
        continue
    }
    list.add({
        value: nodeCode,
        label: networkName + ' · ' + nodeName,
        nodeId: text(row.tid),
        tid: text(row.tid),
        nodeCode: nodeCode,
        nodeName: nodeName,
        networkCode: networkCode,
        networkName: networkName,
        baseUrl: baseUrl,
        isDefault: row.isDefault == null ? row.is_default : row.isDefault
    })
}
return { list: list, networks: networks }
