{
  "properties": {},
  "id": "1b28c8fa05ef4e04be8766664f304608",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "02.核验目录关联服务分页",
  "path": "/services",
  "method": "HEAD",
  "description": "与对应的 GET 共用 metadataCatalogServices；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogServices' as read

// HEAD /dwm/catalog/services 目录关联服务分页。
// 与对应的 GET 共用 metadataCatalogServices；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。
return read()
