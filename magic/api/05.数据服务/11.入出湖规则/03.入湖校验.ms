{
  "properties": {},
  "id": "4beadabc28554b528aaf179adf1b9972",
  "script": null,
  "groupId": "2f650f81f8c243368d56eef608316408",
  "name": "03.入湖校验",
  "createTime": 1789000000011,
  "updateTime": 1789000000011,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/ingest",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"zoneId\": \"...\",\n  \"objectType\": \"TABLE\",\n  \"objectRef\": \"db_table_t\",\n  \"fileFormat\": \"parquet\",\n  \"compression\": \"snappy\",\n  \"partitionRule\": \"dt\",\n  \"interactionMode\": \"API\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"passed\": true,\n  \"violations\": [],\n  \"requiredApprovals\": []\n}",
  "description": "入湖校验（POST /dws/lakerule/ingest）。按分层启用的 IN 规则逐条校验格式、压缩、分区、交互方式与审批要求，返回未通过规则编号。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// 接口：03.入湖校验
// 路由：POST /dws/lakerule/ingest
// 业务说明：按 lake_zone_rule_t 的 IN 规则校验入湖对象；未通过返回具体规则编号，不写业务数据。

var tenantId = tenantRuntime.id()
var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var splitList = (value) => {
    var result = []
    var raw = text(value).trim()
    if (raw == '') { return result }
    for (part in raw.split(',')) {
        var item = text(part).trim().toUpperCase()
        if (item != '') { result.add(item) }
    }
    return result
}

var zoneId = body?.zoneId == null ? '' : (body.zoneId + '').trim()
var objectType = body?.objectType == null ? '' : (body.objectType + '').trim().toUpperCase()
var objectRef = body?.objectRef == null ? '' : (body.objectRef + '').trim()
var fileFormat = body?.fileFormat == null ? '' : (body.fileFormat + '').trim()
var compression = body?.compression == null ? '' : (body.compression + '').trim()
var partitionRule = body?.partitionRule == null ? '' : (body.partitionRule + '').trim()
var interactionMode = body?.interactionMode == null ? '' : (body.interactionMode + '').trim().toUpperCase()

if (zoneId == '') { exit 400, 'zoneId 不能为空' }
if (objectType == '') { exit 400, 'objectType 不能为空' }
if (objectRef == '') { exit 400, 'objectRef 不能为空' }
var zones = db.select("select tid, status, zone_code as zoneCode from lake_zone_t where tid = #{zoneId} and tenant_id = #{tenantId} and is_del = 0", { zoneId: zoneId, tenantId: tenantId })
if (zones == null || zones.size() == 0) { exit 404, '分层不存在或不在当前租户' }
var zoneStatus = text(first(zones[0], ['status', 'STATUS'])).toUpperCase()
if (zoneStatus != 'ENABLED') { exit 409, '分层已停用，不能入湖' }

var rules = db.select("""
  select rule_code as ruleCode, rule_name as ruleName, object_scope as objectScope,
         file_format as fileFormat, compression, partition_rule as partitionRule,
         retention_days as retentionDays, approval_required as approvalRequired,
         interaction_mode as interactionMode, priority
    from lake_zone_rule_t
   where tenant_id = #{tenantId} and zone_id = #{zoneId} and rule_type = 'IN' and status = 'ENABLED' and is_del = 0
   order by priority desc, rule_code asc
""", { tenantId: tenantId, zoneId: zoneId })

var violations = []
var requiredApprovals = []
var expectedMode = ''
for (row in rules) {
    var ruleCode = text(first(row, ['ruleCode', 'rulecode', 'rule_code']))
    var ruleName = text(first(row, ['ruleName', 'rulename', 'rule_name']))
    var scope = text(first(row, ['objectScope', 'objectscope', 'object_scope'])).toUpperCase()
    if (scope != 'ALL' && scope != objectType) { continue }
    var formats = splitList(first(row, ['fileFormat', 'fileformat', 'file_format']))
    if (formats.size() > 0) {
        if (fileFormat == '') {
            violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'fileFormat', message: '缺少文件格式，允许：' + formats.join(',') })
        } else if (!formats.contains(fileFormat.toUpperCase())) {
            violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'fileFormat', message: '文件格式不在允许范围：' + formats.join(',') })
        }
    }
    var compressions = splitList(first(row, ['compression', 'COMPRESSION']))
    if (compressions.size() > 0) {
        if (compression == '') {
            violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'compression', message: '缺少压缩方式，允许：' + compressions.join(',') })
        } else if (!compressions.contains(compression.toUpperCase())) {
            violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'compression', message: '压缩方式不在允许范围：' + compressions.join(',') })
        }
    }
    var requiredPartition = text(first(row, ['partitionRule', 'partitionrule', 'partition_rule']))
    if (requiredPartition != '' && partitionRule == '') {
        violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'partitionRule', message: '缺少分区规则：' + requiredPartition })
    }
    var requiredMode = text(first(row, ['interactionMode', 'interactionmode', 'interaction_mode'])).toUpperCase()
    if (requiredMode != '') {
        if (expectedMode == '') { expectedMode = requiredMode }
        if (interactionMode != '' && interactionMode != requiredMode) {
            violations.add({ ruleCode: ruleCode, ruleName: ruleName, field: 'interactionMode', message: '交互方式必须为：' + requiredMode })
        }
    }
    var approval = text(first(row, ['approvalRequired', 'approvalrequired', 'approval_required'])).toLowerCase()
    if (approval == '1' || approval == 'true') {
        requiredApprovals.add(ruleCode)
    }
}
return {
    passed: violations.size() == 0,
    zoneId: zoneId,
    objectType: objectType,
    objectRef: objectRef,
    violations: violations,
    requiredApprovals: requiredApprovals,
    interactionMode: interactionMode == '' ? expectedMode : interactionMode
}
