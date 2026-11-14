{
  "properties": {},
  "id": "application_register_options_01",
  "script": null,
  "groupId": "7868bf218e904ebc999a94f041d91c36",
  "name": "09.登记所属系统选择项",
  "createTime": 1789957480597,
  "updateTime": 1789980513797,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/register-options",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"code\": 0,\n  \"msg\": \"success\",\n  \"message\": \"success\",\n  \"success\": true,\n  \"data\": [\n    {\n      \"label\": \"验证应用系统\",\n      \"value\": \"validation-app\",\n      \"orgid\": \"validation-org\",\n      \"createdby\": \"validation-user\"\n    }\n  ],\n  \"timestamp\": 0\n}",
  "description": "返回数据源登记第 1 步“所属系统”的选择项数组，按应用名称和标识升序排列。使用认证租户、角色范围及组织成员关系，ALL 获取当前租户的有效系统，部门管理员和登记人员获取所属组织的有效系统。选择器从完整结果在页面内搜索。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "0",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "success",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "message",
        "value": "success",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "true",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "",
        "description": "",
        "required": false,
        "dataType": "Array",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
            "name": "",
            "value": "",
            "description": "",
            "required": false,
            "dataType": "Object",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": [
              {
                "name": "value",
                "value": "validation-app",
                "description": "",
                "required": false,
                "dataType": "String",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "label",
                "value": "验证应用系统",
                "description": "",
                "required": false,
                "dataType": "String",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "orgid",
                "value": "validation-org",
                "description": "",
                "required": false,
                "dataType": "String",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "createdby",
                "value": "validation-user",
                "description": "",
                "required": false,
                "dataType": "String",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              }
            ]
          }
        ]
      },
      {
        "name": "timestamp",
        "value": "0",
        "description": "",
        "required": false,
        "dataType": "Long",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
import tenantRuntime
import dataScope

// POST /dst/application/register-options 返回数据源登记步骤的所属系统选择项。
// 依据认证租户、当前角色和组织成员关系返回完整数组，字段为 label、value、orgid 和 createdby。
// 页面根据名称或标识从结果中搜索。历史请求中的 keyword、limit 及身份字段不参与查询。
var tenantId = tenantRuntime.id()
var scopeResource = 'MENU:2081000000000000002'

// 历史未填写删除标记的系统仍有效，名称和标识的升序沿用各库原有排序。
var applications = db.table('sym_application_t')
    .columns(['app_name', 'tid', 'org_id', 'created_by'])
    .where()
    .eq('tenant_id', tenantId)
    .and(active => active.eq('is_del', 0).or().isNull('is_del'))
    .orderBy('app_name', 'asc')
    .orderBy('tid', 'asc')
    .select()

// 登记人员与部门管理员均按认证账户的有效组织成员关系取选项。
var visible = dataScope.visibleRegistrationApplications(scopeResource, applications)
return visible.map(row => {
    label: row.appName,
    value: row.tid,
    orgid: row.orgId,
    createdby: row.createdBy
})
