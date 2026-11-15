{
  "properties": {},
  "id": "f4f1ed3666744afc9df1b2d847dfddb3",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "12.出湖申请",
  "createTime": 1789000000012,
  "updateTime": 1789000000012,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lake/egress/apply",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"zoneId\": \"...\",\n  \"objectIds\": [\"...\"],\n  \"targetZoneId\": \"...\",\n  \"purpose\": \"专题分析\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"approvalRequired\": true,\n  \"allowed\": false,\n  \"applyFormId\": \"...\"\n}",
  "description": "出湖申请（POST /dws/service/lake/egress/apply）。校验对象属于当前租户与分层，按 OUT 规则判断是否需审批；需审批时创建 data_apply_form_t 申请单，不物理移动数据。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.出湖申请
// 路由：POST /dws/service/lake/egress/apply
// 业务说明：按 OUT 规则校验出湖对象；需审批时生成 data_apply_form_t 申请单并返回申请单号，未审批前不视为允许读取。
import cn.hutool.core.util.IdUtil
import jsons
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var user = me()
var userId = user == null ? '' : (user.userId == null ? (user.id == null ? '' : user.id + '') : user.userId + '')
var userName = user == null ? userId : (user.userName == null ? (user.realName == null ? (user.name == null ? userId : user.name + '') : user.realName + '') : user.userName + '')
var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

if (userId == '') { exit 401, '登录状态无效，请重新登录' }
var zoneId = body?.zoneId == null ? '' : (body.zoneId + '').trim()
var targetZoneId = body?.targetZoneId == null ? '' : (body.targetZoneId + '').trim()
var purpose = body?.purpose == null ? '' : (body.purpose + '').trim()
var objectIds = body?.objectIds
if (zoneId == '') { exit 400, 'zoneId 不能为空' }
if (!(objectIds instanceof List) || objectIds.size() == 0) { exit 400, 'objectIds 必须为非空数组' }
if (objectIds.size() > 200) { exit 400, '单次出湖申请最多 200 个对象' }
if (purpose.length() > 500) { exit 400, '用途说明不能超过500字' }

var zones = db.select("select tid, status from lake_zone_t where tid = #{zoneId} and tenant_id = #{tenantId} and is_del = 0", { zoneId: zoneId, tenantId: tenantId })
if (zones == null || zones.size() == 0) { exit 404, '出湖分层不存在或不在当前租户' }
if (targetZoneId != '') {
    var targetZones = db.select("select tid from lake_zone_t where tid = #{targetZoneId} and tenant_id = #{tenantId} and is_del = 0", { targetZoneId: targetZoneId, tenantId: tenantId })
    if (targetZones == null || targetZones.size() == 0) { exit 404, '目标分层不存在或不在当前租户' }
}

var idSet = {}
var idList = []
for (rawId in objectIds) {
    var id = text(rawId).trim()
    if (id == '') { exit 400, '对象标识不能为空' }
    if (!id.matches('[A-Za-z0-9_-]+')) { exit 400, '对象标识格式不正确' }
    if (idSet[id] != true) { idSet[id] = true; idList.add(id) }
}
var inSql = "'" + idList.join("','") + "'"
var objectRows = db.select("select tid, object_type as objectType, object_ref as objectRef from lake_zone_object_t where tenant_id = #{tenantId} and zone_id = #{zoneId} and is_del = 0 and tid in (" + inSql + ")", { tenantId: tenantId, zoneId: zoneId })
if (objectRows == null || objectRows.size() != idList.size()) { exit 404, '存在不属于当前分层或当前租户的对象' }
var objectTypeSet = {}
for (row in objectRows) {
    objectTypeSet[text(first(row, ['objectType', 'objecttype', 'object_type'])).toUpperCase()] = true
}

var rules = db.select("""
  select rule_code as ruleCode, rule_name as ruleName, object_scope as objectScope,
         approval_required as approvalRequired, interaction_mode as interactionMode
    from lake_zone_rule_t
   where tenant_id = #{tenantId} and zone_id = #{zoneId} and rule_type = 'OUT' and status = 'ENABLED' and is_del = 0
   order by priority desc, rule_code asc
""", { tenantId: tenantId, zoneId: zoneId })

var requiredRules = []
var requiredCodes = {}
var expectedMode = ''
for (row in rules) {
    var scope = text(first(row, ['objectScope', 'objectscope', 'object_scope'])).toUpperCase()
    if (scope != 'ALL' && objectTypeSet[scope] != true) { continue }
    var approval = text(first(row, ['approvalRequired', 'approvalrequired', 'approval_required'])).toLowerCase()
    if (approval == '1' || approval == 'true') {
        var code = text(first(row, ['ruleCode', 'rulecode', 'rule_code']))
        if (requiredCodes[code] != true) {
            requiredCodes[code] = true
            requiredRules.add({
                ruleCode: code,
                ruleName: text(first(row, ['ruleName', 'rulename', 'rule_name']))
            })
        }
    }
    var mode = text(first(row, ['interactionMode', 'interactionmode', 'interaction_mode'])).toUpperCase()
    if (mode != '' && expectedMode == '') { expectedMode = mode }
}

if (requiredRules.size() == 0) {
    return { approvalRequired: false, allowed: true, applyFormId: null, interactionMode: expectedMode, rules: [] }
}

var formId = IdUtil.getSnowflakeNextIdStr()
var now = new Date()
var scopeJson = jsons.toJSONStr({
    zoneId: zoneId,
    targetZoneId: targetZoneId,
    objectIds: idList,
    purpose: purpose,
    interactionMode: expectedMode
})
db.table('data_apply_form_t').primary('tid').insert({
    tid: formId,
    apply_user_id: userId,
    apply_user_name: userName,
    apply_name: '出湖申请-' + zoneId,
    catalog_ids: zoneId,
    type: 'lakeEgress',
    flow_status: 1,
    tenant_id: tenantId,
    resource_scope_json: scopeJson,
    resource_revision_no: 1,
    submission_version: 0,
    created_by: userId,
    updated_by: userId,
    created_time: now,
    updated_time: now,
    is_del: 0
})
return { approvalRequired: true, allowed: false, applyFormId: formId, interactionMode: expectedMode, rules: requiredRules }
