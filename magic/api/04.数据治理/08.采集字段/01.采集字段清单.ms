{
  "properties": {},
  "id": "07b31a6510b54fec87b6b26d3b678eb2",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "01.采集字段清单",
  "path": "/list",
  "method": "GET",
  "description": "与对应的 HEAD 共用 metadataCatalogColumns；该函数执行本租户授权查询。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogColumns' as read

// GET /dwm/column/list 采集字段清单。
// 与对应的 HEAD 共用 metadataCatalogColumns；该函数执行本租户授权查询。
return read(false)
