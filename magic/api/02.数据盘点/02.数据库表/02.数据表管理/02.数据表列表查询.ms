{
  "properties": {},
  "id": "datapd_db_table_list_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "02.数据表列表查询",
  "createTime": 1784454007655,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dbId\": \"可选数据源ID\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 02：返回符合条件的已登记数据表列表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.数据表列表查询
// 路由：POST /dst/database/table/list
// 业务说明：数据表管理 02：返回符合条件的已登记数据表列表。
import '@post:/dst/database/table/page' as tablePage

/*
 * 数据表管理 02：返回符合条件的已登记数据表列表。
 *
 * 复用分页查询的统一字段拼装与过滤逻辑；只读，不修改表资产或 ES。
 */

var originalBody = body
body = {
    ...(originalBody == null ? {} : originalBody),
    pageNum: 1,
    pageSize: originalBody == null || originalBody.pageSize == null ? 10000 : originalBody.pageSize
}
var result = tablePage()
body = originalBody
// 返回当前业务分支的标准结果。
return result == null || result.list == null ? [] : result.list
