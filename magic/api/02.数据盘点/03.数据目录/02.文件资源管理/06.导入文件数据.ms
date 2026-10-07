{
  "properties": {},
  "id": "0de479d6e10340358b40c11991375065",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "06.导入文件数据",
  "createTime": 1782955343993,
  "updateTime": 1784437771748,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/import",
  "method": "POST",
  "parameters": [
    {
      "name": "tid",
      "value": "b868f2b30a6c4d6aaa23f70627e10b6d",
      "description": "真实数据示例参数：tid",
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
      "name": "file",
      "value": {},
      "description": "真实数据示例参数：file",
      "required": false,
      "dataType": "MultipartFile",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "isFull",
      "value": null,
      "description": "真实数据示例参数：isFull",
      "required": false,
      "dataType": "Boolean",
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
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"导入成功，若数据较大，请耐心等待\"\n}",
  "description": "校验请求资料后执行导入文件数据，返回保存或配置结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.导入文件数据
// 路由：POST /dst/catalog/files/import
// 业务说明：校验请求资料后执行导入文件数据，返回保存或配置结果。
import tenantAccounts
import log;
import "@post:/dst/database/metadata/remote-table/data" as remoteTableData;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import java.io.File;
import org.apache.commons.io.FileUtils;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.date.DateUtil;
import cn.dev33.satoken.stp.StpUtil;
import "@post:/sym/user/me" as me;
import "@post:/dst/catalog/catalog-items/list" as getCatalogItem;
import request;
import "@post:/dst/catalog/detail" as queryById;



//获取用户名称
var session =  StpUtil.getTokenSession();
var userName = tenantAccounts.session().dataMap.user.userName;

if(!file){
    // 返回当前业务分支的标准结果。
    return "文件不存在，请上传文件"
}
var fileName = file.getOriginalFilename();
var dotIndex = fileName.lastIndexOf('.');
if (fileName.substring(dotIndex + 1) !== "xlsx"){
    // 参数或业务状态不满足要求时立即终止请求。
    exit -1, "请上传excel文件"
}
var tempFile = File.createTempFile(fileName.substring(0, dotIndex), "."+fileName.substring(dotIndex + 1));
FileUtils.writeByteArrayToFile(tempFile, file.getBytes());
ExcelReader reader = ExcelUtil.getReader(tempFile);
var rowIndex = 0;

// 目录
body = {tid}
cols = getCatalogItem();
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

// 读取文件 字段名 字段值
var columnNamesEn = [];
var values = [];
reader.read().each(rows => {
    if(rowIndex == 0){
    }else{
        values.add(rows);
    }
    rowIndex ++;
});
var result = [];
var index = 0;
var now = DateUtil.now();
values.each(value => {
    var data = {};
    for (col in cols) {
        data.put(col.colEn, value[index] || "");
        index++;
    }
    data.put("unique_id", IdUtil.getSnowflakeNextIdStr());
    data.put("ins_time", now);
    data.put("data_from","互联网"); //互联网
    data.put("upload_user",userName); //互联网
    result.add(data);
    index = 0;
});
body = {action: "batchInsert", dbType: db.dbType, connection: datasourceParam, tableName: resName, rows: result};
var insertData = remoteTableData();
if(isFull){
    body = {action: "deleteBefore", dbType: db.dbType, connection: datasourceParam, tableName: resName, key: "ins_time", value: now};
    remoteTableData();
}

// 返回当前业务分支的标准结果。
return "导入成功，若数据较大，请耐心等待";
