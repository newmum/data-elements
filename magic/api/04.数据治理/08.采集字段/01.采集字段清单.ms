{
  "properties": {},
  "id": "07b31a6510b54fec87b6b26d3b678eb2",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "01.采集字段清单",
  "path": "/list",
  "method": "GET",
  "description": "核对采集表及数据源权限，与分页入口共用 /common/metadataCatalogColumns，返回字段清单。",
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

// GET /dwm/column/list。核对采集表及数据源权限，与分页入口共用 /common/metadataCatalogColumns，返回字段清单。

return read(false)
