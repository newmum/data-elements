{
  "properties": {},
  "id": "241456b9741748059d853cdbb4e12461",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "13.资源申请提交",
  "createTime": 1782955344174,
  "updateTime": 1784437771774,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/submit",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"flowOrderIds\": [\"validation-market-form-1\", \"validation-market-form-2\"],\n    \"dryRun\": true\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"dryRun\": true,\n        \"valid\": true,\n        \"flowOrderIds\": [\"validation-market-form-1\", \"validation-market-form-2\"]\n    }\n}",
  "description": "提交本人在当前租户的超市订阅申请。支持单个 flowOrderId 或最多 100 个 flowOrderIds，dryRun 只校验。全部申请均符合提交条件后一次更新状态，提交后批量同步索引。返回单笔生效结果或批量 items。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import org.springframework.data.elasticsearch.core.ElasticsearchOperations as esOperations
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.UpdateQuery
import java.util.LinkedHashSet
import "@post:/sym/user/me" as me
import jsons
import log
import tenantRuntime

// POST /dws/market/submit：用 flowOrderId 或最多 100 个 flowOrderIds 提交本人在当前租户的超市订阅申请。
// dryRun 仅校验。正式提交整批统一生效，提交后同步索引；返回单笔结果或按传入顺序返回 items。
if (!(body instanceof Map)) { exit 400, '请提供要提交的申请单号' }
var batch = body.containsKey('flowOrderIds')
if (batch && body.containsKey('flowOrderId')) { exit 400, '请选择单笔或批量提交方式' }
var submittedIds = batch ? body.flowOrderIds : [body.flowOrderId]
if (!(submittedIds instanceof List) || submittedIds.size() == 0 || submittedIds.size() > 100) {
    exit 400, '一次提交需要 1 至 100 个申请单号'
}
if (body.containsKey('dryRun') && !(body.dryRun instanceof Boolean)) {
    exit 400, 'dryRun 必须为布尔值'
}
var ids = []
var uniqueIds = new LinkedHashSet()
for (submittedId in submittedIds) {
    if (!(submittedId instanceof String) && (batch || !(submittedId instanceof Number))) {
        exit 400, '申请单号格式不正确'
    }
    var id = (submittedId + '').trim()
    if (id == '' || id.length() > 32) { exit 400, '申请单号格式不正确' }
    if (!uniqueIds.add(id)) { exit 400, '申请单号不能重复' }
    ids.add(id)
}

var tenantId = tenantRuntime.id()
if (tenantId == null || (tenantId + '').trim() == '') { exit 401, '当前租户会话无效' }
var actor = me()?.id
if (actor == null || (actor + '').trim() == '') { exit 401, '当前用户会话无效' }
var marketType = 'catalogSubscribe'
var pendingStatuses = [0, 4, 5]

// 每个 ID 都必须属于当前申请人，且只能提交超市订阅单。资产中心的审批申请使用其他类型。
// 早期申请单的申请人可能未填写，此时按个人中心同一规则匹配创建人。
var ownedMarketForms = table => table.where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
    .eq('type', marketType)
    .and(owner => owner.eq('apply_user_id', actor).or()
        .and(legacy => legacy.eq('created_by', actor)
            .and(missing => missing.isNull('apply_user_id').or().eq('apply_user_id', ''))))
var forms = ownedMarketForms(db.table('data_apply_form_t').columns('tid', 'type', 'flow_status', 'updated_time'))
    .in('tid', ids)
    .select()
var formsById = {}
for (form in forms) { formsById[form.tid] = form }
var pendingIds = []
for (id in ids) {
    var form = formsById[id]
    if (form == null || !marketType.equals(form.type)) { exit 404, '部分申请单不存在或不可提交，请刷新后重试' }
    // 草稿可继续申请，撤回和驳回可修改后重提。已通过的申请保留原生效时间。
    var status = form.flowStatus == null ? null : form.flowStatus::int
    if (status == 2) { continue }
    if (!pendingStatuses.contains(status)) { exit 409, '部分申请单状态已变化，请刷新后重试' }
    pendingIds.add(id)
}
if (body.dryRun == true) { return {dryRun: true, valid: true, flowOrderIds: ids} }

var submittedAt = new Date()
if (pendingIds.size() > 0) {
    db.transaction(() => {
        // 重验更新条件。若其他请求已改变任一申请单，整批回滚并让调用方刷新后重试。
        var updated = ownedMarketForms(db.table('data_apply_form_t'))
            .in('tid', pendingIds)
            .in('flow_status', pendingStatuses)
            .update({flow_status: 2, updated_time: submittedAt, updated_by: actor})
        if (updated != pendingIds.size()) { exit 409, '部分申请单状态已变化，请刷新后重试' }
    })
}

try {
    // 业务事务提交后，一次部分更新租户索引，保留原有申请字段；重试可以补同步。
    var physicalIndex = esCommonService.ensureTenantIndexReady('dataassets_apply_form')
    var updates = ids.map(id => {
        var updatedTime = (formsById[id].flowStatus::int == 2) ? formsById[id].updatedTime : submittedAt
        var fields = jsons.toJSONObject(jsons.toJSONStr({id: id, tid: id, flowStatus: 2, updatedTime: updatedTime}))
        return UpdateQuery.builder(id).withDocument(Document.from(fields)).withDocAsUpsert(true).build()
    })
    esOperations.bulkUpdate(updates, IndexCoordinates.of(physicalIndex))
} catch (error) {
    log.warn('同步超市申请状态索引失败，本次申请单数：{}', ids.size())
}

var items = ids.map(id => { return {flowOrderId: id, flowStatus: 2, directGranted: true} })
return batch ? {items: items} : items[0]
