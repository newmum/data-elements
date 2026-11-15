{
  "properties": {},
  "id": "7b22c3e0a9104c8888a358c0707c126f",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "02.核验采集字段清单",
  "path": "/list",
  "method": "HEAD",
  "description": "与对应的 GET 共用 metadataCatalogColumns；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogColumns' as read

// HEAD /dwm/column/list 采集字段清单。
// 与对应的 GET 共用 metadataCatalogColumns；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。
return read(false)
