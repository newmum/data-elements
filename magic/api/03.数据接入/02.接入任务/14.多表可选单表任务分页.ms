{
  "properties" : { },
  "id" : "c8754c53bb644104a178b88fbcebb7cd",
  "script" : null,
  "groupId" : "99c8dd7189af40939e5110b76bcd7160",
  "name" : "14.多表可选单表任务分页",
  "createTime" : 1791118144463,
  "updateTime" : 1791118144089,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/multi/candidates",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\"}",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "{\"list\":[],\"total\":0}",
  "description" : "分页查询当前会话租户及角色数据范围内可选的真实单表接入任务，供创建多表编组；最多每页50条，不新建任务或NiFi流程。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 接口：多表编组可选单表任务分页（POST /ods/task/multi/candidates）
// 仅展示当前租户、当前角色数据范围内已经存在且有来源表的真实单表任务。
import tenantRuntime
import dataScope
import cn.hutool.core.util.NumberUtil

var text = value => value == null ? '' : ('' + value).trim()
var field = (row, name) => row == null ? null :
    (row[name] != null ? row[name] :
     (row[name.toLowerCase()] != null ? row[name.toLowerCase()] : row[name.toUpperCase()]))
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
var pageNum = text(body?.pageNum).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageNum)) : 1
var pageSize = text(body?.pageSize).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageSize)) : 10
if (pageNum < 1 || pageNum > 100000) { pageNum = 1 }
if (pageSize < 1 || pageSize > 50) { pageSize = 10 }
var keyword = text(body?.keyword)
if (keyword.length() > 100) { exit 400, '搜索词过长' }
var likeKeyword = '%' + keyword + '%'

var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') { exit 403, '无法确定当前数据范围' }
var visibleSql = scopeMode == 'ALL' ? '1=1' :
    (scopeMode == 'OWNER' ? 'd.created_by=#{scopeUserId}' :
     (scopeOrgIds.size() == 0 ? '1=0' :
      'd.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>'))

var result = db.page("""
    select a.tid as taskId, a.task_name as taskName, a.task_status as taskStatus,
           a.pipeline_id as pipelineId, t.tid as sourceTableId,
           t.table_name as sourceTableName, t.table_name_cn as sourceTableNameCn,
           d.db_name as datasourceName
      from data_access_agg_task_t a
      join db_table_t t on t.tid=a.source_table_id
           and t.tenant_id=#{tenantId} and t.is_del=0
      join db_datasource_t d on d.tid=t.datasource_id
           and d.tenant_id=#{tenantId} and d.is_del=0
     where a.tenant_id=#{tenantId} and a.is_del=0
       and (#{keyword}='' or a.task_name like #{likeKeyword}
            or t.table_name like #{likeKeyword} or t.table_name_cn like #{likeKeyword})
       and ${visibleSql}
     order by a.updated_time desc, a.tid desc
""", pageSize, (pageNum - 1) * pageSize)
var list = []
for (row in (result?.list == null ? [] : result.list)) {
    list.add({taskId: field(row, 'taskId'), taskName: field(row, 'taskName'),
        taskStatus: field(row, 'taskStatus'), pipelineId: field(row, 'pipelineId'),
        sourceTableId: field(row, 'sourceTableId'),
        sourceTableName: field(row, 'sourceTableName'),
        sourceTableNameCn: field(row, 'sourceTableNameCn'),
        datasourceName: field(row, 'datasourceName')})
}
return {list: list,
    total: result?.total == null ? 0 : result.total,
    pageNum: pageNum, pageSize: pageSize}
