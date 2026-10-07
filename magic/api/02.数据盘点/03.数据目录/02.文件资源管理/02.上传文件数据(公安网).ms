{
  "properties": {},
  "id": "32ea747d631a40dcaf7a20e0f1daa5ea",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "02.上传文件数据(公安网)",
  "createTime": 1782955343940,
  "updateTime": 1784437771722,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/upload-ga",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"b868f2b30a6c4d6aaa23f70627e10b6d\",\n    \"data\": [\n        {\n            \"drama_length\": \"2\",\n            \"drama_theme\": \"2\",\n            \"production_permit_number\": \"2\",\n            \"drama_name\": \"2\",\n            \"cooperation_organization\": \"2\",\n            \"unique_id\": \"2031577149343375362\",\n            \"ins_time\": \"2026-03-11 11:45:11\",\n            \"data_from\": \"互联网\",\n            \"upload_user\": \"zn\"\n        }\n    ]\n}",
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
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"org.ssssssss.script.exception.MagicScriptException: 找不到函数saveAsset() at Row:56~56,Col:1~11\\n\\nsaveAsset();\\r\\n^^^^^^^^^^^  \",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "1. 根据 目录 uuid，表数据，执行插入数据方法。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.上传文件数据(公安网)
// 路由：POST /dst/catalog/files/upload-ga
// 业务说明：1. 根据 目录 uuid，表数据，执行插入数据方法。
// ============== 公安网数据插入 ==============
// 1. 根据 目录 uuid，表数据，执行插入数据方法
// ===========================================


//下边是公安网逻辑
import log;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.codec.Base64;
import "@post:/dst/database/metadata/remote-table/data" as remoteTableData;
import request;
import "@post:/dst/catalog/saveOrUpdate" as saveAsset;
import "@post:/dst/catalog/detail" as queryById;

// token
var token =  token || request.getHeaders("token")[0]

// 解析请求体并为可选参数设置默认值。
const {tid,data} = body;
log.debug("接口参数{}",body)
//校验
if(is_blank(tid) || (is_null(data) && op !== "all")){
    // 返回当前业务分支的标准结果。
    return "必要参数不能为空"
}
// 查询目录的库信息
body = {tid}
var { resName, tableId, dbId } = queryById();
if (!tableId){
    // 参数或业务状态不满足要求时立即终止请求。
    exit -1, "未建表";
}
body = {tid: dbId}
// 库
var db = queryById(); 
var datasourceParam = {
    "host": db.host,
    "port": db.port.asInt(),
    "user": db.user,
    "password": db.password,
    "database": db.database,
    "serviceName":db.serviceName, //ORACLE
    "charset": "UTF-8"
};

// 插入或更新数据，unique_id为记录唯一id
var dataParam = data;
log.debug("准备插入的数据对象{}",dataParam);

// ------------------------生产调式逻辑--------------------
// //插入数据（重置数据并插入版）
// 更新目录的 recordCount

//------------------------本地调式逻辑--------------------
body = {action: "batchResetInsert", dbType: db.dbType, connection: datasourceParam, tableName: resName + "_copy1", rows: dataParam, key: "unique_id"};
remoteTableData();
// 更新目录的 recordCount
body = {action: "count", dbType: db.dbType, connection: datasourceParam, tableName: resName + "_copy1"};
var recordCount = remoteTableData();
body = { tid, propList: {recordCount} };
saveAsset();

// 返回当前业务分支的标准结果。
return "成功上报"+dataParam.getLength() + "条数据";
