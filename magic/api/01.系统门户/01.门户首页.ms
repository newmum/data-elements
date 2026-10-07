{
  "properties": {},
  "id": "f5c231f2eb04431e8ff2a7ecbb7d07c2",
  "script": null,
  "groupId": "90ef9a3f03244c9b97aa655b8c59b0b6",
  "name": "01.门户首页",
  "createTime": 1782660713507,
  "updateTime": 1786586792887,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/index",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"org.ssssssss.script.exception.MagicScriptException: PreparedStatementCallback; uncategorized SQLException for SQL [insert into test(asset_name,id,table_name) values (?,?,?)]; SQL state [HY000]; error code [1366]; Incorrect integer value: '表资产99' for column 'asset_name' at row 1; nested exception is java.sql.BatchUpdateException: Incorrect integer value: '表资产99' for column 'asset_name' at row 1 at Row:5~5,Col:25~38\\n\\nreturn db.table('test').batchInsert(a);\\r\\n                        ^^^^^^^^^^^^^^  \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "读取当前租户可公开展示的系统配置，组装门户首页所需的名称、标识等资料并返回。",
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
        "name": "desc",
        "value": "操作成功",
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
            "name": "systemName",
            "value": "数据要素操作平台",
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
            "name": "loginLogo",
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
          }
        ]
      }
    ]
  }
}
================================
// 接口：01.门户首页
// 路由：GET /portal/index
// 业务说明：读取当前租户可公开展示的系统配置，组装门户首页所需的名称、标识等资料并返回。
// 门户首页：处理 GET /index 的核心查询和组装返回数据逻辑。
import systemConfig

return systemConfig.publicSettings()
