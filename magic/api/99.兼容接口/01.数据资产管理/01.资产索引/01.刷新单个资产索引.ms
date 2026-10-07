{
  "properties": {},
  "id": "compat_assets_refresh_01",
  "script": null,
  "groupId": "compat_dataassets_assets_01",
  "name": "01.刷新单个资产索引",
  "createTime": 1784458863695,
  "updateTime": 1784458863695,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/refresh",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{}",
  "description": "兼容旧地址，原样转发请求到资产索引单条刷新接口并返回其结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.刷新单个资产索引
// 路由：POST /dataassets/manage/assets/refresh
// 业务说明：兼容旧地址，原样转发请求到资产索引单条刷新接口并返回其结果。
/* 旧地址兼容：参数和响应原样委托 /dst/maintenance/refresh。 */
import '@post:/dst/maintenance/refresh' as refreshAsset
// 返回当前业务分支的标准结果。
return refreshAsset()
