{
  "properties": {},
  "id": "8ac4f13df5184fe1b6195a23bbfa3cb6",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "03.目录关联表资源",
  "path": "/resources",
  "method": "GET",
  "description": "与对应的 HEAD 共用 metadataCatalogResources；该函数执行本租户授权查询。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogResources' as read

// GET /dwm/catalog/resources 目录关联表资源。
// 与对应的 HEAD 共用 metadataCatalogResources；该函数执行本租户授权查询。
return read()
