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
// 接口：多表任务编组分页（POST /ods/task/multi/page）
// 服务端分页后一次关联读取本页全部成员，按来源数据源角色范围过滤整组。
import tenantRuntime
import dataScope
import cn.hutool.core.util.NumberUtil

var text = value => value == null ? '' : ('' + value).trim()
// 不同 JDBC 驱动可能把未加引号的别名保留原样、转小写或转大写。
var field = (row, name) => row == null ? null :
    (row[name] != null ? row[name] :
     (row[name.toLowerCase()] != null ? row[name.toLowerCase()] : row[name.toUpperCase()]))
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '当前租户无效' }
var pageNum = text(body?.pageNum).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageNum)) : 1
var pageSize = text(body?.pageSize).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageSize)) : 20
if (pageNum < 1 || pageNum > 100000) { pageNum = 1 }
if (pageSize < 1 || pageSize > 20) { pageSize = 20 }
var keyword = text(body?.keyword)
if (keyword.length() > 100) { exit 400, '搜索词过长' }
var likeKeyword = '%' + keyword + '%'

var scope = dataScope.dataSource()
var scopeMode = text(scope?.scope)
var scopeUserId = text(scope?.userId)
var scopeOrgIds = scope?.organizationIds == null ? [] : scope.organizationIds
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == '') { exit 403, '无法确定当前数据范围' }
// SQL 片段只由服务端角色枚举决定；身份、部门和租户值均使用参数绑定。
var visibleSql = scopeMode == 'ALL' ? '1=1' :
    (scopeMode == 'OWNER' ? 'd.created_by=#{scopeUserId}' :
     (scopeOrgIds.size() == 0 ? '1=0' :
      'd.org_id in <foreach collection="scopeOrgIds" item="orgId" open="(" close=")" separator=",">#{orgId}</foreach>'))

// 一组任一来源超出范围时隐藏整组，不返回部分成员造成信息泄露。
var groupSql = """
    select g.tid as groupId, g.group_name as groupName, g.created_by as createdBy,
           g.created_time as createdTime, g.updated_time as updatedTime
      from data_access_multi_group_t g
     where g.tenant_id=#{tenantId} and g.is_del=0
       and (#{keyword}='' or g.group_name like #{likeKeyword})
       and not exists (
           select 1 from data_access_multi_group_item_t m
           left join db_table_t t on t.tid=m.source_table_id
                and t.tenant_id=g.tenant_id and t.is_del=0
           left join db_datasource_t d on d.tid=t.datasource_id
                and d.tenant_id=g.tenant_id and d.is_del=0
           where m.group_id=g.tid and m.tenant_id=g.tenant_id
             and (t.tid is null or d.tid is null or not (${visibleSql}))
       )
     order by g.created_time desc, g.tid desc
"""
var groupPage = db.page(groupSql, pageSize, (pageNum - 1) * pageSize)
var groupRows = groupPage?.list == null ? [] : groupPage.list
var groupIds = []
var itemMap = {}
for (group in groupRows) {
    var id = text(field(group, 'groupId'))
    groupIds.add(id)
    itemMap[id] = []
}
if (groupIds.size() > 0) {
    var members = db.select("""
        select m.tid as itemId, m.group_id as groupId, m.source_table_id as sourceTableId,
               m.access_task_id as taskId, m.sort_no as sortNo,
               t.table_name as sourceTableName, t.table_name_cn as sourceTableNameCn,
               a.tid as availableTaskId, a.task_name as taskName, a.task_status as taskStatus,
               a.pipeline_id as pipelineId, a.monitor_status as monitorStatus,
               a.monitor_msg as monitorMessage, a.last_running as lastRunning,
               a.end_running as endRunning
          from data_access_multi_group_item_t m
          join db_table_t t on t.tid=m.source_table_id
               and t.tenant_id=#{tenantId} and t.is_del=0
          join db_datasource_t d on d.tid=t.datasource_id
               and d.tenant_id=#{tenantId} and d.is_del=0
          left join data_access_agg_task_t a on a.tid=m.access_task_id
               and a.source_table_id=m.source_table_id
               and a.tenant_id=#{tenantId} and a.is_del=0
         where m.tenant_id=#{tenantId}
           and m.group_id in <foreach collection="groupIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
           and ${visibleSql}
         order by m.group_id, m.sort_no, m.tid
    """)
    for (member in members) {
        var id = text(field(member, 'groupId'))
        if (itemMap[id] != null) {
            itemMap[id].add({
                itemId: field(member, 'itemId'),
                sourceTableId: field(member, 'sourceTableId'),
                taskId: field(member, 'taskId'),
                sortNo: field(member, 'sortNo'),
                taskAvailable: text(field(member, 'availableTaskId')) != '',
                taskName: field(member, 'taskName'),
                taskStatus: field(member, 'taskStatus'),
                pipelineId: field(member, 'pipelineId'),
                monitorStatus: field(member, 'monitorStatus'),
                monitorMessage: field(member, 'monitorMessage'),
                lastRunning: field(member, 'lastRunning'),
                endRunning: field(member, 'endRunning'),
                sourceTableName: field(member, 'sourceTableName'),
                sourceTableNameCn: field(member, 'sourceTableNameCn')
            })
        }
    }
}
var groups = []
for (group in groupRows) {
    var id = text(field(group, 'groupId'))
    var creator = text(field(group, 'createdBy'))
    groups.add({groupId: id, groupName: field(group, 'groupName'),
        createdBy: creator, canManage: creator == scopeUserId,
        createdTime: field(group, 'createdTime'),
        updatedTime: field(group, 'updatedTime'), items: itemMap[id]})
}
return {list: groups, total: groupPage?.total == null ? 0 : groupPage.total,
    pageNum: pageNum, pageSize: pageSize}
