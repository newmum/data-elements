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
  "description" : "分页查询当前会话租户及角色数据范围内可选的真实单表接入任务，供创建多表编组；最多每页100条，不新建任务或NiFi流程。",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import dataScope
import tableRead
import tableSort
import cn.hutool.core.util.NumberUtil
import '@/common/multi-task-visible-query' as multiTaskVisibleQuery

// POST /ods/task/multi/candidates 查询多表编组可选的单表接入任务。
// 接收分页、关键词和排序参数，返回可见任务及其来源表、数据源名称和总数。
// 租户和角色范围取自登录会话，仅查询有效任务及其有效来源，不产生写入。

var text = value => value == null ? '' : ('' + value).trim()
var tenantId = text(tenantRuntime.id())
if (tenantId == '') {
    exit 401, '当前租户无效'
}
var pageNum = text(body?.pageNum).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageNum)) : 1
var pageSize = text(body?.pageSize).matches('^[0-9]+$') ? NumberUtil.parseInt(text(body.pageSize)) : 10
if (pageNum < 1 || pageNum > 100000) {
    pageNum = 1
}
if (pageSize < 1 || pageSize > 100) {
    pageSize = 10
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

var sortField = tableSort.field(body, {taskName: 'taskName', sourceTableName: 'sourceTableName'}, 'taskName')
var sortDirection = tableSort.direction(body)
var query = multiTaskVisibleQuery(tenantId, scopeMode, scopeUserId, scopeOrgIds)
var projection = {
    taskId: tableRead.column('task', 'tid', 'TEXT'),
    taskName: tableRead.column('task', 'task_name', 'TEXT'),
    taskStatus: tableRead.column('task', 'task_status', 'NUMBER'),
    pipelineId: tableRead.column('task', 'pipeline_id', 'TEXT'),
    sourceTableId: tableRead.column('source_table', 'tid', 'TEXT'),
    sourceTableName: tableRead.column('source_table', 'table_name', 'TEXT'),
    sourceTableNameCn: tableRead.column('source_table', 'table_name_cn', 'TEXT'),
    datasourceName: tableRead.column('source_db', 'db_name', 'TEXT')
}

// 保留原有关键词通配符语义，筛选和总数共用同一查询条件。
if (keyword != '') {
    var pattern = '%' + keyword + '%'
    query.where(tableRead.any([
        tableRead.like(projection.taskName, pattern),
        tableRead.like(projection.sourceTableName, pattern),
        tableRead.like(projection.sourceTableNameCn, pattern)
    ]))
}

// 来源表按中文名称排序，中文名称为空值时使用物理表名；同值按任务 ID 升序。
var order = sortField == 'sourceTableName'
    ? tableRead.coalesce([projection.sourceTableNameCn, projection.sourceTableName])
    : projection.taskName
var page = query.page(projection, order, sortDirection, 'tid', pageNum, pageSize)

// 按接口投影组装响应，避免返回分页方言添加的内部行号。
var items = page.rows.map(row => {
    var item = {}
    for (key in projection.keySet()) {
        item[key] = row[key]
    }
    return item
})
return {list: items, total: page.total, pageNum: pageNum, pageSize: pageSize}
