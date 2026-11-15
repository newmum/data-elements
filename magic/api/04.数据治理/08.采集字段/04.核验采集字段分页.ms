{
  "properties": {},
  "id": "d2762b4faa0f4e1c8f19669f3c54ac2c",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "04.核验采集字段分页",
  "path": "/page",
  "method": "HEAD",
  "description": "与对应的 GET 共用 metadataCatalogColumns；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogColumns' as read

// HEAD /dwm/column/page 采集字段分页。
// 与对应的 GET 共用 metadataCatalogColumns；该函数执行本租户授权查询。使用 GET 相同的响应头，HTTP 层抑制正文。
return read(true)
