{
  "properties": {},
  "id": "nifi_field_map_caps_get_v1",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "77.查询字段映射能力",
  "path": "/field-mapping/capabilities",
  "description": "读取字段映射引擎的能力和参数。",
  "parameters": [],
  "options": [],
  "method": "GET",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFieldMappingCapabilities' as read

// GET /nifi/api/field-mapping/capabilities 返回现用引擎能力及公开参数。
// GET 和 HEAD 共用 @/common/nifiFieldMappingCapabilities。

return read()
