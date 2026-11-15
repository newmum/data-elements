{
  "properties": {},
  "id": "a40b3b2f9b7c0e5a8f0d1c2b3a4e5f6a",
  "script": null,
  "groupId": "094cd528dceb45278146eb6f68aa9c2b",
  "name": "03.服务合集",
  "createTime": 1789000000111,
  "updateTime": 1789000000111,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/packages",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务市场合集：action=list 查询合集及明细，action=save 新增或更新并整体替换明细。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import tableWrite
import "@post:/sym/user/me" as me

// 接口：03.服务合集
// 路由：POST /dws/servicemarket/packages
// 业务说明：服务合集与明细按租户隔离；保存时一次校验全部服务、整体替换明细。

var tenantId = tenantRuntime.id()
var action = body?.action == null ? 'list' : (body.action + '').trim().toLowerCase()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
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
        packageCode: text(first(row, ['packageCode', 'packagecode', 'package_code'])),
        packageName: text(first(row, ['packageName', 'packagename', 'package_name'])),
        subscriptionMode: text(first(row, ['subscriptionMode', 'subscriptionmode', 'subscription_mode'])),
        approvalRequired: num(first(row, ['approvalRequired', 'approvalrequired', 'approval_required'])) == 1,
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        sortNo: num(first(row, ['sortNo', 'sortno', 'sort_no'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    var rows = db.select("select tid, package_code, package_name, subscription_mode, approval_required, status, description, sort_no, created_time from svc_market_package_t " + whereSql + " order by sort_no asc, created_time desc", { tenantId: tenantId })
    var packageIds = []
    for (row in rows) { packageIds.add(text(first(row, ['tid', 'id']))) }
    var details = []
    if (packageIds.size() > 0) {
        details = db.table('svc_market_package_item_t').where().eq('tenant_id', tenantId)
            .in('package_id', packageIds).eq('is_del', 0).orderBy('sort_no', 'asc').select()
    }
    var byPackage = {}
    for (detail in details) {
        var packageId = text(first(detail, ['packageId', 'packageid', 'package_id']))
        if (byPackage[packageId] == null) { byPackage[packageId] = [] }
        byPackage[packageId].add({
            id: text(first(detail, ['id', 'tid'])),
            serviceCode: text(first(detail, ['serviceCode', 'servicecode', 'service_code'])),
            version: text(first(detail, ['version', 'VERSION'])),
            sortNo: num(first(detail, ['sortNo', 'sortno', 'sort_no']))
        })
    }
    var list = []
    for (row in rows) {
        var item = view(row)
        item['items'] = byPackage[item.id] == null ? [] : byPackage[item.id]
        list.add(item)
    }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var packageCode = body?.packageCode == null ? '' : (body.packageCode + '').trim().toUpperCase()
    var packageName = body?.packageName == null ? '' : (body.packageName + '').trim()
    var subscriptionMode = body?.subscriptionMode == null ? 'PACKAGE' : (body.subscriptionMode + '').trim().toUpperCase()
    var approvalRaw = body?.approvalRequired
    var approvalRequired = (approvalRaw == true || text(approvalRaw).toLowerCase() == 'true' || text(approvalRaw) == '1') ? 1 : 0
    var status = body?.status == null ? 'DRAFT' : (body.status + '').trim().toUpperCase()
    var description = body?.description == null ? '' : (body.description + '').trim()
    var sortNo = body?.sortNo == null ? 0 : (body.sortNo + '')::int(0)
    var items = body?.items == null ? [] : body.items
    if (packageCode == '' || packageName == '') { exit 400, '合集编码和合集名称不能为空' }
    if (!['SINGLE', 'PACKAGE'].contains(subscriptionMode)) { exit 400, '订阅模式必须是 SINGLE/PACKAGE' }
    if (!['DRAFT', 'ON_SHELF', 'OFF_SHELF'].contains(status)) { exit 400, '状态必须是 DRAFT/ON_SHELF/OFF_SHELF' }
    if (!(items instanceof List) || items.size() == 0 || items.size() > 200) { exit 400, '合集明细必须为 1 到 200 项的数组' }
    var serviceCodes = []
    for (entry in items) {
        var code = entry?.serviceCode == null ? '' : (entry.serviceCode + '').trim()
        if (code == '' || serviceCodes.contains(code)) { exit 400, '合集明细服务编码不能为空且不能重复：' + code }
        serviceCodes.add(code)
    }
    var foundRows = db.table('svc_gateway_route_t').where().eq('tenant_id', tenantId)
        .eq('is_del', 0).in('service_code', serviceCodes).select()
    var foundCodes = []
    for (found in foundRows) { foundCodes.add(text(first(found, ['serviceCode', 'servicecode', 'service_code']))) }
    for (code in serviceCodes) {
        if (!foundCodes.contains(code)) { exit 400, '合集明细服务不存在：' + code }
    }
    return db.transaction(() => {
        // 现有合集先锁父行，合集与明细一起提交。
        if (id == '') {
            var duplicate = db.select("select count(1) as n from svc_market_package_t where tenant_id = #{tenantId} and package_code = #{packageCode} and is_del = 0", { tenantId: tenantId, packageCode: packageCode })
            if (duplicate[0].n > 0) { exit 409, '合集编码已存在：' + packageCode }
            id = IdUtil.simpleUUID()
            db.update("""
                insert into svc_market_package_t (tid, tenant_id, package_code, package_name, subscription_mode, approval_required, status, description, sort_no, created_by, updated_by, created_time, updated_time, is_del)
                values (#{id}, #{tenantId}, #{packageCode}, #{packageName}, #{subscriptionMode}, #{approvalRequired}, #{status}, #{description}, #{sortNo}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
            """, { id: id, tenantId: tenantId, packageCode: packageCode, packageName: packageName, subscriptionMode: subscriptionMode, approvalRequired: approvalRequired, status: status, description: description, sortNo: sortNo, userId: userId })
        } else {
            var existing = tableWrite.lockRow('svc_market_package_t', tenantId, 'tid', id, ['tid'], {is_del: 0})
            if (existing == null) { exit 404, '服务合集不存在或不在当前租户' }
            db.update("update svc_market_package_t set package_code = #{packageCode}, package_name = #{packageName}, subscription_mode = #{subscriptionMode}, approval_required = #{approvalRequired}, status = #{status}, description = #{description}, sort_no = #{sortNo}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { packageCode: packageCode, packageName: packageName, subscriptionMode: subscriptionMode, approvalRequired: approvalRequired, status: status, description: description, sortNo: sortNo, userId: userId, id: id, tenantId: tenantId })
        }
        db.table('svc_market_package_item_t').where().eq('tenant_id', tenantId).eq('package_id', id).delete()
        var rows = []
        var now = new Date()
        var index = 0
        for (entry in items) {
            rows.add({
                tid: IdUtil.simpleUUID(), tenant_id: tenantId, package_id: id,
                service_code: text(entry?.serviceCode).trim(),
                version: entry?.version == null ? 'v1' : (entry.version + '').trim(),
                sort_no: entry?.sortNo == null ? index : (entry.sortNo + '')::int(0),
                created_by: userId, updated_by: userId, created_time: now, updated_time: now, is_del: 0
            })
            index = index + 1
        }
        db.table('svc_market_package_item_t').batchInsert(rows, 200)
        var saved = db.table('svc_market_package_t').where().eq('tenant_id', tenantId).eq('tid', id).eq('is_del', 0).selectOne()
        return view(saved)
    })
}

exit 400, '不支持的 action：' + action
