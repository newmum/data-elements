{
  "properties": {},
  "id": "dbaeba02930f4099ab2a34ef3b33c8d6",
  "script": null,
  "groupId": "651b3afb2fba41afbb67eb9056d8af3b",
  "name": "05.统计数据总数",
  "createTime": 1782660730177,
  "updateTime": 1784437771470,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/count",
  "method": "POST",
  "parameters": [
    {
      "name": "tableName",
      "value": null,
      "description": "真实数据示例参数：tableName",
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
  "responseBody": null,
  "description": "根据已选业务表执行计数查询，返回当前查询范围的数据总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.统计数据总数
// 路由：POST /data/count
// 业务说明：根据已选业务表执行计数查询，返回当前查询范围的数据总数。
// 从 MySQL 业务表读取当前有效数据。
var result = db.table(tableName).count();

// 返回当前业务分支的标准结果。
return result;
