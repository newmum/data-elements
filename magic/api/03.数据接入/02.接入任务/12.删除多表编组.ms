{
  "properties" : { },
  "id" : "59ce9120d637482a8b63cdc643510727",
  "script" : null,
  "groupId" : "99c8dd7189af40939e5110b76bcd7160",
  "name" : "12.删除多表编组",
  "createTime" : 1791112840406,
  "updateTime" : 1791118144267,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/multi/delete",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"groupId\":\"32位UUID\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"success\":true}",
  "description" : "软删除当前租户的多表编组，原单表任务、NiFi画布和已有数据均保留；重复删除幂等。",
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
 * 删除多表任务编组（POST /ods/task/multi/delete）。
 * 接收编组 ID。验证当前租户、创建人和全组成员来源范围后，只软删除编组。
 * 重试仍校验权限，返回编组 ID、删除状态及原单表任务保留标志。
 */
var text = value => value == null ? '' : ('' + value).trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
if (!StpUtil.isLogin()) { exit 401, '登录已失效' }
var actor = text(StpUtil.getLoginId())
var groupId = text(body?.groupId)
if (!groupId.matches('^[a-fA-F0-9]{32}$')) { exit 400, '编组标识无效' }
var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId != actor) { exit 403, '无法确定当前数据范围' }

return db.transaction(() => {
    // 包含已删除的编组，确保重复请求仍按同一来源范围校验。
    var groupQuery = multiGroupEditQuery(tenantId, groupId, actor, scopeMode, scopeOrgIds)
    var groupIdColumn = tableRead.column('task_group', 'tid', 'TEXT')
    var deletedColumn = tableRead.column('task_group', 'is_del', 'NUMBER')
    var groups = groupQuery.rows({deleted: deletedColumn}, groupIdColumn, 'asc', 1)
    if (groups.size() == 0) { exit 403, '编组不存在或当前无权删除' }

    if (groups[0].deleted == 0) {
        // 并发删除只允许活跃记录发生一次状态变化。
        db.table('data_access_multi_group_t').where()
            .eq('tid', groupId).eq('tenant_id', tenantId).eq('is_del', 0)
            .update({is_del: 1, updated_time: new Date()})
    }
    return {groupId: groupId, deleted: true, memberTasksPreserved: true}
})
