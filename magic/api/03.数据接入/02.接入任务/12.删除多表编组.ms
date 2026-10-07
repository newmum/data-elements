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
// 接口：删除多表任务编组（POST /ods/task/multi/delete）
// 只软删除编组；全部成员来源数据源仍须落在当前角色范围，不触碰单表任务或 NiFi。
import tenantRuntime
import dataScope
import cn.dev33.satoken.stp.StpUtil

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
var visibleSql = scopeMode == 'ALL' ? '1=1' :
    (scopeMode == 'OWNER' ? 'd.created_by=#{scopeUserId}' :
     (scopeOrgIds.size() == 0 ? '1=0' :
      'd.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>'))

return db.transaction(() => {
    // 一次关联反查验证整组所有来源，不在循环内逐成员 SELECT。
    var group = db.selectOne("""
        select g.tid as groupId, g.is_del as deleted
          from data_access_multi_group_t g
         where g.tid=#{groupId} and g.tenant_id=#{tenantId}
           and g.created_by=#{actor}
           and not exists (
               select 1 from data_access_multi_group_item_t m
               left join db_table_t t on t.tid=m.source_table_id
                    and t.tenant_id=g.tenant_id and t.is_del=0
               left join db_datasource_t d on d.tid=t.datasource_id
                    and d.tenant_id=g.tenant_id and d.is_del=0
               where m.group_id=g.tid and m.tenant_id=g.tenant_id
                 and (t.tid is null or d.tid is null or not (${visibleSql}))
           )
    """)
    if (group == null) { exit 403, '编组不存在或当前无权删除' }
    var deleted = group.deleted == null ? group.DELETED : group.deleted
    if (deleted == 0) {
        db.table('data_access_multi_group_t').where()
            .eq('tid', groupId).eq('tenant_id', tenantId).eq('is_del', 0)
            .update({is_del: 1, updated_time: new Date()})
    }
    return {groupId: groupId, deleted: true, memberTasksPreserved: true}
})
