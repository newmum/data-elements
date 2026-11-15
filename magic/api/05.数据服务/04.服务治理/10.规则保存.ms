{
  "properties": {},
  "id": "70cfff4ea75848ba85f7c5f5a645ce52",
  "script": null,
  "groupId": "c452aa598f245789a90d40ae2091e57c",
  "name": "10.规则保存",
  "createTime": 1789000000010,
  "updateTime": 1789000000010,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/lake/zones/{id}/rules",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"rules\": [\n    {\n      \"ruleCode\": \"IN_FMT_01\",\n      \"ruleName\": \"入湖文件格式\",\n      \"ruleType\": \"IN\",\n      \"objectScope\": \"ALL\",\n      \"fileFormat\": \"parquet,orc,csv\",\n      \"retentionDays\": 365,\n      \"approvalRequired\": false,\n      \"interactionMode\": \"API\",\n      \"priority\": 10\n    }\n  ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"saved\": 1,\n  \"zoneId\": \"...\"\n}",
  "description": "整体替换分层入出湖规则（PUT /dws/service/lake/zones/{id}/rules）。一次删除旧规则、一次多行插入新规则；规则编号在同方向内唯一。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.规则保存
// 路由：PUT /dws/service/lake/zones/{id}/rules
// 业务说明：按请求整体替换分层的入出湖规则；规则属于配置，替换时物理清理旧规则，避免逻辑删除与唯一键冲突。
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

var tenantId = tenantRuntime.id()
var zoneId = path?.id == null ? '' : (path.id + '').trim()
var userId = me()?.id
var text = (value) => value == null ? '' : value + ''
var intValue = (value) => value == null ? 0 : value::int(0)

if (zoneId == '') { exit 400, '分层标识不能为空' }
var zoneRows = db.select("select tid from lake_zone_t where tid = #{zoneId} and tenant_id = #{tenantId} and is_del = 0", { zoneId: zoneId, tenantId: tenantId })
if (zoneRows == null || zoneRows.size() == 0) { exit 404, '分层不存在或不在当前租户' }

var rules = body?.rules
if (rules == null) { rules = [] }
if (!(rules instanceof List)) { exit 400, 'rules 必须为数组' }
if (rules.size() > 200) { exit 400, '单个分层最多配置 200 条规则' }

var allowedDirections = ['IN', 'OUT']
var allowedScopes = ['ALL', 'TABLE', 'VIEW', 'FILE', 'API']
var allowedModes = ['API', 'FILE', 'JDBC', 'STREAM']
var allowedStatus = ['ENABLED', 'DISABLED']
var seen = {}
var values = []
var params = { tenantId: tenantId, zoneId: zoneId, userId: userId }
var index = 0
for (item in rules) {
    if (!(item instanceof Map)) { exit 400, '每条规则必须为对象结构' }
    var ruleCode = text(item.ruleCode == null ? item.rule_code : item.ruleCode).trim()
    var ruleName = text(item.ruleName == null ? item.rule_name : item.ruleName).trim()
    var ruleType = text(item.ruleType == null ? item.rule_type : item.ruleType).trim().toUpperCase()
    var objectScope = text(item.objectScope == null ? item.object_scope : item.objectScope).trim().toUpperCase()
    var fileFormat = text(item.fileFormat == null ? item.file_format : item.fileFormat).trim()
    var compression = text(item.compression).trim()
    var partitionRule = text(item.partitionRule == null ? item.partition_rule : item.partitionRule).trim()
    var retentionDays = intValue(item.retentionDays == null ? item.retention_days : item.retentionDays)
    var rawApproval = item.approvalRequired == null ? item.approval_required : item.approvalRequired
    var approvalRequired = 0
    if (rawApproval == true || text(rawApproval).toLowerCase() == 'true' || text(rawApproval) == '1') { approvalRequired = 1 }
    var interactionMode = text(item.interactionMode == null ? item.interaction_mode : item.interactionMode).trim().toUpperCase()
    var priority = intValue(item.priority)
    var status = text(item.status).trim().toUpperCase()
    var description = text(item.description).trim()
    if (ruleCode == '' || ruleName == '') { exit 400, '规则编号和规则名称不能为空' }
    if (ruleCode.length() > 32) { exit 400, '规则编号不能超过32个字符' }
    if (!allowedDirections.contains(ruleType)) { exit 400, '规则方向必须是 IN 或 OUT' }
    if (!allowedScopes.contains(objectScope)) { exit 400, '适用对象必须是 ALL/TABLE/VIEW/FILE/API' }
    if (interactionMode == '') { interactionMode = 'API' }
    if (!allowedModes.contains(interactionMode)) { exit 400, '交互方式必须是 API/FILE/JDBC/STREAM' }
    if (status == '') { status = 'ENABLED' }
    if (!allowedStatus.contains(status)) { exit 400, '规则状态必须是 ENABLED 或 DISABLED' }
    if (retentionDays < 0) { exit 400, '保留天数不能为负数' }
    if (approvalRequired != 0 && approvalRequired != 1) { exit 400, 'approvalRequired 只能是 0 或 1' }
    var key = ruleType + '|' + ruleCode
    if (seen[key] == true) { exit 400, '同方向规则编号重复：' + ruleCode }
    seen[key] = true

    values.add("(#{tid" + index + "}, #{tenantId}, #{zoneId}, #{ruleCode" + index + "}, #{ruleName" + index + "}, #{ruleType" + index + "}, #{objectScope" + index + "}, #{fileFormat" + index + "}, #{compression" + index + "}, #{partitionRule" + index + "}, #{retentionDays" + index + "}, #{approvalRequired" + index + "}, #{interactionMode" + index + "}, #{priority" + index + "}, #{status" + index + "}, #{description" + index + "}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)")
    params["tid" + index] = IdUtil.simpleUUID()
    params["ruleCode" + index] = ruleCode
    params["ruleName" + index] = ruleName
    params["ruleType" + index] = ruleType
    params["objectScope" + index] = objectScope
    params["fileFormat" + index] = fileFormat
    params["compression" + index] = compression
    params["partitionRule" + index] = partitionRule
    params["retentionDays" + index] = retentionDays
    params["approvalRequired" + index] = approvalRequired
    params["interactionMode" + index] = interactionMode
    params["priority" + index] = priority
    params["status" + index] = status
    params["description" + index] = description
    index = index + 1
}

db.update("delete from lake_zone_rule_t where tenant_id = #{tenantId} and zone_id = #{zoneId}", { tenantId: tenantId, zoneId: zoneId })
if (values.size() > 0) {
    db.update("insert into lake_zone_rule_t (tid, tenant_id, zone_id, rule_code, rule_name, rule_type, object_scope, file_format, compression, partition_rule, retention_days, approval_required, interaction_mode, priority, status, description, created_by, updated_by, created_time, updated_time, is_del) values " + values.join(','), params)
}
return { saved: values.size(), zoneId: zoneId }
