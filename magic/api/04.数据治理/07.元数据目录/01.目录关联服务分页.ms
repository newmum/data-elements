{
  "properties": {},
  "id": "7542b242144845b287c82950a1a4b87d",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "01.目录关联服务分页",
  "path": "/services",
  "method": "GET",
  "description": "与对应的 HEAD 共用 metadataCatalogServices；该函数执行本租户授权查询。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogServices' as read

// GET /dwm/catalog/services 目录关联服务分页。
// 与对应的 HEAD 共用 metadataCatalogServices；该函数执行本租户授权查询。
return read()
