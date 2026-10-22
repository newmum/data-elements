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
// 接口：01.数据抽取节点列表
// 路由：POST /ods/nifi-node/page
// 业务说明：数据抽取节点列表（POST /ods/nifi-node/page）。读取当前登录租户的 nifi_node_t，仅返回本租户未删除节点；使用请求参数 keyword。
import tableSort
import tenantRuntime
import cn.hutool.http.HttpRequest
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Map
import java.text.SimpleDateFormat
import com.linewell.dataelement.utils.LocalCache

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var keyword = text(body?.keyword).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再查看节点配置'
}

// Push nodes have separate storage; existing NiFi consumers keep the default contract.
if (text(body?.nodeType) == 'push') {
    // One in-process snapshot read for the whole tenant, never per-row probes.
    // Results describe the last check in this backend process; URL edits and
    // restarts naturally return nodes to the untested state.
    var healthKey = 'push-node-health:' + tenantId
    var healthJson = LocalCache.get(healthKey)
    var health = healthJson == null ? {} : new ObjectMapper().readValue(healthJson,Map)
    var remember = (nodeId,baseUrl,result) => {
        result.healthTime = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())
        health[nodeId] = {baseUrl:baseUrl,status:result.success?'reachable':'unreachable',message:result.message,time:result.healthTime,latencyMs:result.latencyMs}
        LocalCache.put(healthKey,new ObjectMapper().writeValueAsString(health))
        return result
    }
    if (text(body?.action) == 'health') {
        var nodeId = text(body?.tid).trim()
        var node = db.table('push_node_t').where().eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0).selectOne()
        if (node == null) { exit 404, '数据推送节点不存在或无权访问' }
        var url = text(node.baseUrl || node.base_url).replaceAll('/+$', '') + '/actuator/health'
        var started = new Date().getTime()
        var response = null
        try {
            response = HttpRequest.get(url).timeout(5000).setFollowRedirects(false).execute()
            var status = response.getStatus()
            var payload = new ObjectMapper().readValue(response.body(), Map)
            var success = status == 200 && text(payload.status) == 'UP'
            return remember(nodeId,text(node.baseUrl || node.base_url),{ success:success,status:status,latencyMs:new Date().getTime()-started,message:success?'数据推送服务运行正常':'推送服务健康检查未通过' })
        } catch (e) {
            return remember(nodeId,text(node.baseUrl || node.base_url),{ success:false,status:0,latencyMs:new Date().getTime()-started,message:'无法连接数据推送节点，请检查服务地址、网络连接及推送服务是否正常运行' })
        } finally { if (response != null) { response.close() } }
    }
    var page = db.table('push_node_t').where().eq('tenant_id',tenantId).eq('is_del',0)
        .orderBy('is_default','desc').orderBy('updated_time','desc').page(1001,0)
    if (page != null && page.total > 1000) { exit 422, '推送节点超过1000项，请缩小配置规模' }
    var list = []
    for (row in (page == null || page.list == null ? [] : page.list)) {
        var name = text(row.nodeName || row.node_name)
        var code = text(row.nodeCode || row.node_code)
        var network = text(row.networkCode || row.network_code)
        if (keyword != '' && !name.contains(keyword) && !code.contains(keyword) && !network.contains(keyword)) { continue }
        var checked = health[text(row.tid)]
        if (checked != null && checked.baseUrl != text(row.baseUrl || row.base_url)) { checked = null }
        list.add({healthStatus:checked?.status || 'unknown',healthMessage:text(checked?.message).replace('haitong-data-pull 服务健康','数据推送服务运行正常').replace(' haitong-data-pull ','数据推送服务'),healthTime:checked?.time || '',healthLatencyMs:checked?.latencyMs,tid:row.tid,nodeName:name,nodeCode:code,networkCode:network,
            baseUrl:row.baseUrl || row.base_url,enabled:row.enabled,isDefault:row.isDefault == null?row.is_default:row.isDefault,
            remark:row.remark,updatedTime:row.updatedTime || row.updated_time,
            lastSyncTime:row.lastSyncTime || row.last_sync_time,lastSyncStatus:row.lastSyncStatus || row.last_sync_status})
    }
    return {list:tableSort.rows(list,body,{name:'nodeName',nodeName:'nodeName',updatedTime:'updatedTime'},'updatedTime','tid'),total:list.size()}
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

// Where 在当前 Magic API 版本支持 page(size, offset)，不支持 limit(size)。
// 查询及总数都在当前租户范围内，保持有界读取，避免截断后误显示为完整列表。
var nodePage = db.table('nifi_node_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
    .orderBy('is_default', 'desc').orderBy('updated_time', 'desc').page(1001, 0)
if (nodePage != null && nodePage.total > 1000) { exit 422, '节点超过1000项，请使用节点分页查询' }
var rows = nodePage == null || nodePage.list == null ? [] : nodePage.list
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
list=tableSort.rows(list,body,{name:'nodeName',nodeName:'nodeName',updatedTime:'updatedTime'},'updatedTime','tid')
return { list: list, total: list.size() }
