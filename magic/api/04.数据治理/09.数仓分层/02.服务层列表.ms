{
  "properties": {},
  "id": "ea38fe47d5324bf0be61209dbfef3791",
  "script": null,
  "groupId": "fa824b407ef84972b2daa95187dbabb4",
  "name": "02.服务层列表",
  "createTime": 1782955345120,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dws",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"sortField\": \"updatedTime\",\n    \"sortDir\": \"desc\",\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"conditions\": [\n        {\n            \"field\": \"dataSourceType\",\n            \"value\": \"qugzbzk\",\n            \"type\": \"in\"\n        }\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 03：服务层 DWS 数据表分页；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@post:/dwm/layer/page' as layerPage

// POST /dwm/layer/dws 服务层列表
// 资源中心 03：服务层 DWS 数据表分页；读取或计算后返回业务结果。

/*
 * 资源中心 03：服务层 DWS 数据表分页。
 * 只读 db_table_t；可由 dataSourceType 条件筛选具体专题库。
 */

var oldBody = body
body = { ...(oldBody == null ? {} : oldBody), layerRoot: 'dws' }
var result = layerPage()
body = oldBody
// 返回当前业务分支的标准结果。
return result
