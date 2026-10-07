{
  "properties": {},
  "id": "e413aa6a19284d65996f74ee39af69fe",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "02.删除数据操作",
  "createTime": 1782660730121,
  "updateTime": 1784437771466,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "DELETE",
  "parameters": [
    {
      "name": "tableName",
      "value": "da_catalog_t",
      "description": "真实数据示例参数：tableName",
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
      "name": "id",
      "value": "1876461995321249792",
      "description": "真实数据示例参数：id",
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
      "name": "key",
      "value": null,
      "description": "真实数据示例参数：key",
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
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"desc\": \"操作成功\",\n    \"success\": true,\n    \"data\": 1\n}",
  "description": "按目标标识和当前状态执行删除数据操作，返回处理结果。",
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
        "value": "org.ssssssss.script.exception.MagicScriptException: 不支持的类型:class java.lang.String at Row:5~5,Col:10~30\n\n        .in(key?key:\"tid\", id)\r\n         ^^^^^^^^^^^^^^^^^^^^^ ",
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
// 接口：02.删除数据操作
// 路由：DELETE /data/delete
// 业务说明：按目标标识和当前状态执行删除数据操作，返回处理结果。
import java.util.Arrays;

// 从 MySQL 业务表读取当前有效数据。
var result = db.table(tableName)
        .where()
        .in(key?key:"tid", Arrays.asList(id.split(",")))
        .delete();

// 返回当前业务分支的标准结果。
return result;
