{
  "properties": {},
  "id": "a75efba824d64e6f9a3f12561f0a9f10",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "04.审批统计",
  "createTime": 1785673588384,
  "updateTime": 1786191951453,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/summary",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "统计当前租户用户的待办、发起、办理和归档事项。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalRuntime
import tableRead
import tenantRuntime

// 汇总当前租户用户的待办、发起、办理和归档事项。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var principal = approvalRuntime.principal()
var tasks = tableRead.from('flow_task', 'task', tenant)
var task = (name, type) => tableRead.column('task', name, type)
var candidate = (name, type) => tableRead.column('candidate', name, type)
tasks.leftJoin('flow_user', 'candidate', 'associated', 'id', [
    tableRead.equal(candidate('del_flag', 'TEXT'), '0'),
    tableRead.in(candidate('processed_by', 'TEXT'), principal.permissionKeys)
])
tasks.where(tableRead.not(tableRead.isNull(candidate('associated', 'NUMBER'))))
tasks.where(tableRead.equal(task('del_flag', 'TEXT'), '0'))
tasks.where(tableRead.equal(task('node_type', 'NUMBER'), 1))
tasks.where(tableRead.not(tableRead.in(task('flow_status', 'TEXT'), ['4', '6', '8'])))
var todo = tasks.grouped({total: tableRead.countDistinct(task('id', 'NUMBER'))}, [])[0].total

// 发起与归档共用同一组用户范围，在库内按状态聚合。
var instances = tableRead.from('flow_instance', 'instance', tenant)
var instance = (name, type) => tableRead.column('instance', name, type)
var status = instance('flow_status', 'TEXT')
instances.where(tableRead.equal(instance('del_flag', 'TEXT'), '0'))
instances.where(tableRead.equal(instance('create_by', 'TEXT'), principal.userId))
instances.where(tableRead.in(status, ['1', '2', '4', '6', '8', '9']))
var byStatus = instances.grouped({status: status, total: tableRead.count()}, [status])
var process = 0
var archive = 0
for (row in byStatus) {
    if (['1', '9'].contains(row.status)) { process += row.total }
    else { archive += row.total }
}

var history = tableRead.from('flow_his_task', 'handled', tenant)
var handled = (name, type) => tableRead.column('handled', name, type)
history.where(tableRead.equal(handled('del_flag', 'TEXT'), '0'))
history.where(tableRead.equal(handled('approver', 'TEXT'), principal.userId))
var done = history.grouped({total: tableRead.countDistinct(handled('instance_id', 'NUMBER'))}, [])[0].total
return {todo: todo, process: process, done: done, archive: archive}
