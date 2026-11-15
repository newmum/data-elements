{
  "properties": {},
  "id": "resource_ods_list_01",
  "script": null,
  "groupId": "fa824b407ef84972b2daa95187dbabb4",
  "name": "03.贴源层列表",
  "createTime": 1784454007655,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/ods",
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
import '@post:/dwm/layer/page' as layerPage

// POST /dwm/layer/ods 贴源层列表
// 资源中心 04：贴源层 ODS 数据表分页；读取或计算后返回业务结果。

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
