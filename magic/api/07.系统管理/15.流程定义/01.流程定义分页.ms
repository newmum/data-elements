{
  "properties": {},
  "id": "169959dd8fb148e68b4cfe4644f63c76",
  "script": null,
  "groupId": "5f6726d5f8f44059a0151b1f35f42320",
  "name": "01.流程定义分页",
  "createTime": null,
  "updateTime": 1786191950796,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\",\"publishStatus\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户和条件分页读取有效流程定义。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tableSort
import tenantRuntime

// 按当前租户和条件分页读取有效流程定义。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.min(100, Math.max(1, body?.pageSize::int(20)))
var definitions = tableRead.from('flow_definition', 'definition', tenant)
var column = (name, kind) => tableRead.column('definition', name, kind)
var fields = {
    id: column('id', 'NUMBER'),
    flowCode: column('flow_code', 'TEXT'),
    flowName: column('flow_name', 'TEXT'),
    modelValue: column('model_value', 'TEXT'),
    category: column('category', 'TEXT'),
    version: column('version', 'TEXT'),
    isPublish: column('is_publish', 'NUMBER'),
    activityStatus: column('activity_status', 'NUMBER'),
    formCustom: column('form_custom', 'TEXT'),
    formPath: column('form_path', 'TEXT'),
    createBy: column('create_by', 'TEXT'),
    updateBy: column('update_by', 'TEXT'),
    createTime: column('create_time', 'TIME'),
    updateTime: column('update_time', 'TIME')
}
definitions.where(tableRead.equal(column('del_flag', 'TEXT'), '0'))
if (body?.keyword != null && body.keyword != '') {
    var pattern = '%' + body.keyword + '%'
    definitions.where(tableRead.any([tableRead.like(fields.flowCode, pattern), tableRead.like(fields.flowName, pattern)]))
}
if (body?.publishStatus != null && (body.publishStatus + '') != '') {
    var publishStatus = body.publishStatus + ''
    if (!publishStatus.matches('[0-9]{1,3}')) { exit 400, '发布状态无效' }
    definitions.where(tableRead.equal(fields.isPublish, publishStatus::int))
}
var sort = tableSort.field(body, {flowName: 'flowName', createTime: 'createTime', updateTime: 'updateTime'}, 'updateTime')
var page = definitions.page(fields, fields[sort], tableSort.direction(body), 'id', pageNum, pageSize)
page.rows.each(row -> row.id = row.id + '')
return {total: page.total, list: page.rows}
