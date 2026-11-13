{
  "properties" : { },
  "id" : "c562ecb2777d466f8ce04907409b70a7",
  "script" : null,
  "groupId" : "99c8dd7189af40939e5110b76bcd7160",
  "name" : "10.多表编组分页",
  "createTime" : 1791112739532,
  "updateTime" : 1791118050574,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/multi/page",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"list\":[],\"total\":0}",
  "description" : "只读当前会话租户下的多表任务编组与成员，逐表状态从已有单表接入任务实时读取；支持名称搜索和分页，不创建或运行NiFi流程。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import cn.hutool.core.util.NumberUtil
import dataScope
import tableRead
import tableSort
import tenantRuntime

/**
 * POST /ods/task/multi/page 分页读取当前租户可见的多表编组及成员任务状态。
 * 支持编组搜索、编组排序和成员排序。按本页一次批量读取成员，最多返回 10000 条。
 * 当前范围必须覆盖编组的全部来源。查询期间成员或授权发生变化时，请求方需刷新重试。
 */
var text = value => value == null ? '' : ('' + value).trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前租户无效'
}
var pageNum = text(body?.pageNum).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageNum)) : 1
var pageSize = text(body?.pageSize).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageSize)) : 20
if (pageNum < 1 || pageNum > 100000) {
    pageNum = 1
}
if (pageSize < 1 || pageSize > 100) {
    pageSize = 20
}
var keyword = text(body?.keyword)
if (keyword.length() > 100) {
    exit 400, '搜索词过长'
}

var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') {
    exit 403, '无法确定当前数据范围'
}
var groupSort = tableSort.field(body, {
    groupName: 'groupName', createdTime: 'createdTime', tableCount: 'tableCount',
    runningCount: 'runningCount', errorCount: 'errorCount'
}, 'createdTime')
var groupDirection = tableSort.direction(body)
var itemSort = tableSort.field({sortField: body?.itemSortField}, {
    sourceTableName: 'sourceTableName', lastRunning: 'lastRunning', endRunning: 'endRunning'
}, 'sourceTableName')
var itemDirection = tableSort.direction({sortDir: body?.itemSortDir})

var memberColumn = (column, kind) => tableRead.column('member_row', column, kind)
var tableColumn = (column, kind) => tableRead.column('source_table', column, kind)
var sourceColumn = (column, kind) => tableRead.column('source_config', column, kind)
var taskColumn = (column, kind) => tableRead.column('single_task', column, kind)
var memberGroupId = memberColumn('group_id', 'TEXT')
var members = tableRead.from('data_access_multi_group_item_t', 'member_row', tenantId)
members.leftJoin('db_table_t', 'source_table', 'tid', 'source_table_id', [
    tableRead.equal(tableColumn('is_del', 'NUMBER'), 0)
])
members.leftJoin('db_datasource_t', 'source_config', 'tenant_id', 'tenant_id', [
    tableRead.equal(sourceColumn('tid', 'TEXT'), tableColumn('datasource_id', 'TEXT')),
    tableRead.equal(sourceColumn('is_del', 'NUMBER'), 0)
])
members.leftJoin('data_access_agg_task_t', 'single_task', 'tid', 'access_task_id', [
    tableRead.equal(taskColumn('source_table_id', 'TEXT'), memberColumn('source_table_id', 'TEXT')),
    tableRead.equal(taskColumn('is_del', 'NUMBER'), 0)
])

// 空归属和失效来源均计为不可见，先按整组范围过滤再计算分页总数。
var deniedConditions = [
    tableRead.isNull(tableColumn('tid', 'TEXT')),
    tableRead.isNull(sourceColumn('tid', 'TEXT'))
]
if (scopeMode == 'OWNER') {
    var creatorColumn = sourceColumn('created_by', 'TEXT')
    deniedConditions.add(tableRead.isNull(creatorColumn))
    deniedConditions.add(tableRead.not(tableRead.equal(creatorColumn, scopeUserId)))
} else if (scopeMode == 'ORG') {
    var organizationColumn = sourceColumn('org_id', 'TEXT')
    deniedConditions.add(tableRead.isNull(organizationColumn))
    deniedConditions.add(tableRead.not(tableRead.in(organizationColumn, scopeOrgIds)))
}
var denied = tableRead.any(deniedConditions)
var availableTaskId = taskColumn('tid', 'TEXT')
var taskStatus = taskColumn('task_status', 'NUMBER')
var missingTask = tableRead.isNull(availableTaskId)
var groupFacts = {
    group_id: memberGroupId,
    denied_count: tableRead.countIf(denied),
    table_count: tableRead.count(),
    running_count: tableRead.countIf(tableRead.equal(taskStatus, 1)),
    error_count: tableRead.countIf(tableRead.any([missingTask, tableRead.equal(taskStatus, 2)]))
}

