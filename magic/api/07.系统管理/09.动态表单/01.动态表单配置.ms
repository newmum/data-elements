{
  "properties": {},
  "id": "b5dd6283a2af4141ac6e53d31ed8f507",
  "script": null,
  "groupId": "76876a4ac97856e093efe5a17539285e",
  "name": "01.动态表单配置",
  "createTime": null,
  "updateTime": 1786498770383,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/config",
  "method": "POST",
  "parameters": [
    {
      "name": "action",
      "value": null,
      "description": null,
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "get",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\"formName\":\"登记库\",\"env\":\"公安\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前认证租户读取、保存或逻辑删除动态表单。查询按环境、名称及标识筛选，新增校验同环境重名，保存返回表单标识。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.hutool.core.util.IdUtil
import java.lang.IllegalArgumentException
import tenantRuntime

// POST /sym/form/config 根据 action 读取、保存或逻辑删除当前租户的表单定义。
// 查询返回按创建时间排序的配置，保存返回表单标识，删除返回是否更新了有效记录。
if (!['get', 'save', 'delete'].contains(action)) {
    exit -1, '操作类型不合法，仅支持save/delete/get'
}

var input = body == null ? {} : body
var text = value => value == null ? '' : value.toString().trim()
var tid = text(input.tid)
var formName = text(input.formName)
var env = text(input.env)
var tenantId = tenantRuntime.id()

// 数据源由会话路由确定，查询条件继续校验行的租户归属及删除状态。
var activeForms = () => db.camel().table('sym_form')
    .columns('tid', 'form_name', 'form_json', 'form_config', 'env',
        'created_time', 'updated_time', 'is_del', 'tenant_id')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)

if (action == 'get') {
    var forms = activeForms()
    if (!tid.isBlank()) {
        forms.eq('tid', tid)
    }
    if (!formName.isBlank()) {
        forms.eq('form_name', formName)
    }
    if (!env.isBlank()) {
        forms.eq('env', env)
    }
    return forms.orderBy('created_time').select()
}

if (action == 'delete') {
    if (tid.isBlank()) {
        throw new IllegalArgumentException('表单ID不能为空')
    }
    return activeForms().eq('tid', tid).update({is_del: 1, updated_time: new Date()}) > 0
}

var formJson = text(input.formJson)
if (formName.isBlank()) {
    throw new IllegalArgumentException('表单名称不能为空')
}
if (formJson.isBlank()) {
    throw new IllegalArgumentException('表单配置不能为空')
}

var now = new Date()
var values = {
    form_name: formName,
    form_json: formJson,
    form_config: input.formConfig,
    env: env,
    updated_time: now
}
if (!tid.isBlank()) {
    // 保留原来的整份配置更新语义，未提供的辅助配置写入空值。
    if (activeForms().eq('tid', tid).update(values, true) == 0) {
        throw new IllegalArgumentException('表单不存在或已删除')
    }
    return tid
}

return db.transaction(() => {
    // 保留当前租户、同一环境下有效表单的重名检查。
    if (activeForms().eq('form_name', formName).eq('env', env).count() > 0) {
        throw new IllegalArgumentException(env + '运行环境下已存在同名表单')
    }
    var newId = IdUtil.getSnowflakeNextIdStr()
    values.tid = newId
    values.tenant_id = tenantId
    values.is_del = 0
    values.created_time = now
    db.table('sym_form').primary('tid').insert(values)
    return newId
})
