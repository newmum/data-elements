{
  "properties": {},
  "id": "ods_service_node_page_01",
  "groupId": "fc9808ac518c55dc8064cbf8bcf73175",
  "name": "01.数据服务节点列表",
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [{"name": "access", "value": "TENANT_SESSION"}],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"keyword\": \"发布\"\n}",
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "按 keyword 查询当前行业的服务发布节点。health 使用当前租户保存的地址探活，仅返回状态、时延、地址和说明。",
  "script": null
}
================================
import tableSort
import tenantRuntime
import httpx
import flowRuntime
import com.linewell.dataelement.model.common.CommonResponse
import java.net.URI
import org.springframework.http.ResponseEntity

// POST /ods/service/page 数据服务节点列表。
// 接口：05.数据服务节点列表
// 路由：POST /ods/service/page
// health 按 nodeId 读取当前行业已保存的服务节点。HTTP 请求只携带探活所需的目标，不读取节点凭据。

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var keyword = text(body?.keyword).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再查看节点配置'
}
if (body?.action == 'health') {
    var nodeId = text(body?.nodeId).trim()
    var node = db.table('service_node_t').columns('local_node', 'base_url', 'management_url')
        .where().eq('tenant_id', tenantId).eq('tid', nodeId).eq('is_del', 0).selectOne()
    if (node == null) {
        exit 404, '数据服务节点不存在或无权访问'
    }
    var respond = (success,status,latency,message,url) => ResponseEntity.ok(CommonResponse.success({
        success:success, status:status, latencyMs:latency, message:message, url:url
    }))
    var local = text(node.localNode == null ? node.local_node : node.localNode).toLowerCase()
    if (local == '1' || local == 'true') {
        return respond(true, 200, 0, '本机 data-elements 数据服务节点可用', 'local')
    }
    var target = text(node.managementUrl == null ? node.management_url : node.managementUrl).trim()
    if (target == '') {
        target = text(node.baseUrl == null ? node.base_url : node.baseUrl).trim()
    }
    if (target == '') {
        return respond(false, 0, 0, '请填写服务访问地址或管理部署地址', '')
    }
    var uri = null
    try {
        var normalized = target.replaceAll('/+$', '')
        uri = URI.create(normalized)
        if (!'http'.equalsIgnoreCase(uri.getScheme()) && !'https'.equalsIgnoreCase(uri.getScheme())) {
            return respond(false, 0, 0, '服务地址无效：协议必须为 HTTP 或 HTTPS', target)
        }
        if (uri.getHost() == null) {
            return respond(false, 0, 0, '服务地址无效：地址缺少主机名', target)
        }
        if (uri.getPath() == null || uri.getPath() == '' || uri.getPath() == '/') {
            uri = URI.create(normalized + flowRuntime.runtimeHealthPath())
        }
    } catch (error) {
        return respond(false, 0, 0, '服务地址无效', target)
    }
    var started = new Date().getTime()
    try {
        var status = httpx.status(uri)
        var success = status >= 200 && status < 300
        return respond(success, status, new Date().getTime()-started,
            success ? '节点连通正常' : '节点响应 HTTP ' + status, uri.toString())
    } catch (error) {
        var cause = error
        while (cause.getCause() != null) {
            cause = cause.getCause()
        }
        var message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()
        return respond(false, 0, new Date().getTime()-started, message, uri.toString())
    }
}
// 以下从 service_node_t 业务表读取或维护当前操作涉及的记录。
var rows = db.table('service_node_t').where().eq('tenant_id', tenantId).eq('is_del', 0)
    .orderBy('is_default', 'desc').orderBy('updated_time', 'desc').select()
var list = []
for (row in (rows == null ? [] : rows)) {
    var name = row.nodeName == null ? row.node_name : row.nodeName
    var code = row.nodeCode == null ? row.node_code : row.nodeCode
    var domain = row.businessDomain == null ? row.business_domain : row.businessDomain
    var serviceType = row.serviceType == null ? row.service_type : row.serviceType
    if (keyword != '' && text(name).indexOf(keyword) < 0 && text(code).indexOf(keyword) < 0 && text(domain).indexOf(keyword) < 0 && text(serviceType).indexOf(keyword) < 0) {
        continue
    }
    list.add({
        tid: row.tid, nodeName: name, nodeCode: code, businessDomain: domain, serviceType: serviceType,
        baseUrl: row.baseUrl == null ? row.base_url : row.baseUrl,
        managementUrl: row.managementUrl == null ? row.management_url : row.managementUrl,
        credentialRef: row.credentialRef == null ? row.credential_ref : row.credentialRef,
        enabled: row.enabled == null ? 1 : row.enabled,
        isDefault: row.isDefault == null ? row.is_default : row.isDefault,
        localNode: row.localNode == null ? row.local_node : row.localNode,
        remark: row.remark,
        updatedTime: row.updatedTime == null ? row.updated_time : row.updatedTime
    })
}
list=tableSort.rows(list,body,{name:'nodeName',nodeName:'nodeName',updatedTime:'updatedTime'},'updatedTime','tid')
return { list: list, total: list.size() }