var groupsQuery = tableRead.from('data_access_multi_group_t', 'group_row', tenantId)
var groupColumn = (column, kind) => tableRead.column('group_row', column, kind)
var factCount = name => tableRead.coalesce([tableRead.column('group_facts', name, 'NUMBER'), 0])
groupsQuery.leftJoinGrouped(members, 'group_facts', 'group_id', 'tid', groupFacts, [memberGroupId])
groupsQuery.where(tableRead.all([
    tableRead.equal(groupColumn('is_del', 'NUMBER'), 0),
    tableRead.equal(factCount('denied_count'), 0)
]))
if (keyword != '') {
    groupsQuery.where(tableRead.like(groupColumn('group_name', 'TEXT'), '%' + keyword + '%'))
}
var groupFields = {
    groupId: groupColumn('tid', 'TEXT'),
    groupName: groupColumn('group_name', 'TEXT'),
    createdBy: groupColumn('created_by', 'TEXT'),
    createdTime: groupColumn('created_time', 'TIME'),
    updatedTime: groupColumn('updated_time', 'TIME'),
    tableCount: factCount('table_count'),
    runningCount: factCount('running_count'),
    errorCount: factCount('error_count')
}
var page = groupsQuery.page(groupFields, groupFields[groupSort], groupDirection, 'tid', pageNum, pageSize)
var groupRows = page.rows
if (groupRows.size() == 0) {
    return {list: [], total: page.total, pageNum: pageNum, pageSize: pageSize}
}

// 分组聚合已保留查询快照。成员读取重新按当前页和活动编组约束，并同步核验每个来源。
var groupIds = groupRows.map(group => group.groupId)
var currentGroupId = tableRead.column('active_group', 'tid', 'TEXT')
members.leftJoin('data_access_multi_group_t', 'active_group', 'tid', 'group_id', [
    tableRead.equal(tableRead.column('active_group', 'is_del', 'NUMBER'), 0)
])
members.where(tableRead.in(currentGroupId, groupIds))
var memberFields = {
    itemId: memberColumn('tid', 'TEXT'),
    groupId: currentGroupId,
    sourceTableId: memberColumn('source_table_id', 'TEXT'),
    taskId: memberColumn('access_task_id', 'TEXT'),
    sortNo: memberColumn('sort_no', 'NUMBER'),
    sourceTableName: tableColumn('table_name', 'TEXT'),
    sourceTableNameCn: tableColumn('table_name_cn', 'TEXT'),
    availableTaskId: availableTaskId,
    taskName: taskColumn('task_name', 'TEXT'),
    taskStatus: taskStatus,
    pipelineId: taskColumn('pipeline_id', 'TEXT'),
    monitorStatus: taskColumn('monitor_status', 'TEXT'),
    monitorMessage: taskColumn('monitor_msg', 'TEXT'),
    lastRunning: taskColumn('last_running', 'TEXT'),
    endRunning: taskColumn('end_running', 'TEXT'),
    deniedItemId: tableRead.when(denied, memberColumn('tid', 'TEXT'), null)
}
var memberSorts = {
    sourceTableName: tableRead.coalesce([memberFields.sourceTableNameCn, memberFields.sourceTableName]),
    lastRunning: memberFields.lastRunning,
    endRunning: memberFields.endRunning
}
// 只多读一条判断上限，数据库完成组内排序，成员 ID 作为最后的稳定顺序。
var memberRows = members.rows(memberFields,
    [currentGroupId, memberSorts[itemSort], memberFields.sortNo, memberFields.itemId],
    ['asc', itemDirection, 'asc', 'asc'], 10001)
for (member in memberRows) {
    if (member.deniedItemId != null) {
        exit 409, '编组成员或数据权限已变化，请刷新后重试'
    }
}
if (memberRows.size() > 10000) {
    exit 413, '本页编组成员超过10000，请缩小分页范围'
}
var itemsByGroup = {}
for (group in groupRows) {
    itemsByGroup[group.groupId] = []
}
for (member in memberRows) {
    itemsByGroup[member.groupId].add({
        itemId: member.itemId,
        sourceTableId: member.sourceTableId,
        taskId: member.taskId,
        sortNo: member.sortNo,
        taskAvailable: text(member.availableTaskId) != '',
        taskName: member.taskName,
        taskStatus: member.taskStatus,
        pipelineId: member.pipelineId,
        monitorStatus: member.monitorStatus,
        monitorMessage: member.monitorMessage,
        lastRunning: member.lastRunning,
        endRunning: member.endRunning,
        sourceTableName: member.sourceTableName,
        sourceTableNameCn: member.sourceTableNameCn
    })
}
var list = []
for (group in groupRows) {
    var items = itemsByGroup[group.groupId]
    if (items.size() != group.tableCount) {
        exit 409, '编组成员或数据权限已变化，请刷新后重试'
    }
    var creator = text(group.createdBy)
    list.add({
        groupId: text(group.groupId),
        groupName: group.groupName,
        createdBy: creator,
        canManage: creator == scopeUserId,
        createdTime: group.createdTime,
        updatedTime: group.updatedTime,
        tableCount: group.tableCount,
        runningCount: group.runningCount,
        errorCount: group.errorCount,
        items: items
    })
}
return {list: list, total: page.total, pageNum: pageNum, pageSize: pageSize}
