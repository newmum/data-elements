{
  "properties" : { },
  "id" : "0454d01c38ab5d66aafdaf02d9b547d6",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "01.catalog-command",
  "createTime" : null,
  "updateTime" : 1791031451415,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/catalog-command",
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
// 04.数据中心目录编目：复用原目录保存接口，审核状态由 da_catalog_t 控制。
// 旧发布与订阅路由仅保留兼容读取，不参与编目审核状态。
import tenantRuntime
import jsons
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import log
import java.time.Instant
import java.lang.Math
import '@/resource-center/catalog-read' as readCatalog
import '@/resource-center/planning-read' as readPlanning
import '@post:/dst/catalog/saveOrUpdate' as saveCatalog
import '@post:/dst/catalog/detail' as detailCatalog
import '@post:/dst/database/metadata/table/sample-data' as sampleData
import '@post:/sym/user/me' as currentUser

var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var user = currentUser()
var orgId = user.orgId == null ? '' : user.orgId + ''
var text = value => value == null ? '' : (value + '').trim()
var val = (row, name) => row == null ? null : row[name] == null ?
    row[name.toLowerCase()] == null ? row[name.toUpperCase()] : row[name.toLowerCase()] : row[name]
var parse = value => text(value) == '' ? {} : jsons.toJSONObject(value)
var payload = input.body == null ? {} : input.body
var kind = text(input.kind)
var id = text(input.id)
var action = text(input.action)
var method = text(input.method).toUpperCase()
var now = new Date()
var required = (value, title, max) => {
    var result = text(value)
    if (result == '' || result.length() > max) { exit 400, title + '不能为空或超过长度限制' }
    return result
}
var epoch = value => {
    try { return Instant.parse(text(value)).toEpochMilli() }
    catch (error) { exit 400, '请提供带时区的有效日期时间' }
}
var checkVersion = row => {
    if (input.version == null || text(input.version) != text(row.version)) { exit 409, '记录已更新，请刷新后重试' }
}
var organization = value => {
    var key = required(value, '部门', 32)
    var result = db.selectOne("""SELECT id, name FROM rm_org_t
        WHERE tenant_id = #{tenantId} AND id = #{key} AND deleted = 0""")
    if (result == null) { exit 400, '请选择当前租户的有效部门' }
    return result
}
var ownProvider = provider => {
    if (orgId == '' || orgId != text(provider)) { exit 403, '只允许资源提供部门维护或审核此目录' }
}
var writeDraft = (catalogId, draft) => {
    var old = db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('parent_id', catalogId)
        .eq('data_type','catalog').eq('prop_name','resourceCenterDraft').eq('is_del',0).selectOne()
    if (old == null) {
        db.table('da_prop_t').primary('tid').insert({tid: IdUtil.getSnowflakeNextIdStr(), tenant_id: tenantId,
            parent_id: catalogId, data_type: 'catalog', prop_name: 'resourceCenterDraft', prop_type: '0',
            prop_value: jsons.toJSONStr(draft), is_del: 0, created_time: now, updated_time: now,
            created_by: actorId, updated_by: actorId})
    } else {
        db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('tid',old.tid)
            .update({prop_value: jsons.toJSONStr(draft), updated_time: now, updated_by: actorId})
    }
}
var bumpCatalog = (catalogId, version) => {
    var changed = db.table('da_catalog_t').where().eq('tid',catalogId).eq('tenant_id',tenantId)
        .eq('resource_revision_no',version).eq('is_del',0)
        .update({resource_revision_no: version + 1, updated_time: now, updated_by: actorId})
    if (changed != 1) { exit 409, '目录已被更新，请刷新' }
}
var validate = (draft, freeze) => {
    for (key in ['name','code','summary','provider','contact','resourceId','classificationId']) {
        if (text(draft[key]) == '') { exit 400, '目录必填信息不完整：' + key }
    }
    organization(draft.provider)
    var classes = readPlanning({}).classifications
    var hasClass = false
    for (item in classes) { if (item.id == draft.classificationId) { hasClass = true } }
    if (!hasClass) { exit 400, '请选择有效的资源目录分类' }
    if (!['CSV','JSON'].contains(draft.format)) { exit 400, '请选择 CSV 或 JSON 交付格式' }
    if (!['ONCE','DAILY','WEEKLY','MONTHLY'].contains(draft.frequency)) { exit 400, '更新频率无效' }
    if (!['CONDITIONAL','UNCONDITIONAL','NONE'].contains(draft.shareMode)) { exit 400, '共享方式无效' }
    if (draft.shareMode != 'UNCONDITIONAL' && text(draft.conditions) == '') { exit 400, '请填写共享条件或不共享原因' }
    if (!['WORKSPACE','DEPARTMENTS'].contains(draft.visibility)) { exit 400, '可见范围无效' }
    if (draft.visibility == 'DEPARTMENTS') {
        if (draft.visibleDepartments == null || draft.visibleDepartments.isEmpty()) { exit 400, '请选择可见部门' }
        for (department in draft.visibleDepartments) { organization(department) }
    }
    var structure = readCatalog({mode: 'structure', id: draft.resourceId})
    var fieldById = {}
    for (field in structure.fields) { fieldById[text(field.id)] = field }
    if (draft.fieldIds == null || draft.fieldIds.isEmpty() || draft.fieldIds.size() > 500) { exit 400, '请选择 1 至 500 个数据项' }
    var seen = []
    var labels = []
    for (fieldId in draft.fieldIds) {
        var field = fieldById[text(fieldId)]
        if (field == null || seen.contains(fieldId)) { exit 400, '数据项不属于来源表或重复选择' }
        if (text(field.name).toLowerCase().matches('.*(password|passwd|secret|token|private_key|internal_note).*')) {
            exit 400, '交付范围包含禁止公开的凭据或内部字段'
        }
        var label = draft.fieldLabels == null ? '' : text(draft.fieldLabels[text(fieldId)])
        if (label == '') { label = text(field.comment) == '' ? field.name : field.comment }
        if (labels.contains(label)) { exit 400, '对外数据项名称不能重复' }
        labels.add(label); seen.add(fieldId)
    }
    if (draft.structure != null && !freeze) {
        for (prior in draft.structure.fields) {
            if (!draft.fieldIds.contains(prior.id)) { continue }
            var latest = fieldById[text(prior.id)]
            if (latest == null || text(latest.name) != text(prior.name) || text(latest.nativeType) != text(prior.nativeType) ||
                text(latest.length) != text(prior.length) || text(latest.scale) != text(prior.scale)) {
                exit 409, '批准的数据项结构已变化，请重新编制并审核目录版本'
            }
        }
    }
    return structure
}
var saveSub = (sub, version, flowStatus) => {
    var changed = db.table('data_apply_form_t').where().eq('tid',sub.id).eq('tenant_id',tenantId)
        .eq('resource_revision_no',version).eq('is_del',0).update({resource_scope_json: jsons.toJSONStr(sub),
            resource_revision_no: version + 1, flow_status: flowStatus, updated_time: now, updated_by: actorId})
    if (changed != 1) { exit 409, '订阅已被修改，请刷新后重试' }
}
var syncCatalogIndex = catalogId => {
    try {
        var previousBody = body
        var projection = null
        try { body = {tid: catalogId}; projection = detailCatalog() }
        finally { body = previousBody }
        esCommonService.saveOrUpdate('dataassets', catalogId, jsons.toJSONStr(projection), false)
    } catch (error) { log.warn('catalog search projection refresh failed, tid: {}, error: {}', catalogId, error.message) }
}
var writeAuditNote = (catalogId, opinion) => {
    var old = db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('parent_id', catalogId)
        .eq('data_type', 'catalog').eq('prop_name', 'resourceCenterAuditOpinion').eq('is_del', 0).selectOne()
    if (old == null) {
        db.table('da_prop_t').primary('tid').insert({tid:IdUtil.getSnowflakeNextIdStr(), tenant_id:tenantId,
            parent_id:catalogId, data_type:'catalog', prop_name:'resourceCenterAuditOpinion', prop_type:'0',
            prop_value:opinion, is_del:0, created_time:now, updated_time:now, created_by:actorId, updated_by:actorId})
    } else {
        db.table('da_prop_t').where().eq('tenant_id',tenantId).eq('tid',old.tid)
            .update({prop_value:opinion, updated_time:now, updated_by:actorId})
    }
}
if (kind == 'policy') { exit 409, '目录审核必须由提供部门办理；跨网同步尚未配置真实连接，不允许启用模拟结果' }
if (kind == 'catalogs') {
    var entry = id == '' ? null : readCatalog({mode:'one', id:id})
    if (method == 'GET') { return entry }
    if (entry != null) { checkVersion(entry) }
    if (action == '' && ['POST','PUT'].contains(method)) {
        if (entry != null && !['DRAFT','RETURNED'].contains(entry.draft.state)) {
            exit 409, '请先撤回审核或重新编目后再修改目录'
        }
        var props = payload.propList
        if (props == null) { exit 400, '请填写目录编目信息' }
        required(props.catalogName, '目录名称', 255)
        required(props.orgId, '来源部门', 32)
        var tableId = required(props.sourceTableId, '数据中心表', 32)
        var selectedTable = db.selectOne("""
            SELECT t.tid, t.datasource_id AS sourceId, t.table_name AS tableName FROM db_table_t t
            JOIN dwm_center_layer_source_t b ON b.datasource_id = t.datasource_id
             AND b.tenant_id = t.tenant_id AND b.is_del = 0 AND b.target_type IN ('layer','domain')
            WHERE t.tenant_id = #{tenantId} AND t.tid = #{tableId} AND t.is_del = 0 LIMIT 1
        """)
        if (selectedTable == null) { exit 400, '请选择数仓规划已关联数据源中的数据表' }
        var existingCatalog = db.selectOne("""
            SELECT tid FROM da_catalog_t
             WHERE tenant_id = #{tenantId} AND source_table_id = #{tableId}
               AND is_del = 0 AND (#{id} = '' OR tid <> #{id}) LIMIT 1
        """)
        if (existingCatalog != null) { exit 409, '这张数据表已有目录，请打开现有目录编目' }
        props.dbId = val(selectedTable,'sourceId')
        props.sourceTableName = val(selectedTable,'tableName')
        if (payload.catalogItems == null || payload.catalogItems.isEmpty()) { exit 400, '请至少选择一项数据表字段' }
        for (item in payload.catalogItems) {
            required(item.colName, '数据项名称', 255)
            required(item.colEn, '数据项英文名', 255)
            required(item.colType, '数据项类型', 255)
        }
        if (payload.dryRun == true) {
            return {dryRun:true,sourceTableId:tableId,sourceId:val(selectedTable,'sourceId'),
                itemCount:payload.catalogItems.size()}
        }
        var previousBody = body
        var saved = null
        try {
            body = {tid:id == '' ? null : id, assetType:'catalog', reviewRequired:true,
                propList:props, catalogItems:payload.catalogItems}
            saved = saveCatalog()
        } finally { body = previousBody }
        var catalogId = text(saved.tid) == '' ? text(saved.id) : text(saved.tid)
        if (catalogId == '') { exit 500, '目录保存未返回有效标识' }
        return readCatalog({mode:'one', id:catalogId})
    }
    if (entry == null) { exit 404, '资源目录不存在' }
    if (action == 'check') {
        var count = db.table('da_catalog_item_t').where().eq('tenant_id',tenantId)
            .eq('catalog_id',id).eq('is_del',0).count()
        return {id:id, checks:count > 0 ? [] : [{code:'items',level:'ERROR',message:'请先编制目录数据项'}]}
    }
    if (action == 'submit') {
        if (!['DRAFT','RETURNED'].contains(entry.draft.state)) { exit 409, '当前目录不可提交审核' }
        var count = db.table('da_catalog_item_t').where().eq('tenant_id',tenantId)
            .eq('catalog_id',id).eq('is_del',0).count()
        if (count == 0) { exit 400, '请先编制目录数据项' }
        var expected = entry.version
        var changed = db.update("""
            UPDATE da_catalog_t SET asset_status=1, flow_status=1,
                resource_revision_no=#{expected}+1, updated_time=#{now}, updated_by=#{actorId}
             WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0
               AND COALESCE(resource_revision_no,1)=#{expected} AND COALESCE(flow_status,0) IN (0,3,4)
        """, {expected:expected,now:now,actorId:actorId,tenantId:tenantId,id:id})
        if (changed != 1) { exit 409, '目录状态已变化，请刷新' }
        syncCatalogIndex(id)
        return {id:id,state:'PENDING_REVIEW'}
    }
    if (action == 'review' || action == 'withdraw') {
        if (entry.draft.state != 'PENDING_REVIEW') { exit 409, '目录不在审核中' }
        if (action == 'review' && text(entry.draft.submittedBy) == actorId) { exit 403, '不可审核本人提交的目录' }
        if (action == 'withdraw' && text(entry.draft.submittedBy) != actorId) { exit 403, '只允许提交人撤回' }
        var approve = action == 'review' && payload.approve == true
        var opinion = action == 'withdraw' ? '提交人撤回' : text(payload.opinion)
        if (action == 'review' && !approve) { opinion = required(payload.opinion,'退回原因',1000) }
        var changed = db.transaction(() => {
            var assetStatus = action == 'withdraw' || !approve ? 0 : 2
            var flowStatus = action == 'withdraw' ? 4 : approve ? 2 : 3
            var expected = entry.version
            var updated = db.update("""
                UPDATE da_catalog_t SET asset_status=#{assetStatus}, flow_status=#{flowStatus},
                    resource_revision_no=#{expected}+1, updated_time=#{now}, updated_by=#{actorId}
                 WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0
                   AND flow_status=1 AND COALESCE(resource_revision_no,1)=#{expected}
            """, {assetStatus:assetStatus,flowStatus:flowStatus,expected:expected,
                now:now,actorId:actorId,tenantId:tenantId,id:id})
            if (updated != 1) { exit 409, '审核状态已变化，请刷新' }
            writeAuditNote(id, opinion)
            return updated
        })
        syncCatalogIndex(id)
        return {id:id,approved:approve,changed:changed}
    }
    if (action == 'new-version') {
        if (entry.draft.state != 'APPROVED') { exit 409, '只有已审核目录可以重新编目' }
        var expected = entry.version
        var changed = db.update("""
            UPDATE da_catalog_t SET asset_status=0, flow_status=0,
                resource_revision_no=#{expected}+1, updated_time=#{now}, updated_by=#{actorId}
             WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0
               AND flow_status=2 AND asset_status=2 AND COALESCE(resource_revision_no,1)=#{expected}
        """, {expected:expected,now:now,actorId:actorId,tenantId:tenantId,id:id})
        if (changed != 1) { exit 409, '目录已更新，请刷新' }
        syncCatalogIndex(id)
        return {id:id,state:'DRAFT'}
    }
    exit 400, '不支持的目录动作'
}
if (kind == 'publications') {
    if (id == '' && method == 'POST') {
        var entry = readCatalog({mode:'one',id:required(payload.entryId,'目录',32)})
        ownProvider(entry.draft.provider)
        var revisionId = required(payload.revisionId,'版本',32)
        if (entry.draft.id != revisionId || entry.draft.state != 'APPROVED') { exit 409, '只能发布当前已批准的冻结版本' }
        validate(entry.draft,false)
        var changed = db.table('res_catalog_publish_request').where().eq('tenant_id',tenantId).eq('tid',revisionId)
            .eq('request_status','APPROVED').eq('is_del',0).update({request_status:'PUBLISHED',published_time:now,updated_time:now,updated_by:actorId,revision_no:2})
        if (changed != 1) { exit 409, '该版本已发布或状态已变化' }
        return {id:revisionId,state:'PUBLISHED'}
    }
    var publication = readCatalog({mode:'publication',id:id})
    ownProvider(publication.revision.provider); checkVersion(publication)
    if (action == 'sync') { exit 409, '未配置真实跨网目录同步连接，不能将模拟结果记为成功' }
    if (action != 'offline') { exit 400, '不支持的发布动作' }
    var reason = required(payload.reason,'下线原因',1000)
    var changed = db.table('res_catalog_publish_request').where().eq('tenant_id',tenantId).eq('tid',id)
        .eq('request_status','PUBLISHED').eq('revision_no',publication.version).eq('is_del',0)
        .update({request_status:'OFFLINE',offline_reason:reason,revision_no:publication.version+1,updated_time:now,updated_by:actorId})
    if (changed != 1) { exit 409, '发布状态已变化，请刷新' }
    return {id:id,state:'OFFLINE'}
}
if (kind == 'subscriptions' && id == '') {
    var publication = readCatalog({mode:'publication',id:required(payload.publicationId,'发布版本',32)})
    if (publication.state != 'PUBLISHED' || publication.revision.shareMode == 'NONE') { exit 409, '此版本不允许申请' }
    organization(orgId)
    var fields = payload.fieldIds
    if (fields == null || fields.isEmpty()) { exit 400, '请选择申请数据项' }
    var seen = []
    for (field in fields) {
        if (!publication.revision.fieldIds.contains(field) || seen.contains(field)) { exit 400, '申请字段超出发布范围或重复' }
        seen.add(field)
    }
    var from=epoch(payload.validFrom)
    var until=epoch(payload.validTo)
    if (until <= now.getTime() || until <= from || until-from > 366*86400000L) { exit 400, '请设置一年以内的有效申请期限' }
    if (payload.frequency != 'ONCE') { exit 409, '当前交付引擎支持单次真实导出，请选择单次交付；周期调度尚未启用' }
    id=IdUtil.getSnowflakeNextIdStr()
    var sub={id:id,publicationId:publication.id,applicantId:actorId,department:orgId,
        purpose:required(payload.purpose,'业务用途',1000),fieldIds:fields,approvedFields:[],frequency:'ONCE',
        validFrom:text(payload.validFrom),validTo:text(payload.validTo),state:'PENDING'}
    db.table('data_apply_form_t').primary('tid').insert({tid:id,tenant_id:tenantId,apply_user_id:actorId,
        apply_user_name:text(user.name),apply_org_id:orgId,apply_name:publication.revision.name+'订阅申请',
        catalog_ids:publication.entryId,type:'catalogSubscribe',flow_status:1,resource_scope_json:jsons.toJSONStr(sub),
        resource_revision_no:1,created_by:actorId,updated_by:actorId,created_time:now,updated_time:now,is_del:0})
    return {id:id,state:'PENDING'}
}
if (kind == 'subscriptions' || kind == 'deliveries') {
    var delivery = kind == 'deliveries' ? readCatalog({mode:'delivery',id:id}) : null
    var sub = readCatalog({mode:'subscription',id:delivery == null ? id : delivery.subscriptionId})
    var publication = readCatalog({mode:'publication',id:sub.publicationId})
    if (kind == 'subscriptions' && action == 'review') {
        ownProvider(publication.revision.provider); checkVersion(sub)
        if (sub.applicantId == actorId) { exit 403, '不得审批自己的订阅申请' }
        if (sub.state != 'PENDING') { exit 409, '申请已处理' }
        sub.opinion=required(payload.opinion,'审批意见',1000); sub.reviewer=actorId
        if (payload.approve == true) {
            if (publication.state != 'PUBLISHED') { exit 409, '目录已下线' }
            validate(publication.revision,false)
            var fields=payload.fieldIds
            if (fields == null || fields.isEmpty()) { exit 400, '请选择批准数据项' }
            var seen=[]
            for (field in fields) {
                if (!sub.fieldIds.contains(field) || !publication.revision.fieldIds.contains(field) || seen.contains(field)) { exit 400, '批准范围只能缩小' }
                seen.add(field)
            }
            var until = payload.validTo == null ? sub.validTo : text(payload.validTo)
            if (epoch(until) > epoch(sub.validTo) || epoch(until) <= Math.max(now.getTime(),epoch(sub.validFrom))) { exit 400, '批准期限只能缩短且必须仍有效' }
            sub.validTo=until; sub.approvedFields=fields; sub.grantId=IdUtil.getSnowflakeNextIdStr()
            sub.state=epoch(sub.validFrom)>now.getTime() ? 'SCHEDULED' : 'ACTIVE'
        } else { sub.state='RETURNED' }
        saveSub(sub,sub.version,payload.approve == true ? 2 : 4)
        return {id:sub.id,state:sub.state}
    }
    if (kind == 'subscriptions' && ['revoke','pause'].contains(action)) {
        checkVersion(sub)
        if (sub.applicantId != actorId) { ownProvider(publication.revision.provider) }
        sub.opinion=required(payload.reason,'原因',1000); sub.state=action == 'revoke' ? 'REVOKED' : 'PAUSED'
        saveSub(sub,sub.version,6)
        return {id:sub.id,state:sub.state}
    }
    if (sub.applicantId != actorId) { exit 403, '只允许申请人读取获批数据' }
    if (publication.state != 'PUBLISHED' || sub.state != 'ACTIVE' || epoch(sub.validFrom)>now.getTime() || epoch(sub.validTo)<=now.getTime()) {
        exit 403, '授权尚未生效、已撤销、已暂停或已到期'
    }
    var latestStructure = validate(publication.revision,false)
    if (kind == 'deliveries') {
        if (delivery.state != 'SUCCEEDED') { exit 409, '批次尚未生成成功' }
        return delivery
    }
    if (action != 'deliver') { exit 400, '不支持的订阅动作' }
    var columns=[]
    var labels=[]
    var masks=[]
    for (fieldId in sub.approvedFields) {
        if (!sub.fieldIds.contains(fieldId) || !publication.revision.fieldIds.contains(fieldId)) { exit 403, '批准范围无效' }
        for (field in publication.revision.structure.fields) {
            if (field.id != fieldId) { continue }
            columns.add(field.name)
            labels.add(publication.revision.fieldLabels[fieldId] == null ? field.name : publication.revision.fieldLabels[fieldId])
            var latestMask = false
            for (latestField in latestStructure.fields) {
                if (latestField.id == field.id && (latestField.masking == true || text(latestField.masking) == '1')) { latestMask = true }
            }
            masks.add(text(field.name).toLowerCase().matches('.*(phone|mobile|email|identity|certificate).*') || latestMask || field.masking == true || text(field.masking) == '1')
        }
    }
    if (columns.isEmpty()) { exit 403, '没有有效的批准字段' }
    var deliveryId=IdUtil.getSnowflakeNextIdStr()
    var tableId=publication.revision.resourceId
    db.table('data_distribution_task_t').primary('tid').insert({tid:deliveryId,tenant_id:tenantId,
        apply_form_id:sub.id,catalog_id:publication.entryId,catalog_name:publication.revision.name,source_table_id:tableId,
        source_table_name:publication.revision.structure.name,task_code:'EXPORT-'+deliveryId,task_name:publication.revision.name+'批准范围导出',
        task_status:'RUNNING',progress:0,execution_engine:'RESOURCE_EXPORT',execution_mode:'APPROVED_SNAPSHOT',
        started_time:now,created_time:now,updated_time:now,created_by:actorId,updated_by:actorId,is_del:0})
    try {
        var previousBody=body
        var sampled=null
        try { body={tableId:tableId,sampleSize:1001,columns:columns}; sampled=sampleData() }
        finally { body=previousBody }
        var raw=sampled.rows == null ? sampled.data : sampled.rows
        if (raw == null) { exit 502, '来源未返回可识别的数据行，未生成空白成功文件' }
        var rows=[]
        var csvCell=value => {
            var cell=value == null ? '' : value + ''
            if (cell.matches('^[=+@-].*')) { cell="'"+cell }
            return '"'+cell.replace('"','""')+'"'
        }
        var lines=[]
        var header=[]
        for (label in labels) { header.add(csvCell(label)) }
        lines.add(header.join(','))
        var index=0
        for (row in raw) {
            if (index >= 1000) { break }
            var output={}; var line=[]; var fieldIndex=0
            for (column in columns) {
                var value=row[column]
                if (masks[fieldIndex] && value != null) { value='***' }
                output[labels[fieldIndex]]=value; line.add(csvCell(value)); fieldIndex=fieldIndex+1
            }
            rows.add(output); lines.add(line.join(',')); index=index+1
        }
        var result={subscriptionId:sub.id,publicationId:publication.id,grantId:sub.grantId,fieldIds:sub.approvedFields,
            rows:rows.size(),availableRows:raw.size(),truncated:raw.size()>1000,format:publication.revision.format,
            filename:'resource_'+publication.id+'_'+deliveryId+'.'+publication.revision.format.toLowerCase(),
            content:publication.revision.format == 'JSON' ? jsons.toJSONStr(rows) : '\ufeff'+lines.join('\r\n'),scheduledFor:now}
        db.table('data_distribution_task_t').where().eq('tenant_id',tenantId).eq('tid',deliveryId)
            .update({task_status:'SUCCEEDED',progress:100,resource_result_json:jsons.toJSONStr(result),
                status_message:raw.size()>1000 ? '已导出前1000行，来源记录更多' : '批准字段真实数据导出完成',
                finished_time:new Date(),updated_time:new Date()})
        return {id:deliveryId,state:'SUCCEEDED',rows:rows.size(),truncated:raw.size()>1000}
    } catch (error) {
        db.table('data_distribution_task_t').where().eq('tenant_id',tenantId).eq('tid',deliveryId)
            .update({task_status:'FAILED',status_message:'来源读取失败，请检查数据源连接和字段结构',finished_time:new Date(),updated_time:new Date()})
        exit 502, '真实来源读取失败，交付任务已记录失败，可检查连接后重试'
    }
}
exit 400, '不支持的目录运营操作'
