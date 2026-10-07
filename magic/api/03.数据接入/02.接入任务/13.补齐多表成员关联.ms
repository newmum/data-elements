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
// 接口：重试后补齐多表编组成员关联
// 路由：POST /ods/task/multi/relink
// 当前来源表重新执行幂等单表确保接口后，只允许关联同租户同来源表的真实单表任务。
import tenantRuntime
import dataScope
import cn.dev33.satoken.stp.StpUtil

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
var visibleSql = scopeMode == 'ALL' ? '1=1' :
    (scopeMode == 'OWNER' ? 'd.created_by=#{scopeUserId}' :
     (scopeOrgIds.size() == 0 ? '1=0' :
      'd.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>'))
return db.transaction(() => {
    // 与编组分页相同：任一成员来源失效或失权，整组都不得再被修改。
    var group = db.selectOne("""
        select g.tid as groupId
          from data_access_multi_group_t g
         where g.tid=#{groupId} and g.tenant_id=#{tenantId}
           and g.is_del=0 and g.created_by=#{actor}
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
    if (group == null) { exit 403, '编组不存在或当前无权修复' }
    var item = db.table('data_access_multi_group_item_t').where()
        .eq('group_id', groupId).eq('source_table_id', sourceTableId)
        .eq('tenant_id', tenantId).selectOne()
    var task = db.table('data_access_agg_task_t').where()
        .eq('tid', taskId).eq('source_table_id', sourceTableId)
        .eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    var source = db.table('db_table_t').where()
        .eq('tid', sourceTableId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
    if (item == null || task == null || source == null) {
        exit 400, '编组成员或同来源表的单表任务不存在'
    }
    if (text(item.accessTaskId || item.access_task_id) != taskId) {
        db.table('data_access_multi_group_item_t').where()
            .eq('tid', item.tid).eq('tenant_id', tenantId).eq('group_id', groupId)
            .update({access_task_id: taskId, updated_time: new Date()})
    }
    return {groupId: groupId, sourceTableId: sourceTableId, taskId: taskId, linked: true}
})
