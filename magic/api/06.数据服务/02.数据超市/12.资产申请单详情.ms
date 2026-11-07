{
  "properties": {},
  "id": "db35431a36b34bc88b3b48914d7ba0ff",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "12.资产申请单详情",
  "createTime": 1782955344166,
  "updateTime": 1786191947649,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyForm/queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"b71ef91ddcc940a99b220cc3d7cd9120\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"0d50a16cf9b5458398a22f6fa158ea57\"\n}",
  "description": "从当前租户读取本人申请单、表单扩展字段和有效目录；使用请求参数 tid。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.资产申请单详情
import approvalRuntime
import tableRead
import tenantRuntime
// 资产申请单详情：读取本人申请的关系库记录，按原选择顺序组装目录。

var tenantId = tenantRuntime.id()
var userId = approvalRuntime.principal().userId
if (tenantId == null || userId == null) { exit 401, '请先登录' }

var tid = body?.tid == null ? '' : (body.tid + '').trim()
if (tid == '' || tid.length() > 32) { exit 400, '申请单标识无效' }

var textColumn = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numberColumn = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var forms = tableRead.from('data_apply_form_t', 'a', tenantId)
forms.where(tableRead.equal(textColumn('a', 'tid'), tid))
forms.where(tableRead.equal(tableRead.coalesce([numberColumn('a', 'is_del'), 0]), 0))
forms.where(tableRead.equal(tableRead.coalesce([
    tableRead.blankAsNull(textColumn('a', 'apply_user_id')), textColumn('a', 'created_by')
]), userId))
var formRows = forms.rows({
    tid: textColumn('a', 'tid'),
    tenantId: textColumn('a', 'tenant_id'),
    applyName: textColumn('a', 'apply_name'),
    applyUserId: textColumn('a', 'apply_user_id'),
    applyUserName: textColumn('a', 'apply_user_name'),
    applyOrgId: textColumn('a', 'apply_org_id'),
    catalogIds: textColumn('a', 'catalog_ids'),
    type: textColumn('a', 'type'),
    flowStatus: numberColumn('a', 'flow_status'),
    createdTime: tableRead.column('a', 'created_time', 'TIME'),
    updatedTime: tableRead.column('a', 'updated_time', 'TIME')
}, textColumn('a', 'tid'), 'asc', 1)
if (formRows.size() == 0) { exit 404, '申请单不存在或无权查看' }
var form = formRows[0]

var catalogIds = []
for (catalogId in (form.catalogIds || '').split(',')) {
    var id = catalogId.trim()
    if (id != '' && !catalogIds.contains(id)) { catalogIds.add(id) }
}
if (catalogIds.size() > 100) { exit 409, '历史申请关联的目录超过可读取范围，请联系管理员处理' }

var applyProps = tableRead.from('da_prop_t', 'p', tenantId)
applyProps.where(tableRead.equal(textColumn('p', 'parent_id'), tid))
applyProps.where(tableRead.equal(textColumn('p', 'data_type'), 'apply_form'))
applyProps.where(tableRead.equal(numberColumn('p', 'is_del'), 0))
var fields = {
    parentId: textColumn('p', 'parent_id'),
    name: textColumn('p', 'prop_name'),
    value: tableRead.column('p', 'prop_value', 'LOB')
}
var properties = applyProps.rows(fields, textColumn('p', 'tid'), 'desc', 257)
if (properties.size() > 256) { exit 409, '历史申请的表单字段超过可读取范围，请联系管理员处理' }

// 扩展字段保留历史表单字段名称，业务身份和资源引用以主记录为准。
for (prop in properties) {
    var name = prop.name
    if (name != null && name.matches('[A-Za-z][A-Za-z0-9_]{0,63}') &&
        (!form.containsKey(name) || (name == 'applyName' && !form.applyName))) {
        form[name] = prop.value
    }
}

var resources = []
if (catalogIds.size() > 0) {
    var catalogs = tableRead.from('da_catalog_t', 'c', tenantId)
    catalogs.where(tableRead.equal(numberColumn('c', 'is_del'), 0))
    catalogs.where(tableRead.in(textColumn('c', 'tid'), catalogIds))
    var catalogRows = catalogs.rows({
        tid: textColumn('c', 'tid'),
        catalogName: textColumn('c', 'catalog_name'),
        catalogNameEn: textColumn('c', 'catalog_name_en'),
        orgId: textColumn('c', 'org_id'),
        appId: textColumn('c', 'app_id'),
        appName: textColumn('c', 'app_name'),
        targetTableId: textColumn('c', 'target_table_id')
    }, textColumn('c', 'tid'), 'asc', 100)
    if (catalogRows.size() != catalogIds.size()) {
        exit 409, '申请关联的数据目录已删除或不可访问，请重新选择可用目录后发起申请'
    }
    var byId = {}
    for (catalog in catalogRows) { byId[catalog.tid] = catalog }

    var catalogProps = tableRead.from('da_prop_t', 'p', tenantId)
    catalogProps.where(tableRead.equal(numberColumn('p', 'is_del'), 0))
    catalogProps.where(tableRead.equal(textColumn('p', 'data_type'), 'catalog'))
    catalogProps.where(tableRead.in(textColumn('p', 'parent_id'), catalogIds))
    catalogProps.where(tableRead.in(textColumn('p', 'prop_name'), ['apiId', 'fileId', 'dirId', 'shareType', 'updateCycle']))
    var metadata = catalogProps.rows(fields, textColumn('p', 'tid'), 'desc', 1000)
    if (metadata.size() == 1000) { exit 409, '历史申请关联的目录属性超过可读取范围，请联系管理员处理' }
    for (prop in metadata) {
        var catalog = byId[prop.parentId]
        if (catalog != null && !catalog.containsKey(prop.name)) { catalog[prop.name] = prop.value }
    }
    for (catalogId in catalogIds) {
        var catalog = byId[catalogId]
        var resType = []
        if (catalog.targetTableId) { resType.add('table') }
        if (catalog.apiId) { resType.add('api') }
        if (catalog.fileId) { resType.add('file') }
        if (catalog.dirId) { resType.add('dir') }
        catalog.assetType = 'catalog'
        catalog.resType = resType
        resources.add(catalog)
    }
}
form.applyResource = resources
return form
