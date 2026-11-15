{
  "properties": {},
  "id": "a55e7c1d2f4b4a6e9c3d8b1f5a7e2c41",
  "script": null,
  "groupId": "datapd_asset_group_01",
  "name": "02.资产模块保存",
  "createTime": 1791030855464,
  "updateTime": 1791030855464,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/module/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"module\":\"plan\",\"id\":\"\",\"dryRun\":false}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "保存或修改资产规划/发现/处置/安全/运维/评估/盘点记录；仅接受白名单字段，租户由登录上下文确定，支持 dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.资产模块保存
// 路由：POST /dst/asset/module/save
// 业务说明：保存或修改资产模块记录；仅接受白名单字段，租户由登录上下文确定，支持 dryRun。
import cn.hutool.core.date.DateUtil
import cn.hutool.core.util.IdUtil
import java.sql.Timestamp
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var user = me()
var userId = user == null ? '' : (user.id == null ? '' : user.id + '')
var text = (value) => value == null ? '' : value + ''
var modules = {
    plan: {
        table: 'asset_plan_t', codeKey: 'planCode', codeColumn: 'plan_code', nameKey: 'planName',
        fields: { planCode: 'plan_code', planName: 'plan_name', planYear: 'plan_year', planType: 'plan_type',
            scopeJson: 'scope_json', goalDesc: 'goal_desc', orgId: 'org_id', ownerId: 'owner_id',
            startDate: 'start_date', endDate: 'end_date', status: 'status', progress: 'progress', description: 'description' },
        fieldKeys: ['planCode', 'planName', 'planYear', 'planType', 'scopeJson', 'goalDesc', 'orgId', 'ownerId', 'startDate', 'endDate', 'status', 'progress', 'description'],
        required: ['planCode', 'planName'], ints: ['planYear', 'progress'], times: ['startDate', 'endDate'],
        decimals: [], statuses: ['DRAFT', 'APPROVED', 'EXECUTING', 'COMPLETED', 'CANCELLED'], statusKey: 'status'
    },
    discovery: {
        table: 'asset_discovery_t', codeKey: 'discoveryCode', codeColumn: 'discovery_code', nameKey: 'discoveryName',
        fields: { discoveryCode: 'discovery_code', discoveryName: 'discovery_name', discoveryType: 'discovery_type',
            sourceId: 'source_id', resourceType: 'resource_type', resourceId: 'resource_id', resourceName: 'resource_name',
            orgId: 'org_id', catalogId: 'catalog_id', status: 'status', discoveredTime: 'discovered_time',
            handledBy: 'handled_by', handledTime: 'handled_time', description: 'description' },
        fieldKeys: ['discoveryCode', 'discoveryName', 'discoveryType', 'sourceId', 'resourceType', 'resourceId', 'resourceName', 'orgId', 'catalogId', 'status', 'discoveredTime', 'handledBy', 'handledTime', 'description'],
        required: ['discoveryCode'], ints: [], times: ['discoveredTime', 'handledTime'], decimals: [],
        statuses: ['PENDING', 'CONFIRMED', 'REGISTERED', 'IGNORED'], statusKey: 'status'
    },
    disposal: {
        table: 'asset_disposal_t', codeKey: 'disposalCode', codeColumn: 'disposal_code', nameKey: 'assetName',
        fields: { disposalCode: 'disposal_code', assetId: 'asset_id', assetName: 'asset_name', disposalType: 'disposal_type',
            reason: 'reason', riskLevel: 'risk_level', approvalStatus: 'approval_status', applyUserId: 'apply_user_id',
            approverId: 'approver_id', approvedTime: 'approved_time', executedTime: 'executed_time', description: 'description' },
        fieldKeys: ['disposalCode', 'assetId', 'assetName', 'disposalType', 'reason', 'riskLevel', 'approvalStatus', 'applyUserId', 'approverId', 'approvedTime', 'executedTime', 'description'],
        required: ['disposalCode', 'assetId'], ints: [], times: ['approvedTime', 'executedTime'], decimals: [],
        statuses: ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'EXECUTED'], statusKey: 'approvalStatus'
    },
    security: {
        table: 'asset_security_t', codeKey: 'catalogId', codeColumn: 'catalog_id', nameKey: 'classificationName',
        fields: { catalogId: 'catalog_id', securityLevel: 'security_level', classificationId: 'classification_id',
            classificationName: 'classification_name', maskingRequired: 'masking_required',
            authorizationRequired: 'authorization_required', policyStatus: 'policy_status', lastScanTime: 'last_scan_time',
            findingCount: 'finding_count', ownerId: 'owner_id', description: 'description' },
        fieldKeys: ['catalogId', 'securityLevel', 'classificationId', 'classificationName', 'maskingRequired', 'authorizationRequired', 'policyStatus', 'lastScanTime', 'findingCount', 'ownerId', 'description'],
        required: ['catalogId'], ints: ['maskingRequired', 'authorizationRequired', 'findingCount'], times: ['lastScanTime'],
        decimals: [], statuses: ['NONE', 'CONFIGURED', 'REVIEW'], statusKey: 'policyStatus'
    },
    operation: {
        table: 'asset_operation_t', codeKey: 'catalogId', codeColumn: 'catalog_id', nameKey: 'operationName',
        fields: { catalogId: 'catalog_id', operationType: 'operation_type', operationName: 'operation_name',
            healthStatus: 'health_status', lastCheckTime: 'last_check_time', issueCount: 'issue_count',
            lastIssue: 'last_issue', ownerId: 'owner_id', description: 'description' },
        fieldKeys: ['catalogId', 'operationType', 'operationName', 'healthStatus', 'lastCheckTime', 'issueCount', 'lastIssue', 'ownerId', 'description'],
        required: ['catalogId'], ints: ['issueCount'], times: ['lastCheckTime'], decimals: [],
        statuses: ['NORMAL', 'WARNING', 'ERROR'], statusKey: 'healthStatus'
    },
    evaluation: {
        table: 'asset_evaluation_t', codeKey: 'evaluationCode', codeColumn: 'evaluation_code', nameKey: 'evaluationName',
        fields: { evaluationCode: 'evaluation_code', evaluationName: 'evaluation_name', period: 'period', orgId: 'org_id',
            catalogCount: 'catalog_count', qualityScore: 'quality_score', usageScore: 'usage_score',
            sharingScore: 'sharing_score', securityScore: 'security_score', totalScore: 'total_score', grade: 'grade',
            rankNo: 'rank_no', detailJson: 'detail_json', evaluatedTime: 'evaluated_time' },
        fieldKeys: ['evaluationCode', 'evaluationName', 'period', 'orgId', 'catalogCount', 'qualityScore', 'usageScore', 'sharingScore', 'securityScore', 'totalScore', 'grade', 'rankNo', 'detailJson', 'evaluatedTime'],
        required: ['evaluationCode', 'period'], ints: ['catalogCount', 'rankNo'],
        times: ['evaluatedTime'], decimals: ['qualityScore', 'usageScore', 'sharingScore', 'securityScore', 'totalScore'],
        statuses: ['A', 'B', 'C', 'D'], statusKey: 'grade'
    },
    inventory: {
        table: 'asset_inventory_t', codeKey: 'inventoryCode', codeColumn: 'inventory_code', nameKey: 'inventoryName',
        fields: { inventoryCode: 'inventory_code', inventoryName: 'inventory_name', scopeType: 'scope_type',
            scopeJson: 'scope_json', status: 'status', plannedTime: 'planned_time', startedTime: 'started_time',
            finishedTime: 'finished_time', totalCount: 'total_count', matchedCount: 'matched_count',
            missingCount: 'missing_count', mismatchCount: 'mismatch_count', resultJson: 'result_json',
            resultSummary: 'result_summary' },
        fieldKeys: ['inventoryCode', 'inventoryName', 'scopeType', 'scopeJson', 'status', 'plannedTime', 'startedTime', 'finishedTime', 'totalCount', 'matchedCount', 'missingCount', 'mismatchCount', 'resultJson', 'resultSummary'],
        required: ['inventoryCode', 'inventoryName'], ints: ['totalCount', 'matchedCount', 'missingCount', 'mismatchCount'],
        times: ['plannedTime', 'startedTime', 'finishedTime'], decimals: [],
        statuses: ['DRAFT', 'RUNNING', 'COMPLETED', 'FAILED'], statusKey: 'status'
    }
}
var module = text(body?.module).trim().toLowerCase()
if (modules[module] == null) { exit 400, '资产模块无效' }
var config = modules[module]
var dryRun = body?.dryRun == true
var id = text(body?.id).trim()
if (id.length() > 32 || (id != '' && !id.matches('[A-Za-z0-9_-]+'))) { exit 400, '记录标识格式不正确' }

