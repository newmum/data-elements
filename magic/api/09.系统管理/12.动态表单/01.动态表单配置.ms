{
  "properties": {},
  "id": "b5dd6283a2af4141ac6e53d31ed8f507",
  "script": null,
  "groupId": "49db77c12b24426ebf245e20ecb5b28e",
  "name": "01.动态表单配置",
  "createTime": null,
  "updateTime": 1786498770383,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/form",
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
  "description": "动态表单配置（POST /sym/form）。表单定义按当前登录租户从对应业务库读取和保存。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.动态表单配置
// 路由：POST /sym/form
// 业务说明：动态表单配置（POST /sym/form）。表单定义按当前登录租户从对应业务库读取和保存。
// 动态表单配置：处理 POST /form 的核心写入或状态变更逻辑。
import tenantForm

var validActions = ['save', 'delete', 'get']
if (!validActions.contains(action)) {
    exit -1, '操作类型不合法，仅支持save/delete/get'
}

var request = body == null ? {} : body
if (action == 'get') {
    return tenantForm.list(request.formName, request.env, request.tid)
}
if (action == 'save') {
    return tenantForm.save(request)
}
return tenantForm.delete(request.tid)
