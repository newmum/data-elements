{
  "properties": {},
  "id": "3002ad9de30b4d22ac0644a965f629b7",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "03.下载文件导入模板",
  "createTime": 1782955343957,
  "updateTime": 1784437771723,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/download",
  "method": "GET",
  "parameters": [
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
      "name": "isFull",
      "value": true,
      "description": "真实数据示例参数：isFull",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "0",
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
  "responseBody": null,
  "description": "根据数据项下载excel模版, 分空模板、带全量数据模板。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.下载文件导入模板
// 路由：GET /dst/catalog/files/download
// 业务说明：根据数据项下载excel模版, 分空模板、带全量数据模板。
// 根据数据项下载excel模版, 分空模板、带全量数据模板
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import jakarta.servlet.ServletOutputStream;
import cn.hutool.core.codec.Base64;
import response;
import log;

import "@post:/dst/catalog/detail" as queryById;
import "@post:/dst/catalog/catalog-items/list" as getCatalogItem;
import "@get:/dst/catalog/files/curd-data" as curdData;


var excel_result = [];
// 目录
body = {tid}
cols = getCatalogItem();
var {resName} = queryById();

// ==== 增量导入模版 ====
var items = cols.map(item => item.colName);
excel_result.add(items);

// ==== 全量导入模版，额外添加所有表数据 ====
if (isFull === "1"){
    var action = "get"
    var pageNo = 1
    var pageSize = 9999999
    var {originRows} = curdData();
    originRows.each(item => {
        excel_result.add(item.subList(0, cols.size()))
    })
}

ExcelWriter writer = ExcelUtil.getWriter(true);
writer.write(excel_result, true);

filename = filename?filename:"excel_export";
response.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
response.setHeader("Content-Disposition","attachment;filename="+resName+".xlsx"); 
ServletOutputStream out=response.getOutputStream(); 

writer.flush(out, true);
writer.close();
IoUtil.close(out);

// 返回当前业务分支的标准结果。
return response.end();
