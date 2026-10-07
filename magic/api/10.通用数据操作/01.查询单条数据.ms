{
  "properties": {},
  "id": "8381c69b460448bf9e6ce9686853b22e",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "01.查询单条数据",
  "createTime": 1782660730103,
  "updateTime": 1784437771464,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/get",
  "method": "GET",
  "parameters": [
    {
      "name": "tableName",
      "value": "sym_config_t",
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
      "name": "modelCode",
      "value": "测试",
      "description": "真实数据示例参数：modelCode",
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
      "value": "login_50_sysname",
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
      "name": "columns",
      "value": null,
      "description": "真实数据示例参数：columns",
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
  "responseBody": "{\n    \"code\": 100120,\n    \"msg\": \"user_no_login\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "根据表名或模型编码、主键字段和值构建查询条件，调用共享查询函数返回单条业务数据。",
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
        "name": "modelCode",
        "value": "data-catalog-report",
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
        "value": "100120",
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
        "value": "user_no_login",
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
// 接口：01.查询单条数据
// 路由：GET /data/get
// 业务说明：根据表名或模型编码、主键字段和值构建查询条件，调用共享查询函数返回单条业务数据。
import log;
import '@/common/table-search' as tableSearch;
import '@/common/model-search' as modelSearch;

var conditions = [{
    "field": key || "tid",
    "condition": "eq",
    "value": id
}]
var result;
if(tableName){
    result = tableSearch(tableName,columns,conditions).selectOne();
}else{
    result = db.selectOne(modelSearch(modelCode,columns,conditions));
}
// 返回当前业务分支的标准结果。
return result;
