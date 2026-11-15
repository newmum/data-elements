{
  "properties": {},
  "id": "resource_ods_list_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "04.贴源层列表",
  "createTime": 1784454007655,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/ods-list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"conditions\": [\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "资源中心 04：贴源层 ODS 数据表分页；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.贴源层列表
// 路由：POST /dwm/center/ods-list
// 业务说明：资源中心 04：贴源层 ODS 数据表分页；读取或计算后返回业务结果。
import '@post:/dwm/center/layer-page' as layerPage

/*
 * 资源中心 04：贴源层 ODS 数据表分页。
 * 只读 db_table_t，不调用旧 Java 资产查询服务。
 */

var oldBody = body
body = { ...(oldBody == null ? {} : oldBody), layerRoot: 'ods' }
var result = layerPage()
body = oldBody
// 返回当前业务分支的标准结果。
return result
