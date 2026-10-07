{
  "properties": {},
  "id": "5ab722975fb242aa9944638c3fa9c51b",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "02.基础层列表",
  "createTime": 1782955345110,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dwd-list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"sortField\": \"updatedTime\",\n    \"sortDir\": \"desc\",\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"conditions\": [\n        {\n            \"field\": \"dataSourceType\",\n            \"value\": \"qugzbzk\",\n            \"type\": \"in\"\n        }\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 02：基础层 DWD 数据表分页；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.基础层列表
// 路由：POST /dwm/center/dwd-list
// 业务说明：资源中心 02：基础层 DWD 数据表分页；读取或计算后返回业务结果。
import '@post:/dwm/center/layer-page' as layerPage

/*
 * 资源中心 02：基础层 DWD 数据表分页。
 * 只读 db_table_t；可由 dataSourceType 条件筛选具体标准库。
 */

var oldBody = body
body = { ...(oldBody == null ? {} : oldBody), layerRoot: 'dwd' }
var result = layerPage()
body = oldBody
// 返回当前业务分支的标准结果。
return result
