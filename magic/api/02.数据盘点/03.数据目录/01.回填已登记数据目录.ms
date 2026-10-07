{
  "properties": {},
  "id": "datapd_catalog_sync_registered_tables_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "01.回填已登记数据目录",
  "createTime": 1784840000000,
  "updateTime": 1787155377955,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sync-registered-tables",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"maxRows\":10000}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"total\":0,\"synced\":0,\"failed\":0,\"failures\":[]}",
  "description": "按当前租户已完成登记的数据表回填数据目录；目录由登记表派生，使用 sourceTableId 保持一表一目录；使用请求参数 maxRows。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.回填已登记数据目录
// 路由：POST /dst/catalog/sync-registered-tables
// 业务说明：按当前租户已完成登记的数据表回填数据目录；目录由登记表派生，使用 sourceTableId 保持一表一目录；使用请求参数 maxRows。
// 回填已登记数据目录：处理 POST /sync-registered-tables 的核心查询和组装返回数据逻辑。
import '@/common/registeredTableCatalogProjection' as registeredTableCatalogProjection

var maxRows = body == null ? null : body.maxRows
return registeredTableCatalogProjection('syncCompleted', null, false, maxRows)
