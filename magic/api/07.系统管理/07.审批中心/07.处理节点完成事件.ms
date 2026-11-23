{
  "properties": {},
  "id": "c30f3718868148ce84e65217f49a7720",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "07.处理节点完成事件",
  "createTime": 1782955344570,
  "updateTime": 1784446270719,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/finish",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "仅接受 Warm-Flow 发出的已认证节点完成事件。核对当前行业的流程业务关联，在同一事务内推进皓月目录发布或当前提交版本的订阅范围，审批通过时批量生成待交付授权。重复决策幂等，关联或状态变化时回滚。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalFlow
import tableRead
import tableWrite
import tenantRuntime
import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.json.JSONUtil
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterException
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.UUID

// POST /sym/approval/finish 接收流程引擎的节点完成事件。引擎校验当前租户的业务关联后，
// 在同一事务内办理皓月发布或订阅并返回处理结果。重复决策直接返回，状态冲突或快照错配使本次处理回滚。
if (!(engineEvent instanceof ApprovalCallbackRequest)) {
    exit 403, '仅接受流程引擎完成事件'
}

var fail = (status, code, message) => { throw new AssetCenterException(status, code, message) }
var version = value => value == null ? 0 : (value + '')::long
var timestamp = value => value instanceof LocalDateTime ? Timestamp.valueOf(value) : value
var id = () => UUID.randomUUID().toString().replace('-', '')

return approvalFlow.finish(engineEvent, completion => {
    var tenant = tenantRuntime.id()
    var actor = completion.actor
    var businessId = completion.id
    var decision = completion.decision
    var now = new Timestamp(System.currentTimeMillis())
    var update = (table, key, keyValue, row, filters) => {
        var columns = new ArrayList(row.keySet())
        row[key] = keyValue
        if (tableWrite.batchUpdate(table, tenant, columns, [key], filters, [row]) != 1) {
            fail(409, 'INVALID_STATE', '审批业务记录已变化，请重新办理')
        }
    }
    if (completion.type == 'haoyuePublication') {
        var request = tableWrite.lockRow('res_catalog_publish_request', tenant, 'tid', businessId,
            ['publication_id', 'catalog_id', 'request_status'], {})
        if (request == null || request.publication_id == null) {
            fail(404, 'NOT_FOUND', '发布申请不存在')
        }
        if (decision == 1) {
            return true
        }
        var status = decision == 2 ? 'PUBLISHED' : (decision == 4 ? 'WITHDRAWN' : (decision == 5 ? 'RETURNED' : 'REJECTED'))
        if (status == request.request_status) {
            return true
        }
        if (request.request_status != 'IN_REVIEW') {
            fail(409, 'INVALID_STATE', '发布申请已经处理')
        }
        if (decision == 2) {
            var publication = tableWrite.lockRow('da_catalog_publication_t', tenant, 'tid', request.publication_id,
                ['status', 'request_id', 'catalog_id'], {})
            if (publication == null || publication.status != 'VALIDATED' || publication.request_id != businessId
                    || publication.catalog_id != request.catalog_id) {
                fail(409, 'INVALID_STATE', '发布版本不再满足审批条件')
            }
            var head = tableWrite.lockRow('da_catalog_publication_head_t', tenant, 'catalog_id', request.catalog_id, ['revision'], {})
            if (head == null) {
                fail(409, 'INVALID_STATE', '目录发布状态不存在')
            }
            update('da_catalog_publication_t', 'tid', request.publication_id,
                {status: 'PUBLISHED', published_at: now, published_by: actor, updated_time: now, updated_by: actor}, {})
            update('da_catalog_publication_head_t', 'catalog_id', request.catalog_id,
                {current_publication_id: request.publication_id, revision: version(head.revision) + 1, updated_time: now, updated_by: actor}, {})
        }
        // 申请行已锁定并校验为审批中，写入仍按当前租户及申请 ID 限定。
        update('res_catalog_publish_request', 'tid', businessId,
            {request_status: status, reviewed_time: now, published_time: decision == 2 ? now : null,
                reviewer_id: actor, updated_time: now, updated_by: actor}, {})
        return true
    }

    var form = tableWrite.lockRow('data_apply_form_t', tenant, 'tid', businessId,
        ['flow_status', 'submission_version', 'revision', 'apply_name', 'apply_user_id', 'apply_org_id'], {type: 'CATALOG_SUBSCRIPTION'})
    if (form == null) {
        fail(404, 'NOT_FOUND', '订阅申请不存在')
    }
    if (decision == 1 || version(form.flow_status) == decision) {
        return true
    }
    if (version(form.flow_status) != 1) {
        fail(409, 'INVALID_STATE', '订阅申请已经处理')
    }
    var filters = {apply_form_id: businessId, submission_version: version(form.submission_version), status: 'SUBMITTED'}
    var scopes = tableWrite.lockByFilter('da_apply_scope_t', tenant, 'tid',
        ['tid', 'resource_snapshot_id', 'catalog_id', 'publication_id', 'delivery_channel',
            'requested_scope', 'requested_from', 'requested_until', 'revision'], filters, 100)

    var grants = []
    var apiAuthorizations = []
    var distributions = []
    if (decision == 2 && !scopes.isEmpty()) {
        // 先批量检查已生成授权，在同一租户范围预取本轮需要的快照。逐范围验证快照及其发布版本。
        var existing = tableRead.from('da_authorization_scope_t', 'a', tenant)
        var applyScopeId = tableRead.column('a', 'apply_scope_id', 'TEXT')
        existing.where(tableRead.in(applyScopeId, scopes.map(scope => scope.tid)))
        var granted = new LinkedHashSet(existing.grouped({tid: applyScopeId}, [applyScopeId]).map(row => row.tid))
        var eligible = scopes.filter(scope => {
            var ends = timestamp(scope.requested_until)
            var starts = timestamp(scope.requested_from)
            return !granted.contains(scope.tid) && ends != null && ends.after(starts != null && starts.after(now) ? starts : now)
        })
        if (!eligible.isEmpty()) {
            var query = tableRead.from('da_catalog_resource_snapshot_t', 's', tenant)
            var key = tableRead.column('s', 'tid', 'TEXT')
            var expected = new ArrayList(new LinkedHashSet(eligible.map(scope => scope.resource_snapshot_id)))
            query.where(tableRead.in(key, expected))
            var byId = {}
            for (row in query.rows({tid: key, publicationId: tableRead.column('s', 'publication_id', 'TEXT'),
                    contract: tableRead.column('s', 'contract_snapshot', 'TEXT')}, expected.size())) {
                byId[row.tid] = row
            }
            for (scope in eligible) {
                var snapshot = byId[scope.resource_snapshot_id]
                if (snapshot == null || snapshot.publicationId != scope.publication_id) {
                    fail(409, 'RESOURCE_CHANGED', '交付资源快照不存在或不属于该发布版本')
                }
                var resource = JSONUtil.parseObj(snapshot.contract).resource
                var api = scope.delivery_channel == 'API'
                if (resource == null || resource.id == null || (api && resource.kind != 'API')) {
                    fail(409, 'RESOURCE_CHANGED', '交付资源类型不匹配')
                }
                var deliveryId = id()
                if (api) {
                    apiAuthorizations.add({tid: deliveryId, tenant_id: tenant, apply_form_id: businessId, catalog_id: scope.catalog_id,
                        api_id: resource.id, apply_user_id: form.apply_user_id, authorization_status: 'PENDING',
                        gateway_sync_status: 'PENDING', authorization_scope: 'SCOPED_PENDING', gateway_message: '待完成范围校验与网关同步',
                        authorized_time: now, expire_time: timestamp(scope.requested_until), created_by: actor, created_time: now,
                        updated_by: actor, updated_time: now, is_del: 0})
                } else {
                    distributions.add({tid: deliveryId, tenant_id: tenant, apply_form_id: businessId, catalog_id: scope.catalog_id,
                        catalog_name: form.apply_name, source_table_id: resource.id, source_table_name: resource.name,
                        task_code: 'HY-' + deliveryId, task_name: form.apply_name + '受控交付', task_status: 'GENERATED', progress: 0,
                        execution_engine: 'PENDING_INTEGRATION', execution_mode: 'PENDING_INTEGRATION', status_message: '审批通过，等待受控交付引擎接管',
                        created_by: actor, created_time: now, updated_by: actor, updated_time: now, is_del: 0})
                }
                var starts = timestamp(scope.requested_from)
                grants.add({tid: id(), tenant_id: tenant, apply_scope_id: scope.tid, authorization_version: 1,
                    publication_id: scope.publication_id, resource_snapshot_id: scope.resource_snapshot_id, delivery_channel: scope.delivery_channel,
                    api_authorization_id: api ? deliveryId : null, distribution_task_id: api ? null : deliveryId,
                    grantee_user_id: form.apply_user_id, grantee_org_id: form.apply_org_id, scope_snapshot: scope.requested_scope,
                    valid_from: starts != null && starts.after(now) ? starts : now, valid_until: timestamp(scope.requested_until),
                    status: 'PENDING', idempotency_key: DigestUtil.sha256Hex(tenant + ':' + scope.tid + ':approval'),
                    created_by: actor, created_time: now, updated_by: actor, updated_time: now, revision: 0})
            }
        }
    }

    var scopeStatus = decision == 2 ? 'APPROVED' : (decision == 4 ? 'WITHDRAWN' : 'REJECTED')
    var scopeUpdates = scopes.map(scope => { return {tid: scope.tid, status: scopeStatus,
        approved_scope: decision == 2 ? scope.requested_scope : null, decided_at: decision == 2 ? now : null,
        revision: version(scope.revision) + 1, updated_time: now, updated_by: actor} })
    if (tableWrite.batchUpdate('da_apply_scope_t', tenant,
            ['status', 'approved_scope', 'decided_at', 'revision', 'updated_time', 'updated_by'], ['tid'],
            {apply_form_id: businessId, submission_version: filters.submission_version}, scopeUpdates) != scopes.size()) {
        fail(409, 'INVALID_STATE', '订阅申请授权范围已变化，请重新办理')
    }
    if (!apiAuthorizations.isEmpty()) {
        db.table('data_api_authorization_t').primary('tid').withBlank().batchInsert(apiAuthorizations, 100)
    }
    if (!distributions.isEmpty()) {
        db.table('data_distribution_task_t').primary('tid').withBlank().batchInsert(distributions, 100)
    }
    if (!grants.isEmpty()) {
        db.table('da_authorization_scope_t').primary('tid').withBlank().batchInsert(grants, 100)
    }
    update('data_apply_form_t', 'tid', businessId,
        {flow_status: decision, revision: (version(form.revision) + 1) + '', updated_time: now, updated_by: actor}, {type: 'CATALOG_SUBSCRIPTION'})
    return true
})
