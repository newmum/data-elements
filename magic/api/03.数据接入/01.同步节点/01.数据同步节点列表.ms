{
  "properties": {},
  "id": "ods_nifi_node_page_01",
  "groupId": "10bd924399785adab505cc0c03ca90ed",
  "name": "01.数据同步节点列表",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [{"name": "access", "value": "TENANT_SESSION"}],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"keyword\": \"主节点\"\n}",
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "查询当前行业的抽取和推送节点。health 检测本租户已保存节点，diagnostics 读取有限指标。password 仅在编辑者主动点击时读取 nodeId 指定的本租户有效节点密码，响应禁止缓存；列表仅返回是否配置密码及最近检测，不返回密码。",
  "script": null
}
================================
import tableSort
import tableRead
import tenantRuntime
import '@/common/nifiruntime' as readConfiguration
import cn.hutool.http.HttpRequest
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Map
import java.text.SimpleDateFormat
import java.sql.Timestamp
import com.linewell.dataelement.utils.LocalCache
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver as nodeResolver
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.model.common.CommonResponse
import org.springframework.http.ResponseEntity

// POST /ods/node/page 查询当前租户的抽取或推送节点，支持 keyword 筛选并回显最近一次连通检测。
// health 和 diagnostics 只访问当前行业保存的节点，NiFi 客户端从该节点取得连接配置。

var text = value => value == null ? '' : value + ''
var nodeRevision = value => value instanceof Timestamp ? value.toLocalDateTime().toString() : text(value).trim().replace(' ', 'T')
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

var action = text(body?.action)
if (action == 'password') {
    // 编辑者主动查看单个已保存节点的密码；名单不回传密码，节点ID不能扩大当前租户范围。
    var nodeId = text(body?.nodeId).trim()
    if (nodeId == '' || nodeId.length() > 64) { exit 400, '请选择需要查看密码的数据抽取节点' }
    var query = tableRead.from('nifi_node_t', 'n', tenantId)
    query.where(tableRead.all([
        tableRead.equal(tableRead.column('n','tid','TEXT'),nodeId),
        tableRead.equal(tableRead.coalesce([tableRead.column('n','is_del','NUMBER'),0]),0)
    ]))
    var rows = query.rows({auth_password:tableRead.column('n','auth_password','TEXT')},1)
    if (rows.isEmpty()) { exit 404, '数据抽取节点不存在或无权访问' }
    return ResponseEntity.ok().header('Cache-Control','no-store').header('Pragma','no-cache')
        .body(CommonResponse.success({nodeId:nodeId,authPassword:text(rows[0].auth_password)}))
}
if (action == 'health' || action == 'diagnostics') {
    var nodeId = text(body?.nodeId).trim()
    var started = new Date().getTime()
    var respond = data => ResponseEntity.ok(CommonResponse.success(data))
    var rootMessage = error => {
        var cause = error
        while (cause.getCause() != null) {
            cause = cause.getCause()
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()
    }
    if (action == 'diagnostics' && nodeId == '') {
        exit 400, '请选择需要查询的 NiFi 节点'
    }
    var healthFailure = message => respond({success:false, status:0, latencyMs:new Date().getTime()-started,
        message:message, url:''})
    if (nodeId == '') { return healthFailure('请选择需要检测的数据同步节点') }
    var configuration = null
    var node = null
    try {
        configuration = readConfiguration(nodeId, row => row)
        if (configuration == null) {
            var missing = '数据同步节点不存在或不属于当前租户'
            return action == 'health' ? healthFailure(missing) : respond({success:false, message:missing})
        }
        node = nodeResolver.runtimeNode(configuration)
    } catch (error) {
        var message = rootMessage(error)
        return action == 'health' ? healthFailure(message) : respond({success:false, message:message})
    }
    if (action == 'diagnostics') {
        try {
            var snapshot = nifi.systemDiagnostics(node).path('systemDiagnostics').path('aggregateSnapshot')
            if (snapshot.isMissingNode() || snapshot.isNull()) {
                return respond({success:false, message:'NiFi 未返回系统诊断快照'})
            }
            // 由脚本选择对前端开放的指标，避免返回原生诊断内容。
            var number = key => snapshot.path(key).isNumber() ? snapshot.path(key).numberValue() : null
            return respond({
                success:true,
                heapUtilization:snapshot.path('heapUtilization').asText(''),
                usedHeapBytes:number('usedHeapBytes'),
                maxHeapBytes:number('maxHeapBytes'),
                processorLoadAverage:number('processorLoadAverage'),
                availableProcessors:number('availableProcessors'),
                totalThreads:number('totalThreads'),
                statsLastRefreshed:snapshot.path('statsLastRefreshed').asText('')
            })
        } catch (error) {
            return respond({success:false, message:rootMessage(error)})
        }
    }
    var success = true
    var message = 'NiFi 节点根流程组访问正常'
    try {
        nifi.testConnection(node)
    } catch (error) {
        success = false
        message = rootMessage(error)
    }
    var result = {
        success:success, status:success ? 200 : 0, latencyMs:new Date().getTime()-started, message:message,
        url:node.baseUrl() + '/nifi-api/flow/process-groups/root',
        healthTime:new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date())
    }
    // 只回显此探活实际使用的配置版本。保存新配置后，旧结果自然失效。
    var key = 'nifi-node-health:' + tenantId + ':' + node.id() + ':' + nodeRevision(configuration.updated_time)
    LocalCache.put(key, new ObjectMapper().writeValueAsString({
        success:result.success, message:result.message, healthTime:result.healthTime, latencyMs:result.latencyMs
    }))
    return respond(result)
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
var mapper = new ObjectMapper()
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
    // 仅为已通过租户筛选的节点读取内存结果，与检测时的配置版本一致才显示。
    var updatedTime = row.updatedTime == null ? row.updated_time : row.updatedTime
    var revision = nodeRevision(updatedTime)
    var healthJson = LocalCache.get('nifi-node-health:' + tenantId + ':' + text(row.tid) + ':' + revision)
    var checked = healthJson == null ? null : mapper.readValue(healthJson, Map)
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
        hasAuthPassword: text(row.authPassword == null ? row.auth_password : row.authPassword) != '',
        insecureTls: row.insecureTls == null ? row.insecure_tls : row.insecureTls,
        jdbcDriverDirectory: row.jdbcDriverLocations == null ? row.jdbc_driver_locations : row.jdbcDriverLocations,
        remark: row.remark,
        enabled: row.enabled == null ? 1 : row.enabled,
        isDefault: row.isDefault == null ? row.is_default : row.isDefault,
        updatedTime: updatedTime,
        healthStatus: checked == null ? 'unknown' : (checked.success ? 'reachable' : 'unreachable'),
        healthMessage: text(checked?.message),
        healthTime: text(checked?.healthTime),
        healthLatencyMs: checked?.latencyMs
    })
}
list=tableSort.rows(list,body,{name:'nodeName',nodeName:'nodeName',updatedTime:'updatedTime'},'updatedTime','tid')
return { list: list, total: list.size() }
