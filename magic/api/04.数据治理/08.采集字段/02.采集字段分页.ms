{
  "properties": {},
  "id": "5c88cf5d07524a1684303a8748f1de47",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "02.采集字段分页",
  "path": "/page",
  "method": "GET",
  "description": "核对采集表及数据源权限，与清单入口共用 /common/metadataCatalogColumns，返回分页结构。",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/metadataCatalogColumns' as read

// GET /dwm/column/page。核对采集表及数据源权限，与清单入口共用 /common/metadataCatalogColumns，返回分页结构。

return read(true)
