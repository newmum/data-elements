{
  "properties": {},
  "id": "tenant_list_magic_01",
  "script": null,
  "groupId": "d28618bcdcca47279ce2c6f513002948",
  "name": "01.租户列表",
  "createTime": 1787162475819,
  "updateTime": 1787182300277,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20,\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "POST /sym/tenant/list，分页查询租户；使用请求参数 pageNum、pageSize、keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.租户列表
// 路由：POST /sym/tenant/list
// 业务说明：POST /sym/tenant/list，分页查询租户；使用请求参数 pageNum、pageSize、keyword。
import tenantRuntime

var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
  for (key in keys) {
    if (row != null && row[key] != null) {
      return row[key]
    }
  }
  return null
}
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var currentTenantId = text(tenantRuntime.id())
if (currentTenantId == '') {
  exit 401, '未获取到当前租户'
}
var pageNum = body == null || body.pageNum == null ? 1 : body.pageNum::int
var pageSize = body == null || body.pageSize == null ? 20 : body.pageSize::int
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = pageSize < 1 ? 20 : (pageSize > 200 ? 200 : pageSize)
var keyword = text(body == null ? null : body.keyword).toLowerCase()
var rows = []
// 以下从 baseline 业务表读取或维护当前操作涉及的记录。
for (item in db.table('baseline.sym_tenant_t').where().eq('is_del', 0).orderBy('sort_no','asc').orderBy('created_time','asc').select()) {
  var itemText = (text(rowValue(item,['code'])) + ' ' + text(rowValue(item,['name']))).toLowerCase()
  if (keyword == '' || itemText.contains(keyword)) {
    rows.add({ tid: rowValue(item,['tid']), code: rowValue(item,['code']), name: rowValue(item,['name']), status: rowValue(item,['status']), initFlag: rowValue(item,['initFlag','init_flag']), isolationMode: rowValue(item,['isolationMode','isolation_mode']), jsonConfig: rowValue(item,['jsonConfig','json_config']), sortNo: rowValue(item,['sortNo','sort_no']), createdTime: rowValue(item,['createdTime','created_time']), updatedTime: rowValue(item,['updatedTime','updated_time']) })
  }
}
var from = (pageNum - 1) * pageSize
if (from > rows.size()) {
  from = rows.size()
}
var to = from + pageSize
if (to > rows.size()) {
  to = rows.size()
}
return { list: rows.subList(from,to), total: rows.size(), pageNum: pageNum, pageSize: pageSize }
