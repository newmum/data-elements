{
  "properties": {},
  "id": "ods_service_node_save_01",
  "groupId": "1f4028f20aaa4f379b5e37a343b99fa9",
  "name": "06.保存数据服务节点",
  "path": "/service-node/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "requestBody": "{\n  \"nodeName\": \"服务发布主节点\",\n  \"nodeCode\": \"service-primary\",\n  \"businessDomain\": \"公共服务\",\n  \"serviceType\": \"API 服务\",\n  \"baseUrl\": \"https://api.example.com\"\n}",
  "responseBody": "{\n  \"tid\": \"2087600000000000001\"\n}",
  "description": "保存数据服务节点（POST /ods/service-node/saveOrUpdate）。维护当前租户发布节点，部署凭证仅保存凭证引用；使用请求参数 tid、nodeName、nodeCode、businessDomain、serviceType、baseUrl、managementUrl、enabled；涉及 service_node_t 等业务表。",
  "script": null
}
================================
// 接口：06.保存数据服务节点
// 路由：POST /ods/service-node/saveOrUpdate
// 业务说明：保存数据服务节点（POST /ods/service-node/saveOrUpdate）。维护当前租户发布节点，部署凭证仅保存凭证引用；使用请求参数 tid、nodeName、nodeCode、businessDomain、serviceType、baseUrl、managementUrl、enabled；涉及 service_node_t 等业务表。
import cn.hutool.core.util.IdUtil
import tenantRuntime

var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var tid = text(body?.tid).trim()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = text(tenantRuntime.id()).trim()
var name = text(body?.nodeName).trim()
var code = text(body?.nodeCode).trim()
var businessDomain = text(body?.businessDomain).trim()
var serviceType = text(body?.serviceType).trim()
var baseUrl = text(body?.baseUrl).trim().replaceAll('/+$', '')
var managementUrl = text(body?.managementUrl).trim().replaceAll('/+$', '')
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再维护节点'
}
if (blank(name) || blank(code) || blank(businessDomain) || blank(serviceType) || blank(baseUrl)) {
    exit 400, '节点名称、节点编码、业务域、服务类型和服务地址不能为空'
}
if (!baseUrl.matches('^https?://.+') || (!blank(managementUrl) && !managementUrl.matches('^https?://.+'))) {
    exit 400, '服务地址和管理部署地址必须以 http:// 或 https:// 开头'
}
var now = new Date()
var enabled = body?.enabled == false || text(body?.enabled) == '0' ? 0 : 1
var isDefault = body?.isDefault == true || text(body?.isDefault) == '1' ? 1 : 0
var localNode = body?.localNode == true || text(body?.localNode) == '1' ? 1 : 0
if (isDefault == 1 && enabled == 0) {
    exit 400, '默认发布节点必须保持启用状态'
}
// 以下从 service_node_t 业务表读取或维护当前操作涉及的记录。
var duplicate = db.table('service_node_t').where().eq('tenant_id', tenantId).eq('node_code', code).eq('is_del', 0).selectOne()
if (duplicate != null && text(duplicate.tid) != tid) {
    exit 400, '节点编码已存在，请使用其他编码'
}
if (isDefault == 1) {
    db.table('service_node_t').where().eq('tenant_id', tenantId).eq('is_del', 0).update({ is_default: 0, updated_time: now })
}
if (blank(tid)) {
    tid = IdUtil.getSnowflakeNextIdStr()
    db.table('service_node_t').primary('tid').insert({
        tid: tid, tenant_id: tenantId, node_name: name, node_code: code, business_domain: businessDomain, service_type: serviceType,
        base_url: baseUrl, management_url: managementUrl, credential_ref: text(body?.credentialRef).trim(), enabled: enabled,
        is_default: isDefault, local_node: localNode, remark: text(body?.remark).trim(), created_time: now, updated_time: now, is_del: 0
    })
} else {
    var existing = db.table('service_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
    if (existing == null) {
        exit 404, '数据服务节点不存在或已删除'
    }
    var existingLocal = existing.localNode == null ? existing.local_node : existing.localNode
    if ((text(existingLocal) == '1' || existingLocal == true) && localNode == 0) {
        exit 400, '本机发布节点不能取消本机标记'
    }
    db.table('service_node_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).update({
        node_name: name, node_code: code, business_domain: businessDomain, service_type: serviceType, base_url: baseUrl,
        management_url: managementUrl, credential_ref: text(body?.credentialRef).trim(), enabled: enabled,
        is_default: isDefault, local_node: localNode, remark: text(body?.remark).trim(), updated_time: now
    })
}
return { tid: tid }
