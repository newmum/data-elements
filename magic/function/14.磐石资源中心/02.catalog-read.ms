{
  "properties" : { },
  "id" : "084a92c02bd05f8bb8507cc4c4660e40",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "02.catalog-read",
  "createTime" : null,
  "updateTime" : 1791030855462,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/catalog-read",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 03.读取数据中心编目及兼容的历史交付台账。编目和审核状态以 da_catalog_t 为准。
import tenantRuntime
import jsons
import cn.dev33.satoken.stp.StpUtil
import java.time.Instant
import '@post:/sym/user/me' as currentUser

var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var user = currentUser()
var orgId = user.orgId == null ? '' : user.orgId + ''
var text = value => value == null ? '' : value + ''
var val = (row, name) => row == null ? null : row[name] == null ?
    row[name.toLowerCase()] == null ? row[name.toUpperCase()] : row[name.toLowerCase()] : row[name]
var parse = value => text(value) == '' ? {} : jsons.toJSONObject(value)
var mode = input == null ? '' : text(input.mode)
var id = input == null ? '' : text(input.id)
var structure = tableId => {
    var table = db.selectOne("""
        SELECT tid AS id, datasource_id AS sourceId, table_name AS name,
               table_name_cn AS displayName, table_comment AS comment, updated_time AS updatedAt
          FROM db_table_t WHERE tenant_id = #{tenantId} AND tid = #{tableId} AND is_del = 0
    """)
    if (table == null) { exit 404, '来源表已删除或不属于当前租户' }
    var fields = db.select("""
        SELECT tid AS id, column_name AS name, column_comment AS comment, data_type AS nativeType,
               length, scale, nullable, primary_key AS primaryKey, ordinal_position AS ordinal, is_masking AS masking
          FROM db_table_column_t WHERE tenant_id = #{tenantId} AND table_id = #{tableId} AND is_del = 0
         ORDER BY ordinal_position, tid
    """)
    for (field in fields) { field.path = field.name; field.typeFamily = field.nativeType }
    table.fields = fields
    table.kind = 'table'
    table.keys = []
    return table
}
if (mode == 'structure') { return structure(id) }
// 数据中心目录只关联已纳入分层/分库规划的数据表。审核状态以目录主表为准，
// 不再从资源发布申请或 resourceCenterDraft 属性推断。
var catalogRows = db.select("""
    SELECT c.tid AS id, c.catalog_name AS name, c.catalog_name_en AS code,
           c.asset_desc AS summary, c.org_id AS provider,
           c.source_table_id AS resourceId, c.source_table_name AS resourceName,
           c.db_id AS sourceId, c.asset_status AS assetStatus,
           c.flow_status AS flowStatus,
           COALESCE(c.resource_revision_no, 1) AS version,
           c.created_by AS createdBy, c.created_time AS createdAt,
           c.updated_by AS updatedBy, c.updated_time AS updatedAt
      FROM da_catalog_t c
     WHERE c.tenant_id = #{tenantId} AND c.is_del = 0
       AND (#{mode} != 'one' OR c.tid = #{id})
       AND EXISTS (
           SELECT 1 FROM db_table_t t
           JOIN dwm_center_layer_source_t b
             ON b.datasource_id = t.datasource_id AND b.tenant_id = t.tenant_id
            AND b.is_del = 0 AND b.target_type IN ('layer', 'domain')
           WHERE t.tid = c.source_table_id AND t.tenant_id = c.tenant_id AND t.is_del = 0
       )
     ORDER BY c.updated_time DESC, c.tid DESC
""")
var requestRows = db.select("""
    SELECT tid AS id, catalog_id AS catalogId, request_status AS status, snapshot_json AS snapshot,
           revision_number AS revisionNumber, revision_no AS version, created_by AS submittedBy,
           created_time AS submittedAt, reviewer_id AS reviewer, reviewed_time AS reviewedAt,
           review_comment AS opinion, published_time AS publishedAt, offline_reason AS reason,
           sync_status AS busState, updated_time AS updatedAt
      FROM res_catalog_publish_request WHERE tenant_id = #{tenantId} AND is_del = 0 AND snapshot_json IS NOT NULL
     ORDER BY revision_number
""")
var requests = []
var publications = []
var rowById = {}
var toRevision = row => {
    var revision = parse(row.snapshot)
    revision.id = text(row.id)
    revision.number = row.revisionNumber
    revision.state = row.status == 'PENDING' ? 'PENDING_REVIEW' : row.status == 'REJECTED' ? 'RETURNED' :
        ['APPROVED','PUBLISHED','OFFLINE'].contains(row.status) ? 'APPROVED' : 'SUPERSEDED'
    revision.submittedBy = row.submittedBy
    revision.submittedAt = row.submittedAt
    revision.reviewer = row.reviewer
    revision.reviewedAt = row.reviewedAt
    revision.opinion = row.opinion
    if (revision.checks == null) { revision.checks = [] }
    return revision
}
for (row in requestRows) {
    rowById[text(row.id)] = row
    requests.add(row)
    if (['PUBLISHED','OFFLINE'].contains(row.status)) {
        var revision = toRevision(row)
        var allowed = revision.visibility != 'DEPARTMENTS' || revision.provider == orgId ||
            (revision.visibleDepartments != null && revision.visibleDepartments.contains(orgId))
        if (allowed) {
            publications.add({id: text(row.id), entryId: text(row.catalogId), revisionId: text(row.id),
                revision: revision, state: row.status == 'PUBLISHED' ? 'PUBLISHED' : 'OFFLINE',
                version: row.version, busState: row.busState == null ? 'NOT_CONFIGURED' : row.busState,
                publishedBy: row.reviewer, reason: row.reason, createdAt: row.publishedAt, updatedAt: row.updatedAt})
        }
    }
}
if (mode == 'publication') {
    for (publication in publications) { if (publication.id == id) { return publication } }
    exit 404, '发布版本不存在或当前部门不可见'
}
var itemRows = db.select("""
    SELECT i.catalog_id AS catalogId, i.tid AS itemId,
           i.source_table_column_id AS fieldId, i.col_name AS label
      FROM da_catalog_item_t i
      JOIN da_catalog_t c ON c.tid = i.catalog_id AND c.tenant_id = i.tenant_id AND c.is_del = 0
     WHERE i.tenant_id = #{tenantId} AND i.is_del = 0
       AND EXISTS (
           SELECT 1 FROM db_table_t t
           JOIN dwm_center_layer_source_t b ON b.datasource_id = t.datasource_id
             AND b.tenant_id = t.tenant_id AND b.is_del = 0 AND b.target_type IN ('layer','domain')
           WHERE t.tid = c.source_table_id AND t.tenant_id = c.tenant_id AND t.is_del = 0
       )
     ORDER BY i.catalog_id, i.sort_no
""")
var itemsByCatalog = {}
for (item in itemRows) {
    var key = text(val(item, 'catalogId'))
    if (itemsByCatalog[key] == null) { itemsByCatalog[key] = [] }
    itemsByCatalog[key].add(item)
}
var catalogs = []
for (row in catalogRows) {
    var items = itemsByCatalog[text(row.id)] == null ? [] : itemsByCatalog[text(row.id)]
    var fieldIds = []
    var labels = {}
    for (item in items) {
        var fieldId = text(val(item, 'fieldId'))
        if (fieldId == '') { fieldId = text(val(item, 'itemId')) }
        if (fieldId != '') { fieldIds.add(fieldId); labels[fieldId] = val(item, 'label') }
    }
    var assetStatus = val(row, 'assetStatus')
    var flowStatus = val(row, 'flowStatus')
    var state = text(flowStatus) == '1' ? 'PENDING_REVIEW' :
        text(flowStatus) == '3' ? 'RETURNED' :
        text(assetStatus) == '2' && text(flowStatus) == '2' ? 'APPROVED' : 'DRAFT'
    var draft = {id:text(val(row, 'id')), number:val(row, 'version'), state:state,
        name:text(val(row, 'name')), code:text(val(row, 'code')), summary:text(val(row, 'summary')),
        provider:text(val(row, 'provider')), contact:'', resourceId:text(val(row, 'resourceId')),
        resourceName:text(val(row, 'resourceName')), sourceId:text(val(row, 'sourceId')),
        assetStatus:assetStatus, flowStatus:flowStatus,
        submittedBy:val(row, 'updatedBy'), submittedAt:state == 'PENDING_REVIEW' ? val(row, 'updatedAt') : null,
        classificationId:'', keywords:[], frequency:'ONCE', shareMode:'NONE', conditions:'',
        visibility:'WORKSPACE', visibleDepartments:[], format:'CSV',
        fieldIds:fieldIds, fieldLabels:labels, note:'', checks:[]}
    var entry = {id:text(val(row, 'id')), version:val(row, 'version'), draft:draft, revisions:[],
        createdAt:val(row, 'createdAt'), updatedAt:val(row, 'updatedAt'), createdBy:val(row, 'createdBy')}
    catalogs.add(entry)
    if (mode == 'one' && entry.id == id) { return entry }
}
if (mode == 'one') { exit 404, '资源目录不存在' }
var formRows = db.select("""
    SELECT tid AS id, apply_user_id AS applicantId, apply_org_id AS department,
           resource_scope_json AS scope, resource_revision_no AS version,
           created_time AS createdAt, updated_time AS updatedAt
      FROM data_apply_form_t WHERE tenant_id = #{tenantId} AND is_del = 0 AND resource_scope_json IS NOT NULL
""")
var subscriptions = []
var allowedSubs = []
for (row in formRows) {
    var sub = parse(row.scope)
    var req = rowById[text(sub.publicationId)]
    var provider = req == null ? '' : text(parse(req.snapshot).provider)
    if (row.applicantId != actorId && provider != orgId) { continue }
    sub.putAll({id: text(row.id), applicantId: row.applicantId, department: row.department,
        version: row.version, createdAt: row.createdAt, updatedAt: row.updatedAt})
    if (['ACTIVE','SCHEDULED'].contains(sub.state)) {
        if (Instant.parse(sub.validTo).toEpochMilli() <= new Date().getTime()) { sub.state = 'EXPIRED' }
        else if (req == null || req.status != 'PUBLISHED') { sub.state = 'PAUSED' }
        else if (Instant.parse(sub.validFrom).toEpochMilli() <= new Date().getTime()) { sub.state = 'ACTIVE' }
    }
    if (mode == 'subscription' && sub.id == id) { return sub }
    subscriptions.add(sub)
    allowedSubs.add(sub.id)
}
if (mode == 'subscription') { exit 404, '订阅不存在或无权查看' }
var deliveries = []
var taskRows = db.select("""
    SELECT tid AS id, apply_form_id AS subscriptionId, resource_result_json AS result,
           task_status AS state, status_message AS error, created_time AS createdAt, updated_time AS updatedAt
      FROM data_distribution_task_t WHERE tenant_id = #{tenantId} AND is_del = 0
       AND execution_engine = 'RESOURCE_EXPORT' ORDER BY created_time DESC
""")
for (row in taskRows) {
    if (!allowedSubs.contains(text(row.subscriptionId))) { continue }
    var result = parse(row.result)
    result.putAll({id: text(row.id), subscriptionId: text(row.subscriptionId), state: row.state,
        error: row.error, version: 1, createdAt: row.createdAt, updatedAt: row.updatedAt})
    if (mode == 'delivery' && row.id == id) { return result }
    result.remove('content')
    deliveries.add(result)
}
if (mode == 'delivery') { exit 404, '交付批次不存在或无权查看' }
return {catalogs: catalogs, publications: publications, subscriptions: subscriptions, deliveries: deliveries,
    autoApprove: false, busConfigured: false, deliveryScheduling: false,
    actor: {id: actorId, orgId: orgId, name: user.name},
    capabilities: {scheduledDelivery: false, manualDelivery: true, crossNetworkSync: false, autoApprove: false}}
