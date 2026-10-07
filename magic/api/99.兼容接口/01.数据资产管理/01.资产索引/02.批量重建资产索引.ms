{
  "properties": {},
  "id": "compat_assets_batch_refresh_01",
  "script": null,
  "groupId": "compat_dataassets_assets_01",
  "name": "02.批量重建资产索引",
  "createTime": 1784458863750,
  "updateTime": 1784458863750,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/batchRefresh",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "兼容旧地址，原样转发请求到资产索引批量重建接口并返回其结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.批量重建资产索引
// 路由：POST /dataassets/manage/assets/batchRefresh
// 业务说明：兼容旧地址，原样转发请求到资产索引批量重建接口并返回其结果。
/* 旧地址兼容：参数和响应原样委托 /dst/maintenance/batchRefresh。 */
import '@post:/dst/maintenance/batchRefresh' as batchRefreshAssets
// 返回当前业务分支的标准结果。
return batchRefreshAssets()
