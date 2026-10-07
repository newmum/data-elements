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
// 接口：创建多表任务编组（POST /ods/task/multi/save）
// 单次预取 2–100 个单表任务、来源表及数据源，再在内存逐 ID 校验。
import tenantRuntime
import dataScope
import cn.dev33.satoken.stp.StpUtil
import java.util.UUID
import java.util.HashSet
import java.util.ArrayList

var text = value => value == null ? '' : ('' + value).trim()
var field = (row, name) => row == null ? null :
    (row[name] != null ? row[name] :
     (row[name.toLowerCase()] != null ? row[name.toLowerCase()] : row[name.toUpperCase()]))
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
var visibleSql = scopeMode == 'ALL' ? '1=1' :
    (scopeMode == 'OWNER' ? 'd.created_by=#{scopeUserId}' :
     (scopeOrgIds.size() == 0 ? '1=0' :
      'd.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>'))

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
    // 关联查询限定当前租户并套用角色数据范围。缺失/越权 ID 必须使整批失败。
    var taskRows = db.select("""
        select a.tid as taskId, t.tid as sourceId
          from data_access_agg_task_t a
          join db_table_t t on t.tid=a.source_table_id
               and t.tenant_id=#{tenantId} and t.is_del=0
          join db_datasource_t d on d.tid=t.datasource_id
               and d.tenant_id=#{tenantId} and d.is_del=0
         where a.tenant_id=#{tenantId} and a.is_del=0
           and a.tid in <foreach collection="orderedIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
           and ${visibleSql}
    """)
    if (taskRows.size() != orderedIds.size()) { exit 403, '部分单表任务不存在或无权访问' }
    var taskSource = {}
    for (task in taskRows) {
        taskSource[text(field(task, 'taskId'))] = text(field(task, 'sourceId'))
    }
    var sources = new HashSet()
    var rows = new ArrayList()
    for (taskId in orderedIds) {
        var sourceId = text(taskSource[text(taskId)])
        if (sourceId == '' || !sources.add(sourceId)) { exit 400, '来源表缺失或在编组中重复' }
        rows.add({taskId: taskId, sourceId: sourceId})
    }
    precheckPassed = true
    var existing = db.table('data_access_multi_group_t').where()
        .eq('tid', groupId).eq('tenant_id', tenantId).selectOne()
    if (existing != null) { return compareSaved(existing) }
    db.table('data_access_multi_group_t').primary('tid').insert({
        tid: groupId, tenant_id: tenantId, group_name: groupName,
        created_by: actor, created_time: new Date(), updated_time: new Date(), is_del: 0
    })
    var order = 1
    for (row in rows) {
        db.table('data_access_multi_group_item_t').primary('tid').insert({
            tid: UUID.randomUUID().toString().replace('-', ''), tenant_id: tenantId,
            group_id: groupId, source_table_id: row.sourceId,
            access_task_id: row.taskId, sort_no: order,
            created_time: new Date(), updated_time: new Date()
        })
        order++
    }
    return {groupId: groupId, created: true, tableCount: rows.size()}
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
