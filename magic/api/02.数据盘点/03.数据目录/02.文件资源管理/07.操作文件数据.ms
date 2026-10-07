{
  "properties": {},
  "id": "f7f2ee0a5eb5410fbf30a027d48ca8da",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "07.操作文件数据",
  "createTime": 1782955344001,
  "updateTime": 1784437771752,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/curd-data",
  "method": "GET",
  "parameters": [
    {
      "name": "action",
      "value": "测试",
      "description": "真实数据示例参数：action",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": "get",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "tid",
      "value": "b868f2b30a6c4d6aaa23f70627e10b6d",
      "description": "真实数据示例参数：tid",
      "required": true,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageNo",
      "value": null,
      "description": "真实数据示例参数：pageNo",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "pageSize",
      "value": null,
      "description": "真实数据示例参数：pageSize",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "rowId",
      "value": null,
      "description": "真实数据示例参数：rowId",
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
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [
    {
      "name": "tid",
      "value": null,
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
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"columns\": [\n            \"drama_length\",\n            \"drama_theme\",\n            \"production_permit_number\",\n            \"drama_name\",\n            \"cooperation_organization\",\n            \"unique_id\",\n            \"ins_time\",\n            \"data_from\",\n            \"upload_user\"\n        ],\n        \"rows\": [\n            {\n                \"drama_length\": \"2\",\n                \"drama_theme\": \"2\",\n                \"production_permit_number\": \"2\",\n                \"drama_name\": \"2\",\n                \"cooperation_organization\": \"2\",\n                \"unique_id\": \"2031255854370103296\",\n                \"ins_time\": \"2026-03-10 14:28:29\",\n                \"data_from\": \"互联网\",\n                \"upload_user\": \"zn\"\n            },\n            {\n                \"drama_length\": \"2\",\n                \"drama_theme\": \"2\",\n                \"production_permit_number\": \"2\",\n                \"drama_name\": \"2\",\n                \"cooperation_organization\": \"2\",\n                \"unique_id\": \"2031255815962861568\",\n                \"ins_time\": \"2026-03-10 14:28:20\",\n                \"data_from\": \"互联网\",\n                \"upload_user\": \"zn\"\n            },\n            {\n                \"drama_length\": \"2\",\n                \"drama_theme\": \"2\",\n                \"production_permit_number\": \"2\",\n                \"drama_name\": \"2\",\n                \"cooperation_organization\": \"2\",\n                \"unique_id\": \"2031255272083267584\",\n                \"ins_time\": \"2026-03-10 14:26:10\",\n                \"data_from\": \"互联网\",\n                \"upload_user\": \"zn\"\n            }\n        ]\n    }\n}",
  "description": "按文件目录标识读取数据项和目标表，区分数据预览与增删改操作，并将请求转发至对应的数据表接口。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.操作文件数据
// 路由：GET /dst/catalog/files/curd-data
// 业务说明：按文件目录标识读取数据项和目标表，区分数据预览与增删改操作，并将请求转发至对应的数据表接口。
// 数据预览
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService;
import "@post:/dst/catalog/detail" as queryById;
import "@post:/dst/catalog/catalog-items/list" as getCatalogItem;
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService;
import "@post:/dst/database/metadata/remote-table/data" as remoteTableData;
import "@get:/dst/catalog/files/create-table" as createTable;

// 获取字段数
body = {tid}
cols = getCatalogItem();
var { resName, tableId, dbId } = queryById();

if (!tableId){
  createTable();
}

body = {tid: dbId}
var db = queryById();
var datasourceParam = {
    "host": db.host,
    "port": db.port.asInt(),
    "user": db.user,
    "password": db.password,
    "database": db.database,
    "serviceName": db.serviceName, //ORACLE
    "charset": "UTF-8"
};

// ==== 查询 ====
if(action == 'get'){
    body = {action: "preview", dbType: db.dbType, connection: datasourceParam, tableName: resName, pageNo: pageNo || 1, pageSize: pageSize || 20, sortField: "ins_time", sortDirection: "DESC"};
    result = remoteTableData();
    var { columns, rows } = result;
    var formattedRows = rows.map(row => {
        var obj = {};
        columns.each((key, index) => {
            obj[key] = row[index];
        });
        // 返回当前业务分支的标准结果。
        return obj;
    });
    body = {action: "count", dbType: db.dbType, connection: datasourceParam, tableName: resName};
    var total = remoteTableData();
    // 返回当前业务分支的标准结果。
    return {
        columns, 
        rows: formattedRows,
        originRows,
        total
    }
}

// ==== 删除记录 ====
if(action == 'delete'){
    if (!rowId){
        // 参数或业务状态不满足要求时立即终止请求。
        exit -1, "传入删除记录项唯一ID"
    }
    body = {action: "deleteByValues", dbType: db.dbType, connection: datasourceParam, tableName: resName, key: db.dbType !== "oracle" ? "unique_id" : "UNIQUE_ID", values: [rowId]};
    result = remoteTableData();
    // 返回当前业务分支的标准结果。
    return result;
}
