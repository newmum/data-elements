{
  "properties": {},
  "id": "83f7d0fb43d64b94af7519d90e2ea801",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "05.同步文件目录(互联网)",
  "createTime": 1782955343984,
  "updateTime": 1784437771742,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sync-catalog",
  "method": "GET",
  "parameters": [
    {
      "name": "uuid",
      "value": "R-1773133185131",
      "description": "真实数据示例参数：uuid",
      "required": true,
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
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"完成目录唯一标识为R-1773133185131同步\"\n}",
  "description": "1. 请求远程接口传入 uuid 获取 目录信息 与 数据项信息，根据目录 获取库信息。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.同步文件目录(互联网)
// 路由：GET /dst/catalog/files/sync-catalog
// 业务说明：1. 请求远程接口传入 uuid 获取 目录信息 与 数据项信息，根据目录 获取库信息。
// ============== 根据 唯一标识符uuid 将公安网的目录同步到本地 ==============
// 1. 请求远程接口传入 uuid 获取 目录信息 与 数据项信息，根据目录 获取库信息
// 2. 本地保存接口保存文件目录和库信息
// =======================================================================

import log;
import http;
import "@post:/dst/catalog/saveOrUpdate" as saveAsset;
import "@post:/dst/catalog/list" as assetList;
import request;


// token
var token =  token || request.getHeaders("token")[0]

// 公安网地址
var url = "http://192.168.175.86:8088/"


// 远程接口
var res = http.connect(`${url}dataassets/manage/assets/list`)
                .contentType("application/json")
                .header({token})
                .body({uuid})
                .post()
                .getBody();
var catalogs = res.data;
if (!catalogs || catalogs.size() === 0){
    // 参数或业务状态不满足要求时立即终止请求。
    exit -1, "请核实此唯一标识符是否正确";
}
var tid = catalogs[0].tid

// 目录
res = http.connect(`${url}dataassets/manage/assets/queryById`)
                .contentType("application/json")
                .header({token})
                .body({tid})
                .post()
                .getBody();
var catalog = res.data

// 目录项
res = http.connect(`${url}/dst/catalog/catalog-items/list`)
                .contentType("application/json")
                .header({token})
                .body({tid: catalog.tid, token})   
                .post()
                .getBody();
var catalogItems = res.data;

// 库
res = http.connect(`${url}dataassets/manage/assets/queryById`)
                .contentType("application/json")
                .header({token})
                .body({tid: catalog.dbId})
                .post()
                .getBody();
var db = res.data

// 步骤2-保存到本地
body = {
    propList: catalog,
    catalogItems
}
saveAsset();

body = {
    propList: db
}
saveAsset();


// 返回当前业务分支的标准结果。
return "完成目录唯一标识为"+uuid+"同步"
