{
  "properties": {},
  "id": "b81216bf81ee41d8ad5d09526bf07701",
  "script": null,
  "groupId": "c1237871230f4f478b31fc9b2d00b13c",
  "name": "01.审批委托列表",
  "createTime": 1786757000000,
  "updateTime": 1786951315648,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "查询本人的有效委托规则，并批量读取受托人名称。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import tableRead
import tenantAccounts
import tenantRuntime

// 查询本人的有效委托规则，并批量读取受托人名称。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var user = StpUtil.getLoginId() + ''
var rules = tableRead.from('wf_approval_delegation_t', 'rule', tenant)
var column = (name, kind) => tableRead.column('rule', name, kind)
rules.where(tableRead.equal(column('delegator_user_id', 'TEXT'), user))
rules.where(tableRead.equal(column('is_del', 'NUMBER'), 0))
var fields = {
    tid: column('tid', 'TEXT'),
    delegateUserId: column('delegate_user_id', 'TEXT'),
    flowCode: column('flow_code', 'TEXT'),
    startTime: column('start_time', 'TIME'),
    endTime: column('end_time', 'TIME'),
    reason: column('reason', 'TEXT'),
    status: column('status', 'NUMBER'),
    createdTime: column('created_time', 'TIME')
}
var rows = rules.rows(fields, [fields.status, fields.endTime, fields.tid], ['desc', 'desc', 'asc'], 1001)
if (rows.size() > 1000) { exit 413, '审批委托超过1000项' }
if (rows.isEmpty()) { return [] }
var names = tenantAccounts.names(rows.map(item => item.delegateUserId).distinct())
rows.each(item -> item.delegateUserName = names[item.delegateUserId] || item.delegateUserId)
return rows
