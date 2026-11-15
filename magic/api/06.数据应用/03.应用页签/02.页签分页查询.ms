{
  "properties": {},
  "id": "124c8c77129242f786e0903e5172a90b",
  "script": null,
  "groupId": "cc977d046a125088bbe411c296b12efc",
  "name": "02.页签分页查询",
  "createTime": 1782955345276,
  "updateTime": 1784437771880,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"pid\": \"测试\",\n    \"tabType\": \"app\",\n    \"keyword\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 0,\n        \"list\": null\n    }\n}",
  "description": "按所属应用、页签类型和关键词查询应用仓库页签，返回分页资料与总数。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import uiComponentAdmin
import tenantRuntime

// POST /das/tab/page 页签分页查询。按所属应用、页签类型和关键词查询应用仓库页签，返回分页资料与总数。
var tenantId = tenantRuntime.id()

// 仅在认证行业租户内查找应用页签，并在同一页查询中组装大屏和表单的展示名。
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.max(1, Math.min(100, body?.pageSize::int(20)))
var tabs = tableRead.from('app_workshop_repository_tab', 'tab', tenantId)
var field = (name, kind) => tableRead.column('tab', name, kind)
var text = name => field(name, 'TEXT')
var number = name => field(name, 'NUMBER')
var time = name => field(name, 'TIME')
var type = number('tab_type')
tabs.where(tableRead.equal(number('is_del'), 0))

if (body?.pid != null && body.pid != '') {
    tabs.where(tableRead.equal(text('pid'), body.pid))
}
if (body?.tabType != null && body.tabType != '') {
    tabs.where(tableRead.equal(type, body.tabType::int))
}
if (body?.keyword != null && body.keyword != '') {
    var pattern = '%' + body.keyword + '%'
    tabs.where(tableRead.any([tableRead.like(text('name'), pattern), tableRead.like(text('name_cn'), pattern)]))
}

tabs.leftJoin('app_workshop_big_screen', 'screen', 'tid', 'rela_id', [
    tableRead.equal(tableRead.column('screen', 'is_del', 'NUMBER'), 0),
    tableRead.equal(type, 3)
])
tabs.leftJoin('sym_form', 'form', 'tid', 'rela_id', [
    tableRead.equal(tableRead.column('form', 'is_del', 'NUMBER'), 0),
    tableRead.equal(type, 2)
])
var formName = tableRead.column('form', 'form_name', 'TEXT')
var result = tabs.page({
    tid: text('tid'),
    pid: text('pid'),
    tabType: type,
    relaId: text('rela_id'),
    publishId: text('publish_id'),
    thumbnail: field('thumbnail', 'LOB'),
    seqNo: number('seq_no'),
    createdBy: text('created_by'),
    createdTime: time('created_time'),
    updatedBy: text('updated_by'),
    updatedTime: time('updated_time'),
    name: tableRead.coalesce([tableRead.column('screen', 'name', 'TEXT'), formName]),
    nameCn: tableRead.coalesce([tableRead.column('screen', 'name_cn', 'TEXT'), formName])
}, number('seq_no'), 'asc', 'created_time', 'desc', pageNum, pageSize, true)

// 专题和报表的组件保存在控制库，仅对当前页引用一次批量读取展示信息。
uiComponentAdmin.enrichRepositoryTabs(result.rows)
return {total: result.total, list: result.rows}
