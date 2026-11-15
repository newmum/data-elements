{
  "properties": {},
  "id": "72c09465432842a898b4287719ed0eaa",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "06.核验采集表分页",
  "path": "/tables",
  "method": "HEAD",
  "description": "与对应的 GET 共用 metadataCatalogTables；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogTables' as read

// HEAD /dwm/catalog/tables 采集表分页。
// 与对应的 GET 共用 metadataCatalogTables；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。
return read()
