{
  "properties": {},
  "id": "f058946e714846049e38be378047d1c6",
  "script": null,
  "groupId": "5f6726d5f8f44059a0151b1f35f42320",
  "name": "03.保存流程定义",
  "createTime": 1782955344677,
  "updateTime": 1786191950313,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"flowName\": \"test\",\n    \"flowCode\": \"test\",\n    \"modelValue\": \"CLASSICS\",\n    \"category\": \"测试\",\n    \"listenerType\": \"app\",\n    \"listenerPath\": \"测试\",\n    \"id\": \"2036256483356024834\",\n    \"version\": \"测试\",\n    \"isPublish\": true,\n    \"formCustom\": \"测试\",\n    \"formPath\": \"测试\",\n    \"activityStatus\": 2,\n    \"tenantId\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "在当前租户新增或修改流程定义。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import tableWrite
import tenantRuntime

// 在当前租户新增或修改流程定义。
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
var user = StpUtil.getLoginId() + ''
var id = (body?.id || '') + ''
if (id != '' && !id.matches('[1-9][0-9]{0,18}')) { exit -1, '流程定义ID无效' }
var create = id == ''
if (create) { id = IdUtil.getSnowflakeNextIdStr() }
var numericId = id.asLong()
if (numericId == null || numericId <= 0) { exit -1, '流程定义ID无效' }
var fieldNames = {
    flowCode: 'flow_code', flowName: 'flow_name', modelValue: 'model_value',
    category: 'category', version: 'version', isPublish: 'is_publish',
    formCustom: 'form_custom', formPath: 'form_path', activityStatus: 'activity_status',
    listenerType: 'listener_type', listenerPath: 'listener_path'
}
var now = new Date()
var values = {update_by: user, update_time: now}
for (field in fieldNames.keySet()) {
    if (body?.get(field) != null) { values[fieldNames[field]] = body[field] }
}
if (values.containsKey('flow_code') && !(values.flow_code + '').matches('[A-Za-z][A-Za-z0-9_-]{0,39}')) {
    exit -1, '流程编码无效'
}
if (values.containsKey('flow_name') && ((values.flow_name + '').trim() == '' || (values.flow_name + '').length() > 100)) {
    exit -1, '流程名称无效'
}
if (create) {
    if (!values.flow_code || !values.flow_name) { exit -1, '流程编码和名称不能为空' }
    // 新流程初始化为可用草稿，流程图仍由 Warm-Flow 设计器维护。
    values.putAll({id: numericId, tenant_id: tenant, del_flag: '0', create_time: now, create_by: user,
                   model_value: values.model_value || 'CLASSICS', version: values.version || '1',
                   is_publish: values.is_publish == null ? 0 : values.is_publish,
                   activity_status: values.activity_status == null ? 1 : values.activity_status})
    db.table('flow_definition').insert(values)
    return {id: id}
}
return db.transaction(() => {
    // 锁定当前租户的有效定义，更新时保留原归属及创建信息。
    var definition = tableWrite.lockRow('flow_definition', tenant, 'id', numericId, ['id'], {del_flag: '0'})
    if (definition == null) { exit -1, '不存在此流程定义' }
    db.table('flow_definition').where().eq('tenant_id', tenant).eq('id', numericId).eq('del_flag', '0').update(values)
    return {id: id}
})
