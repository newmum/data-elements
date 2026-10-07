{
  "properties": {},
  "id": "728917c4af3943ccaee009539b18a004",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "02.局外数据归集情况",
  "createTime": 1782955343560,
  "updateTime": 1784437771684,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/outerStat",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"org.ssssssss.script.exception.MagicScriptException: 在1中找不到属性data或者方法getData、方法isData,内部类data at Row:7~7,Col:16~19\\n\\n    if (result.data != null && result.code != null) { return result.data; }\\r\\n               ^^^^                                                         \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "读取归集应用接口结果，筛出局外机构的数据归集情况并转换为大屏展示结构。",
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
        "value": "500",
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
        "value": "org.ssssssss.script.exception.MagicScriptException: 在1中找不到属性data或者方法getData、方法isData,内部类data at Row:7~7,Col:16~19\n\n    if (result.data != null && result.code != null) { return result.data; }\r\n               ^^^^                                                         ",
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
        "value": "false",
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
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
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
// 接口：02.局外数据归集情况
// 路由：GET /scene/data-count/outerStat
// 业务说明：读取归集应用接口结果，筛出局外机构的数据归集情况并转换为大屏展示结构。
import "@get:/scene/collect/app" as collectApp

// 解析 HTTP 导入响应（兼容 magic-api 自动包裹的 {code, data} 格式）
var unwrap = (result) => {
    if (result == null) { return null; }
    // 如果响应被 magic-api 框架包裹为 { code, message, data } 格式，提取 data 部分
    if (result.data != null && result.code != null) { return result.data; }
    // 返回当前业务分支的标准结果。
    return result;
}

// ==================== 构造 area_hj（局外机构 → 厅局区域汇总） ====================
var queryType = 1
var buildAreaHj = (jwResult) => {
    var data = unwrap(jwResult)
    var list = []
    if (data != null && data.list != null) {
        list = data.list
    }
    var areaHj = []
    for (item in list) {
        areaHj.add({
            "areaName": "\n" + (item.orgName != null ? item.orgName : ""),
            "tagCount": item.tagCount != null ? item.tagCount : 0,
            "departCount": 0,
            "appCount": 0,
            "appHjCount": 0,
            "categoryCount": item.categoryCount != null ? item.categoryCount : 0,
            "categoryHjCount": item.categoryHjCount != null ? item.categoryHjCount : 0,
            "dataCount": 0,
            "dataHjCount": item.dataHjCount != null ? item.dataHjCount : 0,
            "gxjsCount": 0,
            "gxyhCount": 0,
            "gxjsRate": 0
        })
    }
    // 返回当前业务分支的标准结果。
    return areaHj
}

// 返回当前业务分支的标准结果。
return buildAreaHj(collectApp(queryType))
