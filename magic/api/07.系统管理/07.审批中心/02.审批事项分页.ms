{
  "properties": {},
  "id": "f23cef22cacc4a8cbe5ac3df7c3a1566",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "02.审批事项分页",
  "createTime": 1786756145867,
  "updateTime": 1786951315185,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"flowSatusType\":\"todo\",\"flowCode\":\"\",\"key\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按会话参与范围和视图分页读取审批事项，并批量补充发起人。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import approvalRuntime
import jsons
import tableRead
import tableSort
import tenantAccounts
import tenantRuntime

// 按用户参与关系分页读取审批事项，最多一次批量补充当前页发起人名称。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var principal = approvalRuntime.principal()
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.min(100, Math.max(1, body?.pageSize::int(20)))
var view = body?.flowSatusType || 'todo'
var isTodo = view == 'todo'
var isDone = view == 'done'
var source = isTodo ? 'flow_task' : (isDone ? 'flow_his_task' : 'flow_instance')
var entries = tableRead.from(source, 'entry', tenant)
var field = (name, type) => tableRead.column('entry', name, type)
var definition = (name, type) => tableRead.column('definition', name, type)
var instance = (name, type) => tableRead.column(isTodo || isDone ? 'instance' : 'entry', name, type)
var id = field('id', 'NUMBER')
var instanceId = isTodo || isDone ? field('instance_id', 'NUMBER') : id
var flowStatus = isTodo ? field('flow_status', 'TEXT') : instance('flow_status', 'TEXT')
var ext = instance('ext', 'TEXT')
var flowCode = definition('flow_code', 'TEXT')
var flowName = definition('flow_name', 'TEXT')
entries.where(tableRead.equal(field('del_flag', 'TEXT'), '0'))
entries.leftJoin('flow_definition', 'definition', 'id', 'definition_id', [])
entries.where(tableRead.not(tableRead.isNull(definition('id', 'NUMBER'))))

if (isTodo || isDone) {
    entries.leftJoin('flow_instance', 'instance', 'id', 'instance_id', [])
    entries.where(tableRead.not(tableRead.isNull(instance('id', 'NUMBER'))))
}

if (isTodo) {
    // 一个任务可能匹配用户、角色和机构多条候选规则，按任务键先去重再参与分页。
    var candidates = tableRead.from('flow_user', 'candidate', tenant)
    var associated = tableRead.column('candidate', 'associated', 'NUMBER')
    candidates.where(tableRead.equal(tableRead.column('candidate', 'del_flag', 'TEXT'), '0'))
    candidates.where(tableRead.in(tableRead.column('candidate', 'processed_by', 'TEXT'), principal.permissionKeys))
    entries.leftJoinGrouped(candidates, 'eligible', 'associated', 'id', {associated: associated}, [associated])
    entries.where(tableRead.not(tableRead.isNull(tableRead.column('eligible', 'associated', 'NUMBER'))))
    entries.where(tableRead.equal(field('node_type', 'NUMBER'), 1))
    entries.where(tableRead.equal(instance('del_flag', 'TEXT'), '0'))
    entries.where(tableRead.not(tableRead.in(flowStatus, ['4', '6', '8'])))
} else if (isDone) {
    // 每个流程只保留当前人员的最后一条办理历史。
    var history = tableRead.from('flow_his_task', 'handled', tenant)
    var handledInstance = tableRead.column('handled', 'instance_id', 'NUMBER')
    history.where(tableRead.equal(tableRead.column('handled', 'approver', 'TEXT'), principal.userId))
    history.where(tableRead.equal(tableRead.column('handled', 'del_flag', 'TEXT'), '0'))
    entries.leftJoinGrouped(history, 'latest', 'instance_id', 'instance_id', {
        instance_id: handledInstance, latest_id: tableRead.max(tableRead.column('handled', 'id', 'NUMBER'))
    }, [handledInstance])
    entries.where(tableRead.equal(id, tableRead.column('latest', 'latest_id', 'NUMBER')))
    entries.where(tableRead.equal(field('approver', 'TEXT'), principal.userId))
} else {
    entries.where(tableRead.equal(field('create_by', 'TEXT'), principal.userId))
    var statuses = {process: ['1', '9'], archive: ['2', '4', '6', '8'],
                    achieve: ['2', '4', '6', '8'], all: ['1', '2', '4', '6', '8', '9', '11']}[view]
    if (statuses != null) { entries.where(tableRead.in(flowStatus, statuses)) }
}
if (body?.flowCode != null && body.flowCode != '') { entries.where(tableRead.equal(flowCode, body.flowCode)) }
if (body?.key != null && body.key != '') {
    var pattern = '%' + body.key + '%'
    entries.where(tableRead.any([tableRead.like(flowName, pattern), tableRead.like(ext, pattern)]))
}
var fields = {
    id: id, instanceId: instanceId,
    nodeCode: field('node_code', 'TEXT'), nodeName: field('node_name', 'TEXT'), nodeType: field('node_type', 'NUMBER'),
    definitionId: field('definition_id', 'NUMBER'), createTime: field('create_time', 'TIME'),
    updateTime: field('update_time', 'TIME'), tenantId: field('tenant_id', 'TEXT'), flowStatus: flowStatus,
    businessId: instance('business_id', 'TEXT'), activityStatus: instance('activity_status', 'NUMBER'),
    createBy: instance('create_by', 'TEXT'), ext: ext, flowCode: flowCode, flowName: flowName,
    formPath: isTodo || isDone ? field('form_path', 'TEXT') : definition('form_path', 'TEXT'),
    businessName: tableRead.coalesce([tableRead.jsonText(ext, 'businessName'), flowName])
}
if (isDone) {
    fields.message = field('message', 'TEXT')
} else {
    fields.formCustom = isTodo ? field('form_custom', 'TEXT') : definition('form_custom', 'TEXT')
}
var sort = tableSort.field(body, {businessName: 'businessName', flowName: 'flowName',
    createTime: 'createTime', updateTime: 'updateTime'}, 'createTime')
var page = entries.page(fields, fields[sort], tableSort.direction(body), 'id', pageNum, pageSize)
var names = tenantAccounts.names(page.rows.map(item => item.createBy).distinct())
for (item in page.rows) {
    item.id = item.id + ''
    item.definitionId = item.definitionId + ''
    item.instanceId = item.instanceId + ''
    item.businessId = item.businessId == null ? null : item.businessId + ''
    item.createName = names[item.createBy] || item.createBy
    try {
        item.ext = item.ext == null || item.ext == '' ? {} : jsons.toJSONObject(item.ext)
    } catch (error) {
        item.ext = {}
    }
    item.businessName = item.ext?.businessName || item.flowName
}
return {total: page.total, list: page.rows}
