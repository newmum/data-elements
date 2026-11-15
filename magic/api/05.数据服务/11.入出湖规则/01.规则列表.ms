{
  "properties": {},
  "id": "1ba32da0a958422e934662fc1c1b55fb",
  "script": null,
  "groupId": "2f650f81f8c243368d56eef608316408",
  "name": "01.规则列表",
  "createTime": 1789000000009,
  "updateTime": 1789000000009,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/rules",
  "method": "GET",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的湖仓分层或对象标识"
    },
    {
      "name": "ruleType",
      "value": "",
      "description": "查询参数",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": []\n}",
  "description": "分层入出湖规则列表（GET /dws/lakerule/rules?id={id}）。校验分层归属后一次查询全部规则，支持 ruleType 过滤。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// 接口：01.规则列表
// 路由：GET /dws/lakerule/rules?id={id}
// 业务说明：查询分层入湖/出湖规则；分层归属先校验，规则一次查询返回。

var tenantId = tenantRuntime.id()
var zoneId = param?.id == null ? '' : (param.id + '').trim()
var ruleTypeValue = param.ruleType == null ? '' : (param.ruleType + '').trim().toUpperCase()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

if (zoneId == '') { exit 400, '分层标识不能为空' }
var zoneRows = db.select("select tid from lake_zone_t where tid = #{zoneId} and tenant_id = #{tenantId} and is_del = 0", { zoneId: zoneId, tenantId: tenantId })
if (zoneRows == null || zoneRows.size() == 0) { exit 404, '分层不存在或不在当前租户' }

var whereSql = " where tenant_id = #{tenantId} and zone_id = #{zoneId} and is_del = 0 "
if (ruleTypeValue != '') {
    if (ruleTypeValue != 'IN' && ruleTypeValue != 'OUT') { exit 400, 'ruleType 必须是 IN 或 OUT' }
    whereSql += " and rule_type = '" + ruleTypeValue + "' "
}
var rows = db.select("""
  select tid as id, tid, zone_id as zoneId, rule_code as ruleCode, rule_name as ruleName,
         rule_type as ruleType, object_scope as objectScope, file_format as fileFormat,
         compression, partition_rule as partitionRule, retention_days as retentionDays,
         approval_required as approvalRequired, interaction_mode as interactionMode,
         priority, status, description, created_time as createdTime, updated_time as updatedTime
    from lake_zone_rule_t
""" + whereSql + " order by rule_type asc, priority desc, rule_code asc", { tenantId: tenantId, zoneId: zoneId })

var list = []
for (row in rows) {
    list.add({
        id: text(first(row, ['id', 'tid'])),
        zoneId: text(first(row, ['zoneId', 'zoneid', 'zone_id'])),
        ruleCode: text(first(row, ['ruleCode', 'rulecode', 'rule_code'])),
        ruleName: text(first(row, ['ruleName', 'rulename', 'rule_name'])),
        ruleType: text(first(row, ['ruleType', 'ruletype', 'rule_type'])),
        objectScope: text(first(row, ['objectScope', 'objectscope', 'object_scope'])),
        fileFormat: text(first(row, ['fileFormat', 'fileformat', 'file_format'])),
        compression: text(first(row, ['compression', 'COMPRESSION'])),
        partitionRule: text(first(row, ['partitionRule', 'partitionrule', 'partition_rule'])),
        retentionDays: num(first(row, ['retentionDays', 'retentiondays', 'retention_days'])),
        approvalRequired: num(first(row, ['approvalRequired', 'approvalrequired', 'approval_required'])) == 1,
        interactionMode: text(first(row, ['interactionMode', 'interactionmode', 'interaction_mode'])),
        priority: num(first(row, ['priority', 'PRIORITY'])),
        status: text(first(row, ['status', 'STATUS'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        createdTime: first(row, ['createdTime', 'createdtime', 'created_time']),
        updatedTime: first(row, ['updatedTime', 'updatedtime', 'updated_time'])
    })
}
return { list: list, zoneId: zoneId }
