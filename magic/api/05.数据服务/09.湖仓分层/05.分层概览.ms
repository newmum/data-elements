{
  "properties": {},
  "id": "cb06b4cfde2e45fba158477b784c3ea5",
  "script": null,
  "groupId": "b5badef9297e4dbb8a6028e72a393d4e",
  "name": "05.分层概览",
  "createTime": 1789000000005,
  "updateTime": 1789000000005,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/overview",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"zones\": [],\n  \"summary\": {}\n}",
  "description": "湖仓分层概览（GET /dws/lake/overview）。三条聚合查询返回分层、对象生命周期计数和规则计数，不按分层逐项查询。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// 接口：05.分层概览
// 路由：GET /dws/lake/overview
// 业务说明：分层列表 + 对象按生命周期聚合 + 规则按方向聚合，共三条查询，不按分层循环补查。

var tenantId = tenantRuntime.id()
var text = (value) => value == null ? '' : value + ''
var num = (value) => value == null ? 0 : value::int(0)
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}

var zoneRows = db.select("""
  select tid as id, zone_code as zoneCode, zone_name as zoneName, zone_type as zoneType,
         datasource_id as datasourceId, schema_name as schemaName, status, sort_no as sortNo, description
    from lake_zone_t
   where tenant_id = #{tenantId} and is_del = 0
   order by sort_no asc, zone_code asc
""", { tenantId: tenantId })

var objectRows = db.select("""
  select zone_id as zoneId, lifecycle_status as lifecycleStatus, count(1) as n
    from lake_zone_object_t
   where tenant_id = #{tenantId} and is_del = 0
   group by zone_id, lifecycle_status
""", { tenantId: tenantId })

var ruleRows = db.select("""
  select zone_id as zoneId, rule_type as ruleType, count(1) as n
    from lake_zone_rule_t
   where tenant_id = #{tenantId} and is_del = 0
   group by zone_id, rule_type
""", { tenantId: tenantId })

var objectCountMap = {}
var onlineCountMap = {}
for (row in objectRows) {
    var zoneId = text(first(row, ['zoneId', 'zoneid', 'zone_id']))
    var lifecycle = text(first(row, ['lifecycleStatus', 'lifecyclestatus', 'lifecycle_status'])).toUpperCase()
    var count = num(first(row, ['n', 'N']))
    objectCountMap[zoneId] = num(objectCountMap[zoneId]) + count
    if (lifecycle == 'ONLINE') { onlineCountMap[zoneId] = num(onlineCountMap[zoneId]) + count }
}
var inRuleMap = {}
var outRuleMap = {}
for (row in ruleRows) {
    var zoneId = text(first(row, ['zoneId', 'zoneid', 'zone_id']))
    var ruleType = text(first(row, ['ruleType', 'ruletype', 'rule_type'])).toUpperCase()
    var count = num(first(row, ['n', 'N']))
    if (ruleType == 'OUT') { outRuleMap[zoneId] = num(outRuleMap[zoneId]) + count } else { inRuleMap[zoneId] = num(inRuleMap[zoneId]) + count }
}

var zones = []
var enabledCount = 0
var objectTotal = 0
var onlineTotal = 0
var ruleTotal = 0
for (row in zoneRows) {
    var zoneId = text(first(row, ['id', 'tid']))
    var status = text(first(row, ['status', 'STATUS']))
    var objectCount = num(objectCountMap[zoneId])
    var onlineCount = num(onlineCountMap[zoneId])
    var inCount = num(inRuleMap[zoneId])
    var outCount = num(outRuleMap[zoneId])
    if (status == 'ENABLED') { enabledCount = enabledCount + 1 }
    objectTotal = objectTotal + objectCount
    onlineTotal = onlineTotal + onlineCount
    ruleTotal = ruleTotal + inCount + outCount
    zones.add({
        id: zoneId,
        zoneCode: text(first(row, ['zoneCode', 'zonecode', 'zone_code'])),
        zoneName: text(first(row, ['zoneName', 'zonename', 'zone_name'])),
        zoneType: text(first(row, ['zoneType', 'zonetype', 'zone_type'])),
        datasourceId: text(first(row, ['datasourceId', 'datasourceid', 'datasource_id'])),
        schemaName: text(first(row, ['schemaName', 'schemaname', 'schema_name'])),
        status: status,
        sortNo: num(first(row, ['sortNo', 'sortno', 'sort_no'])),
        description: text(first(row, ['description', 'DESCRIPTION'])),
        objectCount: objectCount,
        onlineCount: onlineCount,
        inRuleCount: inCount,
        outRuleCount: outCount
    })
}
return {
    zones: zones,
    summary: {
        zoneCount: zones.size(),
        enabledCount: enabledCount,
        objectCount: objectTotal,
        onlineCount: onlineTotal,
        ruleCount: ruleTotal
    }
}
