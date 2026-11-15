{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "tenant_user_page_magic_01",
  "script": null,
  "groupId": "d28618bcdcca47279ce2c6f513002948",
  "name": "03.用户租户分页",
  "createTime": 1787162475917,
  "updateTime": 1787184457874,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"targetTenantId\": \"目标租户ID\",\n  \"keyword\": \"\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "应用独立本地账号接口；业务规则由租户账号 Magic 函数执行。共享控制库账号及成员关系分支已退役；使用请求参数 targetTenantId、keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantAccounts
import tenantRuntime

// 在当前应用内分页读取本地用户。目标租户只能作为当前会话范围的交叉核对。
var current = tenantRuntime.id()
if (!current) { exit 401, '当前租户无效' }
var target = (body?.targetTenantId || current) + ''
if (target != current) { exit 403, '本地账号只能查询所属应用' }
if (!tenantAccounts.isLocal(current)) { exit 403, '当前租户未启用本地账号' }
var pageNum = Math.max(1, body?.pageNum || 1)
var pageSize = Math.min(200, Math.max(1, body?.pageSize || 200))
var keyword = (body?.keyword || '').trim()
var users = tableRead.from('rm_user_t', 'account', current)
var column = (key, type) => tableRead.column('account', key, type)
users.where(tableRead.equal(column('deleted', 'NUMBER'), 0))
if (keyword != '') {
    users.where(tableRead.any([tableRead.containsLiteral(column('user_name', 'TEXT'), keyword),
        tableRead.containsLiteral(column('real_name', 'TEXT'), keyword)]))
}
var projection = {userId: column('id', 'TEXT'), userName: column('user_name', 'TEXT'),
    realName: column('real_name', 'TEXT'), phone: column('phone', 'TEXT')}
var page = users.page(projection, column('create_time', 'TIME'), 'asc', 'id', 'asc', pageNum, pageSize, true, 200)
var list = page.rows.map(row => ({...row, assigned: true, defaultTenant: true}))
return {list: list, total: page.total, accountSource: 'LOCAL'}
