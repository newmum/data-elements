{
  "properties": {},
  "id": "0f920389b8a24a0cb501700a768e491b",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "03.保存数据操作",
  "createTime": 1782660730135,
  "updateTime": 1784437771467,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"da_catalog_t\": {\n        \"tid\": \"1953732120453365760\",\n        \"assetStatus\": 2\n    }\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": -1,\n    \"msg\": \"服务暂时不可用，请检查网络连接或稍后再试\",\n    \"desc\": \"org.ssssssss.script.exception.MagicScriptException: PreparedStatementCallback; bad SQL grammar [update da_catalog_t set updated_time = ?,updated_by = ?,asset_status = ? where tid = ?] at Row:13~13,Col:40~51\\n\\n    db.table(tableName).primary(\\\"tid\\\").update(data)\\r\\n                                       ^^^^^^^^^^^^  at Row:24~24,Col:17~44\\n\\n        table = saveObject(tableName, table)\\r\\n                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "校验请求资料后执行保存数据操作，返回保存或配置结果。",
  "requestBodyDefinition": {
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
        "name": "asset",
        "value": "catalog",
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
        "name": "catalog",
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
            "value": "1876462283612540928",
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
            "name": "assetName",
            "value": "测试0108",
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
            "name": "sourceName",
            "value": "test0108",
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
            "name": "appId",
            "value": "1876442865973121024",
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
            "name": "orgaId",
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
      },
      {
        "name": "props",
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
                "name": "propValue",
                "value": "1796489999114715137",
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
                "name": "propName",
                "value": "manageUnit",
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
        "name": "items",
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
                "name": "colName",
                "value": "12",
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
                "name": "colEn",
                "value": "12",
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
                "name": "colType",
                "value": "5",
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
                "name": "colLength",
                "value": "12",
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
                "name": "colComment",
                "value": "",
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
                "name": "isPk",
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
                "name": "isNullable",
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
                "name": "colUnit",
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
              },
              {
                "name": "dateFormat",
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
              },
              {
                "name": "markLvl",
                "value": "1",
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
                "name": "maskingId",
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
              },
              {
                "name": "isDict",
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
                "name": "dictName",
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
              },
              {
                "name": "shareType",
                "value": "1",
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
        "value": "-1",
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
        "value": "服务暂时不可用，请检查网络连接或稍后再试",
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
        "name": "desc",
        "value": "org.ssssssss.script.exception.MagicScriptException: Cannot invoke \"Object.getClass()\" because \"target\" is null at Row:12~12,Col:25~28\n\nfor(tableName,tables in body){\r\n                        ^^^^   ",
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
// 接口：03.保存数据操作
// 路由：POST /data/save
// 业务说明：校验请求资料后执行保存数据操作，返回保存或配置结果。
import log
import cn.hutool.core.util.StrUtil
import '@/common/save-data' as saveData
import request

var result = [];
for(tableName,tables in body){
    if (!tables){
        continue
    }
    for(table in tables?.isMap()?[tables]:tables){
        table = table.each((key,value) => {
            if (value && value.isString() && StrUtil.startWith(value,'#') && StrUtil.count(value, '.') == 1) {
                // 去掉#符号分割对象名和属性名
                const [objName, propName] = StrUtil.split(value.substring(1), '.')
                // 获取#符号表的字段值进行赋值
                result.each((item) => {
                    if(item.table==objName){
                        table[key] = item[propName]
                    }
                })
            }
        })
        table = saveData(tableName, table)
        table.table = tableName
        result.add(table)
    }
}
// 返回当前业务分支的标准结果。
return result
