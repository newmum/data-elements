{
  "properties" : { },
  "id" : "9dc102c8fc8845d2a48c161b442385b8",
  "script" : null,
  "groupId" : "99c8dd7189af40939e5110b76bcd7160",
  "name" : "13.补齐多表成员关联",
  "createTime" : 1791112840450,
  "updateTime" : 1791118144374,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/multi/relink",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"groupId\":\"32位UUID\",\"sourceTableId\":\"来源表ID\",\"taskId\":\"任务ID\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"success\":true}",
  "description" : "重试单表任务确保后，验证编组、来源表和新任务同属当前租户且匹配，幂等更新成员关联；不触及其他单表任务。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import dataScope
import tableRead
import '@/common/multi-group-edit-query' as multiGroupEditQuery
import cn.dev33.satoken.stp.StpUtil

/**
 * 补齐多表编组成员关联（POST /ods/task/multi/relink）。
 * 接收编组、来源表和单表任务 ID，供重试单表任务后关联新任务。
 * 事务内先验证编组创建人和全组来源范围，再确认成员与新任务使用同一有效来源。
 * 只更新指定成员的关联，重复关联同一任务时返回成功且不写入。
 */
var tenantId = tenantRuntime.id()
if (!StpUtil.isLogin()) { exit 401, '登录已失效' }
var actor = '' + StpUtil.getLoginId()
var text = (value) => value == null ? '' : ('' + value).trim()
var groupId = text(body?.groupId)
var sourceTableId = text(body?.sourceTableId)
var taskId = text(body?.taskId)
if (!groupId.matches('^[a-fA-F0-9]{32}$') || sourceTableId == '' || taskId == '') {
    exit 400, '编组、来源表和单表任务标识不能为空'
}
var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId != actor) { exit 403, '无法确定当前数据范围' }

return db.transaction(() => {
    // 先验证整组权限和有效状态，通过后才读取待补齐的成员。
    var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
    var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
    var groupQuery = multiGroupEditQuery(tenantId, groupId, actor, scopeMode, scopeOrgIds)
    var groupIdColumn = textColumn('task_group', 'tid')
    groupQuery.where(active('task_group'))
    var groups = groupQuery.rows({group_id: groupIdColumn}, groupIdColumn, 'asc', 1)
    if (groups.size() == 0) { exit 403, '编组不存在或当前无权修复' }

    // 旧任务可缺失，仍以成员保存的来源关联当前有效单表任务。
    var itemIdColumn = textColumn('group_item', 'tid')
    var itemSourceColumn = textColumn('group_item', 'source_table_id')
    var sourceIdColumn = textColumn('source_table', 'tid')
    var replacementIdColumn = textColumn('replacement_task', 'tid')
    var memberQuery = tableRead.from('data_access_multi_group_item_t', 'group_item', tenantId)
    memberQuery.leftJoin('db_table_t', 'source_table', 'tid', 'source_table_id', [active('source_table')])
    memberQuery.leftJoin('data_access_agg_task_t', 'replacement_task', 'source_table_id', 'source_table_id', [
        active('replacement_task'), tableRead.equal(replacementIdColumn, taskId)
    ])
    memberQuery.where(tableRead.all([
        tableRead.equal(textColumn('group_item', 'group_id'), groupId),
        tableRead.equal(itemSourceColumn, sourceTableId),
        tableRead.not(tableRead.isNull(sourceIdColumn)),
        tableRead.not(tableRead.isNull(replacementIdColumn))
    ]))
    var members = memberQuery.rows({
        tid: itemIdColumn, access_task_id: textColumn('group_item', 'access_task_id')
    }, itemIdColumn, 'asc', 1)
    if (members.size() == 0) { exit 400, '编组成员或同来源表的单表任务不存在' }

    var item = members[0]
    if (text(item.access_task_id) != taskId) {
        db.table('data_access_multi_group_item_t').where()
            .eq('tid', item.tid).eq('tenant_id', tenantId).eq('group_id', groupId)
            .update({access_task_id: taskId, updated_time: new Date()})
    }
    return {groupId: groupId, sourceTableId: sourceTableId, taskId: taskId, linked: true}
})
