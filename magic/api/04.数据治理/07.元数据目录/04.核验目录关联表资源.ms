{
  "properties": {},
  "id": "6fd099f9efa348f990d386b34ac33dcf",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "04.核验目录关联表资源",
  "path": "/resources",
  "method": "HEAD",
  "description": "与对应的 GET 共用 metadataCatalogResources；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogResources' as read

// HEAD /dwm/catalog/resources 目录关联表资源。
// 与对应的 GET 共用 metadataCatalogResources；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。
return read()
