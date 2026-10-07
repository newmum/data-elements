{
  "properties": {},
  "id": "a3b59b1dfb2c4c3989bf0a8a0e1f91cc",
  "script": null,
  "groupId": "f254c72dc6a74266bf2a8887997ec41c",
  "name": "01.页面组件管理",
  "createTime": 1782660715899,
  "updateTime": 1786582574209,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/component",
  "method": "POST",
  "parameters": [
    {
      "name": "action",
      "value": "list",
      "description": "页面组件管理接口。读写 ui_component_t / ui_component_history_t；用于低代码页面列表、组件树、保存源码和递归删除；SQL 使用 MySQL/达梦兼容写法。",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tid",
      "value": "",
      "description": "删除操作用",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "componentId",
      "value": "",
      "description": "真实数据示例参数：componentId",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n    \"tid\": \"2e83a7abf33140c7a3df27d0c79e98c1\",\n    \"type\": \"app\",\n    \"name\": \"测试\",\n    \"pid\": \"测试\",\n    \"sourceCode\": \"测试\",\n    \"compileJs\": \"测试\",\n    \"compileCss\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 400,\n    \"msg\": \"body[type]不合法\",\n    \"message\": \"body[type]不合法\",\n    \"success\": false,\n    \"data\": null,\n    \"timestamp\": 1785741933452,\n    \"error\": {\n        \"code\": \"MAGIC-BUSINESS-400\",\n        \"message\": \"body[type]不合法\",\n        \"detail\": \"body[type]不合法\",\n        \"traceId\": \"959F15F9974C452A\",\n        \"type\": \"BusinessException\",\n        \"method\": \"POST\",\n        \"path\": \"/sym/component\"\n    }\n}",
  "description": "根据 action 查询页面组件列表或树，或调用组件管理服务执行保存与维护操作。",
  "requestBodyDefinition": {
    "name": "root",
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
        "name": "tid",
        "value": "1978382256294871040",
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
        "name": "remark",
        "value": "可疑经济",
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
        "name": "name",
        "value": "demo2",
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
        "name": "type",
        "value": "1",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  },
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
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
            "name": "list",
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
                    "name": "tid",
                    "value": "0b8286331eda4724892ee849be2cac22",
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
                    "name": "realName",
                    "value": "1.文旅行业业务场景",
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
                    "name": "remark",
                    "value": "1.文旅行业业务场景",
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
                    "name": "name",
                    "value": "1.文旅行业业务场景",
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
                    "name": "pid",
                    "value": "0",
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
                    "name": "isGroup",
                    "value": "1",
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
                    "name": "children",
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
                            "name": "tid",
                            "value": "6edc669a182546b99b42bba4a6a69a1d",
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
                            "name": "realName",
                            "value": "4.1.智慧研学商户端",
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
                            "name": "remark",
                            "value": "4.1.智慧研学商户端",
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
                            "name": "name",
                            "value": "4.1.智慧研学商户端",
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
                            "name": "pid",
                            "value": "0b8286331eda4724892ee849be2cac22",
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
                            "name": "isGroup",
                            "value": "1",
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
                            "name": "children",
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
                                    "name": "tid",
                                    "value": "74d005d6b0c1446ba636c42674a50fd8",
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
                                    "name": "realName",
                                    "value": "study-tour-product",
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
                                    "name": "remark",
                                    "value": "4.1.1.研学商品",
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
                                    "name": "name",
                                    "value": "4.1.1.研学商品(study-tour-product)",
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
                                    "name": "pid",
                                    "value": "6edc669a182546b99b42bba4a6a69a1d",
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
                                    "name": "isGroup",
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
                                    "name": "children",
                                    "value": "",
                                    "description": "",
                                    "required": false,
                                    "dataType": "Array",
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
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          },
          {
            "name": "total",
            "value": "4",
            "description": "",
            "required": false,
            "dataType": "Integer",
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
  }
}
================================
// 接口：01.页面组件管理
// 路由：POST /sym/component
// 业务说明：根据 action 查询页面组件列表或树，或调用组件管理服务执行保存与维护操作。
import uiComponentCache
import uiComponentAdmin

if(action == 'list'){
    return uiComponentCache.list()
}
if(action == 'tree'){
    return uiComponentAdmin.tree()
}
if(action == 'save'){
    var duplicateMessage = uiComponentAdmin.duplicateMessage(body)
    if(duplicateMessage != null && duplicateMessage != ''){
        exit -1, duplicateMessage
    }
    return uiComponentAdmin.save(body)
}
if(action == 'saveCode'){
    return uiComponentAdmin.saveCode(body)
}
if(action == 'del'){
    return uiComponentAdmin.delete(tid)
}
if (action === 'getSourceCode'){
    return uiComponentAdmin.sourceCode(tid)
}
if (action === 'history'){
    return uiComponentAdmin.history(componentId)
}
if (action === 'historyDetail'){
    return uiComponentAdmin.historyDetail(tid)
}
if (action === 'getLastCode'){
    return uiComponentAdmin.lastCode(componentId)
}
