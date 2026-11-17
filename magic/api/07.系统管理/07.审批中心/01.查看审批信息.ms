{
  "properties" : { },
  "id" : "e827efbdb789464ea6fbf4b8570f4a61",
  "script" : null,
  "groupId" : "b2bc4c16954544a69d4bcb4700dadce1",
  "name" : "01.查看审批信息",
  "createTime" : null,
  "updateTime" : 1794837671716,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/info",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ {
    "name" : "access",
    "value" : "TENANT_SESSION",
    "description" : null,
    "required" : false,
    "dataType" : "String",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ],
  "requestBody" : null,
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : null,
  "description" : "按租户与当前用户的流程参与关系读取审批详情和节点。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import approvalRuntime
import jsons
import tableRead
import tenantAccounts
import tenantRuntime

// 读取当前租户的流程详情。仅发起人、当前办理人和历史办理人可查看。
var business = (businessId || '') + ''
var requestedInstance = (instanceId || '') + ''
if (business == '' && requestedInstance == '') { exit -1, 'businessId 和 instanceId 不能同时为空' }
if (requestedInstance != '' && !requestedInstance.matches('[1-9][0-9]{0,18}')) {
    exit -1, '审批流程ID无效'
}
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var principal = approvalRuntime.principal()
var instances = tableRead.from('flow_instance', 'instance', tenant)
var instanceField = (name, type) => tableRead.column('instance', name, type)
var definition = (name, type) => tableRead.column('definition', name, type)
var instanceFields = {
    id: instanceField('id', 'NUMBER'), businessId: instanceField('business_id', 'TEXT'),
    flowStatus: instanceField('flow_status', 'TEXT'), definitionId: instanceField('definition_id', 'NUMBER'),
    createBy: instanceField('create_by', 'TEXT'), nodeCode: instanceField('node_code', 'TEXT'),
    nodeName: instanceField('node_name', 'TEXT'), ext: instanceField('ext', 'TEXT'),
    createTime: instanceField('create_time', 'TIME'), updateTime: instanceField('update_time', 'TIME'),
    flowCode: definition('flow_code', 'TEXT'), flowName: definition('flow_name', 'TEXT')
}
instances.where(tableRead.equal(instanceField('del_flag', 'TEXT'), '0'))
instances.leftJoin('flow_definition', 'definition', 'id', 'definition_id', [])
instances.where(tableRead.not(tableRead.isNull(definition('id', 'NUMBER'))))
if (business != '') { instances.where(tableRead.equal(instanceFields.businessId, business)) }
if (requestedInstance != '') {
    var id = requestedInstance.asLong()
    if (id == null || id <= 0) { exit -1, '审批流程ID无效' }
    instances.where(tableRead.equal(instanceFields.id, id))
}
// 老申请可能有多次流转。没有实例号时按创建时间和主键取最近流程。
var selected = instances.rows(instanceFields, [instanceFields.createTime, instanceFields.id], ['desc', 'desc'], 1)
if (selected.isEmpty()) { exit -1, '审批流程不存在或已删除' }
var instance = selected[0]
var canView = (instance.createBy + '') == principal.userId
if (!canView) {
    var current = tableRead.from('flow_user', 'candidate', tenant)
    var candidate = (name, type) => tableRead.column('candidate', name, type)
    var taskReference = (name, type) => tableRead.column('task_reference', name, type)
    current.leftJoin('flow_task', 'task_reference', 'id', 'associated', [
        tableRead.equal(taskReference('del_flag', 'TEXT'), '0'),
        tableRead.equal(taskReference('instance_id', 'NUMBER'), instance.id)
    ])
    current.where(tableRead.equal(candidate('del_flag', 'TEXT'), '0'))
    current.where(tableRead.in(candidate('processed_by', 'TEXT'), principal.permissionKeys))
    current.where(tableRead.not(tableRead.isNull(taskReference('id', 'NUMBER'))))
    canView = !current.rows({id: taskReference('id', 'NUMBER')}, 1).isEmpty()
}
if (!canView) {
    var involvement = tableRead.from('flow_his_task', 'involvement', tenant)
    var previous = (name, type) => tableRead.column('involvement', name, type)
    involvement.where(tableRead.equal(previous('instance_id', 'NUMBER'), instance.id))
    involvement.where(tableRead.equal(previous('del_flag', 'TEXT'), '0'))
    involvement.where(tableRead.equal(previous('approver', 'TEXT'), principal.userId))
    canView = !involvement.rows({id: previous('id', 'NUMBER')}, 1).isEmpty()
}
if (!canView) { exit -1, '您不是该流程的发起人或参与人，无权查看' }

var tasks = tableRead.from('flow_task', 'task', tenant)
var task = (name, type) => tableRead.column('task', name, type)
tasks.where(tableRead.equal(task('instance_id', 'NUMBER'), instance.id))
tasks.where(tableRead.equal(task('del_flag', 'TEXT'), '0'))
tasks.where(tableRead.equal(task('node_type', 'NUMBER'), 1))
var active = tasks.rows({id: task('id', 'NUMBER'), nodeCode: task('node_code', 'TEXT'),
    nodeName: task('node_name', 'TEXT')}, [task('create_time', 'TIME'), task('id', 'NUMBER')], ['desc', 'desc'], 1)
var currentTask = active.isEmpty() ? null : active[0]

var history = tableRead.from('flow_his_task', 'history', tenant)
var entry = (name, type) => tableRead.column('history', name, type)
history.where(tableRead.equal(entry('instance_id', 'NUMBER'), instance.id))
history.where(tableRead.equal(entry('del_flag', 'TEXT'), '0'))
var historyFields = {
    id: entry('id', 'NUMBER'), nodeCode: entry('node_code', 'TEXT'), nodeName: entry('node_name', 'TEXT'),
    skipType: entry('node_type', 'NUMBER'), approver: entry('approver', 'TEXT'), message: entry('message', 'TEXT'),
    createTime: entry('create_time', 'TIME'), updateTime: entry('update_time', 'TIME')
}
var nodes = history.rows(historyFields, [historyFields.createTime, historyFields.id], ['asc', 'asc'], 1001)
if (nodes.size() > 1000) { exit 413, '流程历史节点超过1000项' }
var identityIds = []
if (instance.createBy != null && instance.createBy != '') { identityIds.add(instance.createBy) }
for (node in nodes) {
    if (node.approver != null && node.approver != '') { identityIds.add(node.approver) }
}
var names = tenantAccounts.names(identityIds.distinct())
for (node in nodes) {
    node.id = node.id + ''
    node.approverName = node.approver == null ? null : (names[node.approver] || node.approver)
}
var extension = {}
if (instance.ext != null && instance.ext != '') {
    try { extension = jsons.toJSONObject(instance.ext) }
    catch (error) { extension = {} }
}

return {
    approvalInfo: {
        instanceId: instance.id + '', businessId: instance.businessId,
        flowStatus: instance.flowStatus, flowCode: instance.flowCode, flowName: instance.flowName,
        definitionId: instance.definitionId + '', createBy: instance.createBy + '',
        createByName: instance.createBy == null ? null : (names[instance.createBy] || instance.createBy),
        createTime: instance.createTime, updateTime: instance.updateTime,
        nodeCode: currentTask?.nodeCode || instance.nodeCode, nodeName: currentTask?.nodeName || instance.nodeName,
        taskId: currentTask?.id == null ? null : currentTask.id + '',
        ext: extension
    },
    flowNodes: nodes
}
