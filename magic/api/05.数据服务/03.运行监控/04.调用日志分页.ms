{
  "properties": {},
  "id": "9a2f6c4e1d8b3a7f5e0c9b2d4a6f8c31",
  "script": null,
  "groupId": "8c4d2f6a1b7e4c3d9a0f5e2b6d8c1a37",
  "name": "04.调用日志分页",
  "createTime": 1793900000000,
  "updateTime": 1793900000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitor/calls",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 10,\n    \"success\": null,\n    \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"data\": {\n        \"total\": 0,\n        \"pageNum\": 1,\n        \"pageSize\": 10,\n        \"list\": []\n    }\n}",
  "description": "按当前登录租户分页查询已发布数据服务的调用日志，支持成功状态与服务名称筛选。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var expected = (key + '').toLowerCase()
        var found = null
        for (actual in row.keySet()) {
            if ((actual + '').toLowerCase() == expected && row[actual] != null) {
                found = row[actual]
            }
        }
        if (found != null) { return found }
    }
    return null
}
var toLong = value => value == null ? 0 : (value + '')::long
var toInt = value => value == null ? 0 : (value + '')::int

var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '请先登录' }
var pageNum = body == null || body.pageNum == null ? 1 : (body.pageNum + '')::int
var pageSize = body == null || body.pageSize == null ? 10 : (body.pageSize + '')::int
if (pageNum < 1) { pageNum = 1 }
if (pageSize < 1) { pageSize = 10 }
if (pageSize > 100) { pageSize = 100 }
var keyword = body == null || body.keyword == null ? '' : (body.keyword + '').trim()
var success = body == null || body.success == null ? null : (body.success + '')::int
var offset = (pageNum - 1) * pageSize
var where = " where tenant_id = #{tenantId}"
if (success != null && (success == 0 || success == 1)) {
  where = where + " and success = #{success}"
}
if (keyword != '') {
  where = where + " and (instr(lower(ifnull(service_name, '')), lower(#{keyword})) > 0 or instr(lower(ifnull(path, '')), lower(#{keyword})) > 0)"
}

var totalRow = db.selectOne("select count(1) total from fs_call_log" + where)
var rows = db.select("select id, flow_id flowId, service_name serviceName, method, path, status, success, duration_ms durationMs, created_at createdAt from fs_call_log" + where + " order by created_at desc, id desc limit #{pageSize} offset #{offset}")

var list = []
for (row in rows) {
  list.add({
    id: text(rowValue(row, ['id', 'ID'])),
    flowId: text(rowValue(row, ['flowId', 'FLOWID', 'flow_id', 'FLOW_ID'])),
    serviceName: text(rowValue(row, ['serviceName', 'SERVICENAME', 'service_name', 'SERVICE_NAME'])),
    method: text(rowValue(row, ['method', 'METHOD'])),
    path: text(rowValue(row, ['path', 'PATH'])),
    status: toInt(rowValue(row, ['status', 'STATUS'])),
    success: toInt(rowValue(row, ['success', 'SUCCESS'])),
    durationMs: toLong(rowValue(row, ['durationMs', 'DURATIONMS', 'duration_ms', 'DURATION_MS'])),
    createdAt: text(rowValue(row, ['createdAt', 'CREATEDAT', 'created_at', 'CREATED_AT']))
  })
}

return { total: toLong(rowValue(totalRow, ['total', 'TOTAL'])), pageNum: pageNum, pageSize: pageSize, list: list }
