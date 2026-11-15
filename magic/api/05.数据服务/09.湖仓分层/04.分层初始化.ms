{
  "properties": {},
  "id": "1caf0bcbb2964dd08bddab6f98a9e75e",
  "script": null,
  "groupId": "b5badef9297e4dbb8a6028e72a393d4e",
  "name": "04.分层初始化",
  "createTime": 1789000000004,
  "updateTime": 1789000000004,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/init",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"datasourceId\": \"\",\n  \"schemas\": {\n    \"ODS\": \"ods\"\n  }\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"created\": [\"ODS\"],\n  \"skipped\": [],\n  \"total\": 6\n}",
  "description": "按当前租户幂等初始化六层湖仓（POST /dws/lake/init）：ODS/BASE/SUBJECT/TOPIC/LAKE_DW/LAKE_COLLECT。先一次查询已存在编码，再用一条多行插入补齐缺失分层。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import tenantRuntime
import "@post:/sym/user/me" as me

// 接口：04.分层初始化
// 路由：POST /dws/lake/init
// 业务说明：幂等初始化六层湖仓；已存在的 zone_code 跳过，缺失的一次性批量插入，不逐行查询。

var tenantId = tenantRuntime.id()
var userId = me()?.id
var datasourceId = body?.datasourceId == null ? '' : (body.datasourceId + '').trim()
var schemas = body?.schemas

var text = (value) => value == null ? '' : value + ''
var first = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        if (row[key] != null && text(row[key]).trim() != '') { return row[key] }
    }
    return null
}
var schemaOf = (code, fallback) => {
    if (schemas == null) { return '' }
    var value = schemas[code]
    return value == null ? '' : text(value).trim()
}

if (datasourceId != '') {
    var sourceCount = db.select("select count(1) as n from db_datasource_t where tid = #{datasourceId} and tenant_id = #{tenantId} and is_del = 0", { datasourceId: datasourceId, tenantId: tenantId })
    var sourceTotal = sourceCount == null || sourceCount.size() == 0 ? 0 : (first(sourceCount[0], ['n', 'N']) == null ? 0 : first(sourceCount[0], ['n', 'N']))
    if (sourceTotal == 0) { exit 400, '承载数据源不存在或不在当前租户' }
}

var presets = [
    { code: 'ODS', name: '原始库', type: 'ORIGIN', sort: 10, desc: '贴源落地，保留原始结构与增量' },
    { code: 'BASE', name: '基础库', type: 'BASE', sort: 20, desc: '清洗、标准化后的基础明细' },
    { code: 'SUBJECT', name: '主题库', type: 'SUBJECT', sort: 30, desc: '按业务主题组织的数据' },
    { code: 'TOPIC', name: '专题库', type: 'TOPIC', sort: 40, desc: '按专题或专项组织的数据' },
    { code: 'LAKE_DW', name: '数据湖-仓库区', type: 'LAKE', sort: 50, desc: '经开发处理后的统一存储区' },
    { code: 'LAKE_COLLECT', name: '数据湖-汇聚区', type: 'LAKE', sort: 60, desc: '跨系统汇聚与交换缓冲区' }
]

var existingRows = db.select("select zone_code as zoneCode from lake_zone_t where tenant_id = #{tenantId} and is_del = 0 and zone_code in ('ODS','BASE','SUBJECT','TOPIC','LAKE_DW','LAKE_COLLECT')", { tenantId: tenantId })
var existingCodes = []
for (row in existingRows) {
    var code = first(row, ['zoneCode', 'zonecode', 'zone_code', 'ZONECODE'])
    if (code != null) { existingCodes.add(text(code)) }
}

var created = []
var skipped = []
var values = []
var params = { tenantId: tenantId, userId: userId, datasourceId: datasourceId }
var index = 0
for (preset in presets) {
    if (existingCodes.contains(preset.code)) {
        skipped.add(preset.code)
    } else {
        values.add("(#{tid" + index + "}, #{tenantId}, #{zoneCode" + index + "}, #{zoneName" + index + "}, #{zoneType" + index + "}, #{datasourceId}, #{schemaName" + index + "}, 'ENABLED', #{sortNo" + index + "}, #{description" + index + "}, #{userId}, #{userId}, current_timestamp, current_timestamp, 0)")
        params["tid" + index] = IdUtil.simpleUUID()
        params["zoneCode" + index] = preset.code
        params["zoneName" + index] = preset.name
        params["zoneType" + index] = preset.type
        params["schemaName" + index] = schemaOf(preset.code, preset.code.toLowerCase())
        params["sortNo" + index] = preset.sort
        params["description" + index] = preset.desc
        created.add(preset.code)
        index = index + 1
    }
}
if (values.size() > 0) {
    db.update("insert into lake_zone_t (tid, tenant_id, zone_code, zone_name, zone_type, datasource_id, schema_name, status, sort_no, description, created_by, updated_by, created_time, updated_time, is_del) values " + values.join(','), params)
}
return { created: created, skipped: skipped, total: presets.size() }
