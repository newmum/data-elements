{
  "properties": {},
  "id": "5753ca0ff70d49c3872694ce10fb438a",
  "script": null,
  "groupId": "ae2bf97ab5e84d0c8c9bbd063d292c59",
  "name": "04.刷新文件元数据",
  "createTime": 1782955343972,
  "updateTime": 1784437771725,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refresh-meta",
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
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"recordCount\": 0,\n        \"fieldCount\": 5\n    }\n}",
  "description": "数据条数: 查表记录数, 元数据探查。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.刷新文件元数据
// 路由：GET /dst/catalog/files/refresh-meta
// 业务说明：数据条数: 查表记录数, 元数据探查。
// 获取字段数 + 数据条数
import com.linewell.dataelement.metautil.service.MetadataExplorerService as metadataExplorerService;
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService;
import "@post:/dst/database/metadata/remote-table/data" as remoteTableData;
import "@post:/dst/catalog/detail" as queryById;
import "@post:/dst/catalog/saveOrUpdate" as saveAsset;
import "@post:/dst/catalog/catalog-items/list" as getCatalogItem;

// 获取字段数
body = {tid}
cols = getCatalogItem();
var fieldCount = cols.size();


// 数据条数: 查表记录数, 元数据探查
var { resName, tableId, dbId } = queryById();

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

body = {action: "count", dbType: db.dbType, connection: datasourceParam, tableName: resName};
var countSum = remoteTableData();

// 更新目录的 recordCount 和 fieldCount
var propList = {recordCount: countSum, fieldCount}
body = { tid, propList };
saveAsset();

// 返回当前业务分支的标准结果。
return propList;
