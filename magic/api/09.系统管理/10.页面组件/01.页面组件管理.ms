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
      "description": "读取共享低代码编译包、组件树和历史，或通过组件管理服务维护组件。runtimeBatch 使用 body.root 请求已核对依赖的入口，返回 complete、scope 和 components。",
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
import cn.hutool.crypto.digest.DigestUtil
import java.util.Locale
import uiComponentCache
import uiComponentAdmin

// POST /sym/component：按 action 读取共享编译包、树和历史，或调用管理服务维护组件。
// runtimeBatch 接收 body.root，在同一已认证路由返回入口包或兼容的全量包。
if (action == 'list') {
    return uiComponentCache.list()
}
if (action == 'runtimeBatch') {
    var root = body?.root
    var dataTaskRoot = root == 'data-convergence' || root == 'data-convergence-hive'
    if (root != 'my-workbench' && root != 'app-shell' && !dataTaskRoot) {
        return { complete: false, components: [] }
    }

    // 仅为已核对编译产物及其依赖提供入口包。独立详情路由和登记仍按原流程加载。
    var reviewedDigests = {
        'global-nav': '2043d19053a529bdcdf89812c496d804c88915fe905ce91a6ff25c903ef03719',
        'shopping-car': '1fd5afffdffb61f935d4b959db1257bbd3e59a811af689915fc36f31884b0e38'
    }
    if (root == 'my-workbench') {
        reviewedDigests['my-workbench'] = 'ea214eee37c62654fadf8660ffd80fc330124ae437a8761c7a18c32576187cbf'
    }
    if (dataTaskRoot) {
        reviewedDigests.putAll({
            'data-convergence': '88f639c5b3d353d711f8e0c49f9bd3a68e74cdf830f7bb7637c20539118d8ef4',
            'data-convergence-hive': '063b0d0f72166d65e6e195f4261c160384e385c39cee3bc9ea7a19baec727c98',
            'access-apply': '70eef44205912840db9d6d977f7d0bd98d82dd03cdbffe2f23cf9df88c143ae2',
            'schedule-setting': 'cacb84d9abe0ace3717610d60fb17392865dccbee637a07124eebfd58cef260e'
        })
    }

    // 历史名称可能用驼峰或其他大小写。发现与入口包名称近似的别名就沿用全量注册顺序。
    var reviewedNames = {}
    for (name in reviewedDigests.keySet()) {
        reviewedNames[name.replaceAll('-', '').toLowerCase(Locale.ROOT)] = name
    }

    // 从同一控制库缓存快照取得编译包，按入口名在内存定位，无逐组件数据库读取。
    var components = uiComponentCache.list()
    var selected = []
    var matchedNames = []
    for (item in components) {
        var name = item.name
        var reviewedName = name == null ? null : reviewedNames.get(name.replaceAll('-', '').toLowerCase(Locale.ROOT))
        if (reviewedName == null) {
            continue
        }
        // 在线编译改动、组件缺失或同名冲突均使用原全量包，保持动态依赖可用。
        if (name != reviewedName || matchedNames.contains(name) || DigestUtil.sha256Hex(item.compileJs) != reviewedDigests.get(name)) {
            return { complete: true, scope: 'all', components: components }
        }
        matchedNames.add(name)
        selected.add(item)
    }
    if (selected.size() == reviewedDigests.size()) {
        return { complete: true, scope: 'root', components: selected }
    }
    return { complete: true, scope: 'all', components: components }
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
