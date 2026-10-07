{
  "properties": {},
  "id": "28ab37e70de24570a2015c72d289ec89",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "08.文件数据物化建表",
  "createTime": 1782955344009,
  "updateTime": 1784437771755,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/create-table",
  "method": "GET",
  "parameters": [
    {
      "name": "tid",
      "value": "85368b91972c4a35b83b8fa73e34e740",
      "description": "真实数据示例参数：tid",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": "pass",
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n}",
  "headers": [
    {
      "name": "tid",
      "value": "b868f2b30a6c4d6aaa23f70627e10b6d",
      "description": null,
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
  "paths": [],
  "responseBody": "{\n    \"code\": 400,\n    \"msg\": \"参数[tid]为必填项\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "根据文件目录数据项+resName，指定dbId建表。",
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
        "value": "已建表，无需再建",
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
}
================================
// 接口：08.文件数据物化建表
// 路由：GET /dst/catalog/files/create-table
// 业务说明：根据文件目录数据项+resName，指定dbId建表。
// 根据文件目录数据项+resName，指定dbId建表
import "@post:/dst/database/metadata/getCreateTableDDL" as getCreateTableDDL;
import "@post:/dst/database/metadata/createTable" as createTable;
import "@post:/dst/catalog/catalog-items/list" as getCatalogItem;
import "@post:/dst/catalog/detail" as queryById;
import "@post:/dst/catalog/saveOrUpdate" as saveAsset;

// 1.查询 文件目录项 resName tableId
body = {tid}
var { resName, tableId, dbId, fileCatalogName } = queryById();
var cols = getCatalogItem();
if (tableId){
  // 返回当前业务分支的标准结果。
  return "已建表，无需再建";
}
cols = cols.map(el => ({
    "columnName": el.colEn,
    "columnComment": el.colName,
    "dataType": "varchar",
    "columnType": `varchar(${el.colLength || 255})`,
    "length": el.colLength || 255,
    "precisionLength": 0,
    "scale": 0,
    "nullable": el.isNullable == 0 ? false : true,
    "defaultValue": null,
    "primaryKey": el.isPk ? el.isPk : 0,
    "autoIncrement": 0,
    "isUnique": el.isPk ? el.isPk : 0,
    "indexed": el.isPk ? el.isPk : 0,
    "ordinalPosition": el.sortNo ? el.sortNo : 0,
    "charset": "utf8mb4",
    "collation": "utf8mb4_general_ci",
    "extra": null
}))
// 增加字段：unique_id ins_time data_from upload_user
var otherCols = [
    {colName: "唯一标识符", colEn: "unique_id"},
    {colName: "入库时间", colEn: "ins_time"},
    {colName: "数据来源", colEn: "data_from"},
    {colName: "上报单位用户", colEn: "upload_user"},
]
var i = cols.size();
otherCols.each(item => {
    cols.push({
        "columnName": item.colEn,
        "columnComment": item.colName,
        "dataType": "varchar",
        "columnType": `varchar(255)`,
        "length": 255,
        "precisionLength": 0,
        "scale": 0,
        "nullable": 0,
        "defaultValue": null,
        "primaryKey": 0,
        "autoIncrement": 0,
        "isUnique": 0,
        "indexed": 0,
        "ordinalPosition": i++,
        "charset": "utf8mb4",
        "collation": "utf8mb4_general_ci",
        "extra": null
    })
})


// 2.获取建表ddl
body = {
    "propList":{
        dbId, tableName: resName, tableNameCn: fileCatalogName
    },
    "tableItems": cols
}
const ddl = getCreateTableDDL();


// 3.建表
dbId = "badb2b8bbb8248049780884f4b1930b5";
body = { ddl, dbId };
createTable();

// 建表成功更新目录的 tableId 和 dbId，标识建表成功
body = { tid, propList:{tableId: resName, dbId} };
saveAsset();

// 返回当前业务分支的标准结果。
return {
    tableId: resName
};
