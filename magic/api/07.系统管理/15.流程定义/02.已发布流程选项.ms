{
  "properties": {},
  "id": "6c3ab97a62ce4aec8b9fb91d17703726",
  "script": null,
  "groupId": "5f6726d5f8f44059a0151b1f35f42320",
  "name": "02.已发布流程选项",
  "createTime": 1782955344534,
  "updateTime": 1784446270714,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/options",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取当前租户可选的已发布流程定义。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tableRead
import tenantRuntime

// 读取当前租户可选的已发布流程定义。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
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
    listenerType: column('listener_type', 'TEXT'),
    listenerPath: column('listener_path', 'TEXT'),
    ext: column('ext', 'TEXT'),
    createBy: column('create_by', 'TEXT'),
    updateBy: column('update_by', 'TEXT'),
    createTime: column('create_time', 'TIME'),
    updateTime: column('update_time', 'TIME'),
    delFlag: column('del_flag', 'TEXT'),
    tenantId: column('tenant_id', 'TEXT')
}
definitions.where(tableRead.equal(fields.isPublish, 1))
definitions.where(tableRead.equal(fields.delFlag, '0'))
var rows = definitions.rows(fields, [fields.id], ['asc'], 1001)
if (rows.size() > 1000) { exit 413, '已发布流程选项超过1000项' }
rows.each(row -> row.id = row.id + '')
return rows
