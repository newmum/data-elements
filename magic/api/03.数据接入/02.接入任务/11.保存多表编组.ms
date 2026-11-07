{
  "properties" : { },
  "id" : "c276687fa7cb4a0fada25b972ccda4b1",
  "script" : null,
  "groupId" : "99c8dd7189af40939e5110b76bcd7160",
  "name" : "11.保存多表编组",
  "createTime" : 1791112840367,
  "updateTime" : 1791118050698,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/multi/save",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"groupId\":\"32位UUID\",\"groupName\":\"案件多表接入\",\"taskIds\":[\"任务1\",\"任务2\"]}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"success\":true}",
  "description" : "创建多表任务编组：事务验证当前租户现有单表任务及来源表，固定请求标识重复提交返回原编组。只建立关联，不部署或运行统一NiFi流程。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import dataScope
import tableRead
import '@/common/multi-task-visible-query' as multiTaskVisibleQuery
import cn.dev33.satoken.stp.StpUtil
import java.util.UUID
import java.util.HashSet
import java.util.ArrayList

/**
 * 创建多表任务编组（POST /ods/task/multi/save）。
 * 接收编组请求标识、名称和有序的 2 至 100 个任务 ID，返回编组 ID、创建标志及成员数。
 * 在当前租户及角色范围内批量校验任务和来源。同一创建人以相同请求重试时返回已有编组。
 * 编组及成员在同一事务中保存，不部署或运行 NiFi 流程。
 */
var text = value => value == null ? '' : ('' + value).trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
var groupId = text(body?.groupId)
var groupName = text(body?.groupName)
var taskIds = body?.taskIds
if (!groupId.matches('^[a-fA-F0-9]{32}$')) { exit 400, '编组请求标识无效' }
if (groupName == '' || groupName.length() > 200) { exit 400, '编组名称须为1到200个字符' }
if (taskIds == null || taskIds.size() < 2 || taskIds.size() > 100) { exit 400, '每个编组需包含2至100个单表任务' }
var orderedIds = new ArrayList()
var seenIds = new HashSet()
for (rawId in taskIds) {
    var taskId = text(rawId)
    if (!taskId.matches('^[A-Za-z0-9_-]{1,32}$') || !seenIds.add(taskId)) {
        exit 400, '单表任务标识无效或重复'
    }
    orderedIds.add(taskId)
}
if (!StpUtil.isLogin()) { exit 401, '登录已失效' }
var actor = text(StpUtil.getLoginId())
var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId != actor) { exit 403, '无法确定当前数据范围' }

var compareSaved = existing => {
    if (text(existing.createdBy || existing.created_by || existing.CREATED_BY) != actor) { exit 403, '无权使用此编组请求标识' }
    var isDeleted = existing.isDel == null ? (existing.is_del == null ? existing.IS_DEL : existing.is_del) : existing.isDel
    if (isDeleted != 0) { exit 409, '此编组请求已经删除，请重新创建' }
    var saved = db.table('data_access_multi_group_item_t').where()
        .eq('tenant_id', tenantId).eq('group_id', groupId)
        .orderBy('sort_no', 'asc').select()
    if (text(existing.groupName || existing.group_name || existing.GROUP_NAME) != groupName || saved.size() != orderedIds.size()) {
        exit 409, '相同请求标识已用于其他编组内容'
    }
    var index = 0
    for (savedRow in saved) {
        if (text(savedRow.accessTaskId || savedRow.access_task_id || savedRow.ACCESS_TASK_ID) != text(orderedIds[index])) {
            exit 409, '相同请求标识已用于其他编组成员'
        }
        index++
    }
    return {groupId: groupId, created: false, tableCount: saved.size()}
}
var precheckPassed = false
try {
    return db.transaction(() => {
        // 共享查询限定当前租户和角色范围。任一任务不可见时拒绝整批。
        var taskQuery = multiTaskVisibleQuery(tenantId, scopeMode, scopeUserId, scopeOrgIds)
        var taskIdColumn = tableRead.column('task', 'tid', 'TEXT')
        var sourceIdColumn = tableRead.column('source_table', 'tid', 'TEXT')
        taskQuery.where(tableRead.in(taskIdColumn, orderedIds))
        var taskRows = taskQuery.rows({task_id: taskIdColumn, source_id: sourceIdColumn},
            taskIdColumn, 'asc', orderedIds.size())
        if (taskRows.size() != orderedIds.size()) { exit 403, '部分单表任务不存在或无权访问' }

        var taskSource = {}
        for (task in taskRows) {
            taskSource[text(task.task_id)] = text(task.source_id)
        }
        var sources = new HashSet()
        for (taskId in orderedIds) {
            var sourceId = text(taskSource[taskId])
            if (sourceId == '' || !sources.add(sourceId)) { exit 400, '来源表缺失或在编组中重复' }
        }

        // 重试也先校验当前数据权限，再对照已保存的内容与成员顺序。
        precheckPassed = true
        var existing = db.table('data_access_multi_group_t').where()
            .eq('tid', groupId).eq('tenant_id', tenantId).selectOne()
        if (existing != null) { return compareSaved(existing) }

        db.table('data_access_multi_group_t').primary('tid').insert({
            tid: groupId, tenant_id: tenantId, group_name: groupName,
            created_by: actor, created_time: new Date(), updated_time: new Date(), is_del: 0
        })
        var members = new ArrayList()
        var order = 1
        for (taskId in orderedIds) {
            members.add({
                tid: UUID.randomUUID().toString().replace('-', ''), tenant_id: tenantId,
                group_id: groupId, source_table_id: taskSource[taskId],
                access_task_id: taskId, sort_no: order,
                created_time: new Date(), updated_time: new Date()
            })
            order++
        }
        // 按请求顺序一次批量插入，成员写入失败时编组也随事务回滚。
        db.table('data_access_multi_group_item_t').batchInsert(members, 100)
        return {groupId: groupId, created: true, tableCount: members.size()}
    })
} catch (error) {
    // 同一请求键的并发提交可能都读到“尚不存在”，然后第二个在主键插入处失败。
    // 必须在失败事务回滚后开启新读视图核对赢家，不能把主键冲突直接返给重试者。
    if (!precheckPassed) { throw error }
    var winner = db.table('data_access_multi_group_t').where()
        .eq('tid', groupId).eq('tenant_id', tenantId).selectOne()
    if (winner == null) { throw error }
    return compareSaved(winner)
}
