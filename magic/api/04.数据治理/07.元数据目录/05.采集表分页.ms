{
  "properties": {},
  "id": "2aae3cefffb2475998ca78df66e3a8a5",
  "script": null,
  "groupId": "cfa98a9699854c299d9d512a34aa8d99",
  "name": "05.采集表分页",
  "path": "/tables",
  "method": "GET",
  "description": "与对应的 HEAD 共用 metadataCatalogTables；该函数执行本租户授权查询。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogTables' as read

// GET /dwm/catalog/tables 采集表分页。
// 与对应的 HEAD 共用 metadataCatalogTables；该函数执行本租户授权查询。
return read()
