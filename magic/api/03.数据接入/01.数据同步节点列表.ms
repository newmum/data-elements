{
  "properties": {},
  "id": "ods_nifi_node_page_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "01.数据同步节点列表",
  "path": "/nifi-node/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"keyword\": \"主节点\"\n}",
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "数据同步节点列表（POST /ods/nifi-node/page）。读取当前登录租户的 nifi_node_t，仅返回本租户未删除节点；使用请求参数 keyword。",
  "script": null
}
================================
// 接口：01.数据同步节点列表
// 路由：POST /ods/nifi-node/page
// 业务说明：数据同步节点列表（POST /ods/nifi-node/page）。读取当前登录租户的 nifi_node_t，仅返回本租户未删除节点；使用请求参数 keyword。
import tenantRuntime

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var keyword = text(body?.keyword).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再查看节点配置'
}
// 以下从 sym_dict_t 业务表读取或维护当前操作涉及的记录。
var root = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('dict_code', 'SSWL').eq('is_del', 0).selectOne()
var networkNames = {}
var networkIcons = {}
if (root != null) {
    var dictionaries = db.table('sym_dict_t').where().eq('tenant_id', tenantId).eq('parent_id', root.tid).eq('is_del', 0).select()
    for (dictionary in (dictionaries == null ? [] : dictionaries)) {
        var dictionaryCode = text(dictionary.dictCode == null ? dictionary.dict_code : dictionary.dictCode).trim()
        if (dictionaryCode != '') {
            networkNames[dictionaryCode] = text(dictionary.dictName == null ? dictionary.dict_name : dictionary.dictName).trim()
            networkIcons[dictionaryCode] = text(dictionary.icon).trim()
        }
    }
}

var rows = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
    .orderBy('is_default', 'desc').orderBy('updated_time', 'desc').select()
var list = []
for (row in (rows == null ? [] : rows)) {
    var nodeName = row.nodeName == null ? row.node_name : row.nodeName
    var nodeCode = row.nodeCode == null ? row.node_code : row.nodeCode
    var legacyNetwork = text(row.networkType == null ? row.network_type : row.networkType).trim()
    var networkCode = text(row.networkCode == null ? row.network_code : row.networkCode).trim()
    if (networkCode == '') {
        networkCode = legacyNetwork == '公安网' ? 'GAW' : (legacyNetwork == '互联网' ? 'HLW' : (legacyNetwork == '专网' || legacyNetwork == 'ZWW' ? 'SPZW' : legacyNetwork))
    }
    var networkName = text(networkNames[networkCode]).trim()
    if (networkName == '') {
        networkName = legacyNetwork == '' ? networkCode : legacyNetwork
    }
    if (keyword != '' && text(nodeName).indexOf(keyword) < 0 && text(nodeCode).indexOf(keyword) < 0 && networkCode.indexOf(keyword) < 0 && networkName.indexOf(keyword) < 0) {
        continue
    }
    list.add({
        tid: row.tid,
        nodeName: nodeName,
        nodeCode: nodeCode,
        networkCode: networkCode,
        networkName: networkName,
        networkIcon: text(networkIcons[networkCode]).trim(),
        baseUrl: row.baseUrl == null ? row.base_url : row.baseUrl,
        rootProcessGroupId: row.rootProcessGroupId == null ? row.root_process_group_id : row.rootProcessGroupId,
        authUsername: row.authUsername == null ? row.auth_username : row.authUsername,
        insecureTls: row.insecureTls == null ? row.insecure_tls : row.insecureTls,
        jdbcDriverDirectory: row.jdbcDriverLocations == null ? row.jdbc_driver_locations : row.jdbcDriverLocations,
        remark: row.remark,
        enabled: row.enabled == null ? 1 : row.enabled,
        isDefault: row.isDefault == null ? row.is_default : row.isDefault,
        updatedTime: row.updatedTime == null ? row.updated_time : row.updatedTime
    })
}
return { list: list, total: list.size() }
