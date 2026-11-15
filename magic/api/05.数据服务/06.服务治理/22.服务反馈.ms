{
  "properties": {},
  "id": "f3af2a1e8a6b9d4f7e9c0b1a2f3d4e59",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "22.服务反馈",
  "createTime": 1789000000110,
  "updateTime": 1789000000110,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gateway/feedback",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"action\": \"list\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "服务反馈闭环：action=list 查询，action=save 登记，action=handle 处理并关闭。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.服务反馈
// 路由：POST /dws/service/gateway/feedback
// 业务说明：订阅方反馈登记与处理闭环；关闭时记录处理人、结果与闭环时间。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

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
        serviceCode: text(first(row, ['serviceCode', 'servicecode', 'service_code'])),
        appId: text(first(row, ['appId', 'appid', 'app_id'])),
        feedbackType: text(first(row, ['feedbackType', 'feedbacktype', 'feedback_type'])),
        content: text(first(row, ['content', 'CONTENT'])),
        status: text(first(row, ['status', 'STATUS'])),
        handler: text(first(row, ['handler', 'HANDLER'])),
        handleResult: text(first(row, ['handleResult', 'handleresult', 'handle_result'])),
        closedTime: first(row, ['closedTime', 'closedtime', 'closed_time']),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time'])
    }
}

if (action == 'list') {
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var statusFilter = body?.status == null ? '' : (body.status + '').trim().toUpperCase()
    var whereSql = " where tenant_id = #{tenantId} and is_del = 0 "
    if (serviceCode != '') { whereSql += " and service_code = '" + serviceCode.replace("'", "''") + "' " }
    if (statusFilter != '') { whereSql += " and status = '" + statusFilter.replace("'", "''") + "' " }
    var rows = db.select("select tid, service_code, app_id, feedback_type, content, status, handler, handle_result, closed_time, created_time from svc_feedback_t " + whereSql + " order by created_time desc", { tenantId: tenantId })
    var list = []
    for (row in rows) { list.add(view(row)) }
    return { list: list, total: list.size() }
}

if (action == 'save') {
    var serviceCode = body?.serviceCode == null ? '' : (body.serviceCode + '').trim()
    var appId = body?.appId == null ? '' : (body.appId + '').trim()
    var feedbackType = body?.feedbackType == null ? 'ISSUE' : (body.feedbackType + '').trim().toUpperCase()
    var content = body?.content == null ? '' : (body.content + '').trim()
    if (serviceCode == '' || content == '') { exit 400, '服务编码和反馈内容不能为空' }
    if (!['ISSUE', 'SUGGEST', 'OTHER'].contains(feedbackType)) { exit 400, '反馈类型必须是 ISSUE/SUGGEST/OTHER' }
    var id = IdUtil.simpleUUID()
    db.update("""
        insert into svc_feedback_t (tid, tenant_id, service_code, app_id, feedback_type, content, status, handler, handle_result, closed_time, created_by, updated_by, created_time, updated_time, is_del)
        values (#{id}, #{tenantId}, #{serviceCode}, #{appId}, #{feedbackType}, #{content}, 'OPEN', null, null, null, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)
    """, { id: id, tenantId: tenantId, serviceCode: serviceCode, appId: appId, feedbackType: feedbackType, content: content, userId: userId })
    var saved = db.select("select tid, service_code, app_id, feedback_type, content, status, handler, handle_result, closed_time, created_time from svc_feedback_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

if (action == 'handle') {
    var id = body?.id == null ? '' : (body.id + '').trim()
    var status = body?.status == null ? 'CLOSED' : (body.status + '').trim().toUpperCase()
    var handleResult = body?.handleResult == null ? '' : (body.handleResult + '').trim()
    if (id == '') { exit 400, '反馈标识不能为空' }
    if (!['PROCESSING', 'CLOSED'].contains(status)) { exit 400, '处理状态必须是 PROCESSING/CLOSED' }
    if (status == 'CLOSED' && handleResult == '') { exit 400, '关闭反馈必须填写处理结果' }
    var existing = db.select("select tid from svc_feedback_t where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { id: id, tenantId: tenantId })
    if (existing == null || existing.size() == 0) { exit 404, '反馈不存在或不在当前租户' }
    if (status == 'CLOSED') {
        db.update("update svc_feedback_t set status = 'CLOSED', handler = #{userId}, handle_result = #{handleResult}, closed_time = current_timestamp, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { userId: userId, handleResult: handleResult, id: id, tenantId: tenantId })
    } else {
        db.update("update svc_feedback_t set status = 'PROCESSING', handler = #{userId}, handle_result = #{handleResult}, updated_by = #{userId}, updated_time = current_timestamp where tid = #{id} and tenant_id = #{tenantId} and is_del = 0", { userId: userId, handleResult: handleResult, id: id, tenantId: tenantId })
    }
    var saved = db.select("select tid, service_code, app_id, feedback_type, content, status, handler, handle_result, closed_time, created_time from svc_feedback_t where tid = #{id} and tenant_id = #{tenantId}", { id: id, tenantId: tenantId })
    return view(saved[0])
}

exit 400, '不支持的 action：' + action
