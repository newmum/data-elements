{
  "properties": {},
  "id": "8c3e5f4d1f9a4c7e0d2b3a4f5e6c7d82",
  "script": null,
  "groupId": "9a427a26bc984532b8595da7142765d2",
  "name": "07.网络区域",
  "createTime": 1789000000103,
  "updateTime": 1789000000103,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/regions",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务网关网络区域管理：action=list 查询，action=save 新增或更新；区域编码租户内唯一。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

// 接口：07.网络区域
// 路由：POST /dws/gateway/regions
// 业务说明：互联网区/政务外网区/跨网区配置，按当前租户隔离。

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var view = (row) => {
    return {
        id: text(first(row, ['id', 'tid'])),
        regionCode: text(first(row, ['regionCode', 'regioncode', 'region_code'])),
        regionName: text(first(row, ['regionName', 'regionname', 'region_name'])),
        networkZone: text(first(row, ['networkZone', 'networkzone', 'network_zone'])),
        endpoint: text(first(row, ['endpoint', 'ENDPOINT'])),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    var rows = db.select("select tid, region_code, region_name, network_zone, endpoint, status, description, created_time from svc_gateway_region_t " + whereSql + " order by region_code asc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var regionCode = body?.regionCode == null ? '' : (body.regionCode + '').trim().toUpperCase()
    var regionName = body?.regionName == null ? '' : (body.regionName + '').trim()
    var networkZone = body?.networkZone == null ? 'GOV' : (body.networkZone + '').trim().toUpperCase()
    var endpoint = body?.endpoint == null ? '' : (body.endpoint + '').trim()
    var status = body?.status == null ? 'ENABLED' : (body.status + '').trim().toUpperCase()
    var description = body?.description == null ? '' : (body.description + '').trim()
    if (regionCode == '' || regionName == '') { exit 400, '区域编码和区域名称不能为空' }
    if (!['INTERNET', 'GOV', 'CROSS'].contains(networkZone)) { exit 400, '网络分区必须是 INTERNET/GOV/CROSS' }
    if (!['ENABLED', 'DISABLED'].contains(status)) { exit 400, '状态必须是 ENABLED/DISABLED' }
    if (id == '') {
        var duplicate = db.select("select count(1) as n from svc_gateway_region_t where tenant_id = #{tenantId} and region_code = #{regionCode} and is_del = 0", { tenantId: tenantId, regionCode: regionCode })
        if (duplicate[0].n > 0) { exit 409, '区域编码已存在：' + regionCode }
        id = IdUtil.simpleUUID()
        db.update("""
            insert into svc_gateway_region_t (tid, tenant_id, region_code, region_name, network_zone, endpoint, status, description, created_by, updated_by, created_time, updated_time, is_del)
            values (#{id}, #{tenantId}, #{regionCode}, #{regionName}, #{networkZone}, #{endpoint}, #{status}, #{description}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
        """, { id: id, tenantId: tenantId, regionCode: regionCode, regionName: regionName, networkZone: networkZone, endpoint: endpoint, status: status, description: description, userId: userId })
    } else {
        var existing = db.select("select tid from svc_gateway_region_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
        if (existing == null || existing.size() == 0) { exit 404, '区域不存在或不在当前租户' }
        var duplicate = db.select("select count(1) as n from svc_gateway_region_t where tenant_id = #{tenantId} and region_code = #{regionCode} and tid <> #{id} and is_del = 0", { tenantId: tenantId, regionCode: regionCode, id: id })
        if (duplicate[0].n > 0) { exit 409, '区域编码已存在：' + regionCode }
        db.update("update svc_gateway_region_t set region_code = #{regionCode}, region_name = #{regionName}, network_zone = #{networkZone}, endpoint = #{endpoint}, status = #{status}, description = #{description}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { regionCode: regionCode, regionName: regionName, networkZone: networkZone, endpoint: endpoint, status: status, description: description, userId: userId, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, region_code, region_name, network_zone, endpoint, status, description, created_time from svc_gateway_region_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

exit 400, '不支持的 action：' + action