var toDate = (value) => {
    var raw = text(value).trim()
    if (raw == '') { return null }
    try {
        return new Timestamp(DateUtil.parse(raw).getTime())
    } catch (error) {
        exit 400, '时间格式不正确：' + raw
    }
}
var toInt = (value) => text(value).trim() == '' ? null : (value + '')::int(0)
var toDouble = (value) => text(value).trim() == '' ? null : (value + '')::double
var toText = (value) => {
    var raw = text(value).trim()
    return raw == '' ? null : raw
}

for (key in config.required) {
    if (text(body == null ? null : body[key]).trim() == '') { exit 400, '必填字段不能为空：' + key }
}
var statusValue = config.statusKey == null ? '' : text(body == null ? null : body[config.statusKey]).trim().toUpperCase()
if (statusValue != '' && config.statuses.size() > 0 && !config.statuses.contains(statusValue)) {
    exit 400, '状态取值无效：' + statusValue
}
if (module == 'security') {
    var level = text(body?.securityLevel).trim().toUpperCase()
    if (level != '' && !['L1', 'L2', 'L3', 'L4', 'L5'].contains(level)) { exit 400, '安全级别必须是 L1-L5' }
}
if (module == 'evaluation') {
    var period = text(body?.period).trim()
    if (!period.matches('^[0-9]{4}-(0[1-9]|1[0-2])$')) { exit 400, '评估周期格式必须为 YYYY-MM' }
}
var catalogId = text(body?.catalogId).trim()
if ((module == 'security' || module == 'operation' || (module == 'discovery' && catalogId != '')) && catalogId != '') {
    var catalog = db.selectOne("SELECT tid FROM da_catalog_t WHERE tenant_id = #{tenantId} AND tid = #{catalogId} AND is_del = 0")
    if (catalog == null) { exit 404, '关联目录不存在或不在当前租户' }
}

