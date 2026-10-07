{
  "properties": {},
  "id": "ods_service_node_page_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "05.数据服务节点列表",
  "path": "/service-node/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"keyword\": \"发布\"\n}",
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "数据服务节点列表（POST /ods/service-node/page）。读取当前租户服务发布节点，不返回部署凭证引用以外的敏感信息；使用请求参数 keyword；涉及 service_node_t 等业务表。",
  "script": null
}
================================
// 接口：05.数据服务节点列表
// 路由：POST /ods/service-node/page
// 业务说明：数据服务节点列表（POST /ods/service-node/page）。读取当前租户服务发布节点，不返回部署凭证引用以外的敏感信息；使用请求参数 keyword；涉及 service_node_t 等业务表。
import tenantRuntime

var text = value => value == null ? '' : value + ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var keyword = text(body?.keyword).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') {
    exit 401, '当前登录租户无效，请重新登录后再查看节点配置'
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
return { list: list, total: list.size() }
