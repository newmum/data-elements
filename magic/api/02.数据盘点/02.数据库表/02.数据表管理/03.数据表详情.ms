{
  "properties": {},
  "id": "datapd_db_table_detail_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "03.数据表详情",
  "createTime": 1784454007655,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tableId\": \"00e4a8e8cc254c708925a853200bf5b9\",\n    \"tid\": \"00e4a8e8cc254c708925a853200bf5b9\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 03：读取单张数据表的登记详情和治理配置；使用请求参数 tid、tableId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据表详情
// 路由：POST /dst/database/table/detail
// 业务说明：数据表管理 03：读取单张数据表的登记详情和治理配置；使用请求参数 tid、tableId。
import '@post:/dst/database/table/list' as tableList

/*
 * 数据表管理 03：读取单张数据表的登记详情和治理配置。
 *
 * 主表为 db_table_t；字段详情由 /dst/database/metadata/columns 单独读取。
 * 本接口只读，不自动刷新物理元数据，也不自动保存。
 */

var tableId = body == null ? null : (body.tid == null ? body.tableId : body.tid)
if (tableId == null || (tableId + '').trim() == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tableId 不能为空'
}
var oldBody = body
body = { tid: tableId, pageSize: 1 }
var rows = tableList()
body = oldBody
// 返回当前业务分支的标准结果。
return rows == null || rows.size() == 0 ? null : rows[0]