var codeValue = text(body == null ? null : body[config.codeKey]).trim()
if (codeValue != '') {
    var duplicateSql = "SELECT COUNT(1) AS n FROM " + config.table + " WHERE tenant_id = #{tenantId} AND " + config.codeColumn + " = #{codeValue} AND is_del = 0"
    if (id != '') { duplicateSql = duplicateSql + ' AND tid <> #{id}' }
    var duplicate = db.select(duplicateSql)
    if (duplicate[0].n > 0) { exit 409, '编码已存在：' + codeValue }
}
if (id != '') {
    var existing = db.select("SELECT tid FROM " + config.table + " WHERE tenant_id = #{tenantId} AND tid = #{id} AND is_del = 0")
    if (existing == null || existing.size() == 0) { exit 404, '记录不存在或不在当前租户' }
}
if (dryRun) {
    return { dryRun: true, valid: true, module: module, id: id }
}

var columns = []
var valueExpressions = []
var sets = []
var params = { tenantId: tenantId, id: id, userId: userId }
for (key in config.fieldKeys) {
    if (body == null || body[key] == null) { continue }
    var raw = body[key]
    var normalized = null
    if (config.times.contains(key)) { normalized = toDate(raw) }
    else if (config.ints.contains(key)) { normalized = toInt(raw) }
    else if (config.decimals.contains(key)) { normalized = toDouble(raw) }
    else { normalized = toText(raw) }
    if (normalized == null) { continue }
    var column = config.fields[key]
    columns.add(column)
    valueExpressions.add('#{' + key + '}')
    sets.add(column + ' = #{' + key + '}')
    params[key] = normalized
}
if (id == '') {
    id = IdUtil.getSnowflakeNextIdStr()
    params.id = id
    columns.add(0, 'tid')
    valueExpressions.add(0, '#{id}')
    columns.add(1, 'tenant_id')
    valueExpressions.add(1, '#{tenantId}')
    columns.add('created_by')
    valueExpressions.add('#{userId}')
    columns.add('updated_by')
    valueExpressions.add('#{userId}')
    columns.add('created_time')
    valueExpressions.add('current_timestamp')
    columns.add('updated_time')
    valueExpressions.add('current_timestamp')
    columns.add('is_del')
    valueExpressions.add('0')
    var insertSql = 'INSERT INTO ' + config.table + ' (' + columns.join(',') + ') VALUES (' + valueExpressions.join(',') + ')'
    db.update(insertSql, params)
} else {
    sets.add('updated_by = #{userId}')
    sets.add('updated_time = current_timestamp')
    var updateSql = 'UPDATE ' + config.table + ' SET ' + sets.join(',') + ' WHERE tid = #{id} AND tenant_id = #{tenantId} AND is_del = 0'
    var changed = db.update(updateSql, params)
    if (changed != 1) { exit 409, '记录已被修改，请刷新后重试' }
}
var savedRows = db.select("SELECT * FROM " + config.table + " WHERE tid = #{id} AND tenant_id = #{tenantId} AND is_del = 0")
var saved = savedRows[0]
saved['id'] = id
return saved
