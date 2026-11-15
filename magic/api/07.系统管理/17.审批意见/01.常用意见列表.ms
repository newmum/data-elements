{
  "properties": {},
  "id": "f8a5b9d39f3a43dabb3ddcbd0c1a2aed",
  "script": null,
  "groupId": "08e1a2c4867d44009e15a322d2aea3ee",
  "name": "01.常用意见列表",
  "createTime": 1782955344452,
  "updateTime": 1784446270673,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取本人保存的常用审批意见。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import tableRead
import tenantRuntime

// 读取本人保存的常用审批意见。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var user = StpUtil.getLoginId() + ''
var suggestions = tableRead.from('flow_suggestion', 'suggestion', tenant)
var column = (name, kind) => tableRead.column('suggestion', name, kind)
suggestions.where(tableRead.equal(column('create_by', 'TEXT'), user))
suggestions.where(tableRead.equal(column('is_del', 'NUMBER'), 0))
var fields = {
    tid: column('tid', 'TEXT'),
    content: column('content', 'TEXT'),
    useNum: column('use_num', 'NUMBER'),
    createTime: column('create_time', 'TIME'),
    createBy: column('create_by', 'TEXT'),
    updateTime: column('update_time', 'TIME'),
    updateBy: column('update_by', 'TEXT'),
    tenantId: column('tenant_id', 'TEXT')
}
var rows = suggestions.rows(fields, [fields.useNum, fields.tid], ['desc', 'asc'], 1001)
if (rows.size() > 1000) { exit 413, '常用审批意见超过1000项' }
return rows
