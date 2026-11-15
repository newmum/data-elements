{
  "properties": {},
  "id": "5c88cf5d07524a1684303a8748f1de47",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "03.采集字段分页",
  "path": "/page",
  "method": "GET",
  "description": "与对应的 HEAD 共用 metadataCatalogColumns；该函数执行本租户授权查询。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogColumns' as read

// GET /dwm/column/page 采集字段分页。
// 与对应的 HEAD 共用 metadataCatalogColumns；该函数执行本租户授权查询。
return read(true)
